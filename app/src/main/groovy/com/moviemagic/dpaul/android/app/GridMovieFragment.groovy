package com.moviemagic.dpaul.android.app

import android.app.Activity
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.CursorLoader
import android.support.v4.content.Loader
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.moviemagic.dpaul.android.app.adapter.MovieGridRecyclerAdapter
import com.moviemagic.dpaul.android.app.contentprovider.MovieMagicContract
import com.moviemagic.dpaul.android.app.backgroundmodules.GlobalStaticVariables
import com.moviemagic.dpaul.android.app.backgroundmodules.LoadMoreMovies
import com.moviemagic.dpaul.android.app.backgroundmodules.LogDisplay
import com.squareup.picasso.Picasso
import groovy.transform.CompileStatic

@CompileStatic
class GridMovieFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String LOG_TAG = GridMovieFragment.class.getSimpleName()
    private static final String STATE_MOVIE_CATEGORY = 'movie_Category'
    //This is to indicate the start page for the more load. Driven by the value used in syncadapter (i.e. total
    //number of pages already downloaded)
    private int mStartPage = 0
    //This is to hold the current page of the data.
    private int mCurrentPage = mStartPage
    //To hold the previous count of the total records
    private int mPreviousRecordCount = 0
    //To hold the threashHold to load next pag  e. Currently set to 20 (i.e. one page worth of data)
    private final int mThreasholdCount = 20
    //Boolean to indicate if more data is being loaded
    private boolean isMoreDataToLoad = true
    //Boolean to track if the API call was successful
    public static boolean isDataLoadFailed = false
    //Re-try counter in case API call failed
    private int mReTryCounter = 0

    private Callback mCallback
    private CollectionColorChangeCallback mCollectionColorChangeCallback
    private boolean mCollectionGridFlag = false
//    private GridView mGridView
    private RecyclerView mRecyclerView
//    private CustomGridView mGridView
    private MovieGridRecyclerAdapter mGridRecyclerAdapter
//    private MovieGridAdapter mGridAdapter
    private String mMovieCategory
    private int mMovieCollectionId
    private Uri mMovieCategoryAndCollectionIdUri
    private String mMovieListType
    private static final int MOVIE_GRID_FRAGMENT_LOADER_ID = 0

    //Projection for movie_basic_info table
    private static final String[] MOVIE_COLUMNS = [MovieMagicContract.MovieBasicInfo._ID,
                                                   MovieMagicContract.MovieBasicInfo.COLUMN_TITLE,
                                                   MovieMagicContract.MovieBasicInfo.COLUMN_POSTER_PATH,
                                                   MovieMagicContract.MovieBasicInfo.COLUMN_PAGE_NUMBER,
                                                   MovieMagicContract.MovieBasicInfo.COLUMN_MOVIE_ID,
                                                   MovieMagicContract.MovieBasicInfo.COLUMN_MOVIE_LIST_TYPE]
    //These are indices of the above columns, if projection array changes then this needs to be changed
    final static int COL_MOVIE_ROW_ID = 0
    final static int COL_MOVIE_TITLE = 1
    final static int COL_MOVIE_POSTER = 2
    final static int COL_MOVIE_PAGE_NUM = 3
    final static int COL_MOVIE_ID = 4
    final static int COL_MOVIE_LIST_TYPE = 4

    //An empty constructor is needed so that lifecycle is properly handled
    public GridMovieFragment(){
        LogDisplay.callLog(LOG_TAG,'GridMovieFragment empty constructor is called',LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
    }

    //Collection id is used so that same module can be used for different categories and collection
    //Excpet categories, the collection id is passed as zero and not used
//    public GridMovieFragment(String movieCategory, int collectionId){
//        mMovieCategory = movieCategory
//        mMovieCollectionId = collectionId
//    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        LogDisplay.callLog(LOG_TAG, 'onCreate is called', LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
        super.onCreate(savedInstanceState)
        //Following line needed to let android know that Fragment has options menu
        //If this line is not added then associated method (e.g. OnCreateOptionsMenu) does not get supported
        //even in auto code completion
        setHasOptionsMenu(true)
    }

    @Override
    public void onCreateOptionsMenu (Menu menu, MenuInflater inflater) {
        // Inflate the menu, this adds items to the action bar if it is present.
        inflater.inflate(R.menu.grid_fragment_menu, menu)
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.grid_fragment_menu) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LogDisplay.callLog(LOG_TAG,'onCreateView is called',LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
        //Get the bundle from the Fragment
        Bundle args = getArguments()
        if (args) {
//            mMovieCategory = args.getString(GlobalStaticVariables.MOVIE_BASIC_INFO_CATEGORY)
//            mMovieCollectionId = args.getInt(GlobalStaticVariables.MOVIE_BASIC_INFO_COLL_ID)
            mMovieCategoryAndCollectionIdUri = args.getParcelable(GlobalStaticVariables.MOVIE_CATEGORY_AND_COLL_ID_URI) as Uri
            mMovieCategory = MovieMagicContract.MovieBasicInfo.getMovieCategoryFromMovieAndCollectionIdUri(mMovieCategoryAndCollectionIdUri)
            mMovieCollectionId = MovieMagicContract.MovieBasicInfo.getCollectionIdFromMovieAndCollectionIdUri(mMovieCategoryAndCollectionIdUri)
            LogDisplay.callLog(LOG_TAG,"Grid Fragment arguments.Uri -> $mMovieCategoryAndCollectionIdUri",LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
            LogDisplay.callLog(LOG_TAG,"Grid Fragment arguments.Movie Category -> $mMovieCategory",LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
            LogDisplay.callLog(LOG_TAG,"Grid Fragment arguments.Collection ID -> $mMovieCollectionId",LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
        }

        //inflate the view before referring any view using id
        View mRootView = inflater.inflate(R.layout.fragment_grid_movie,container,false)
        mRecyclerView = mRootView.findViewById(R.id.auto_grid_recycler_view) as RecyclerView
//        mGridView = mRootView.findViewById(R.id.movie_grid) as GridView
//        mGridView = mRootView.findViewById(R.id.movie_grid) as CustomGridView
        //Create a new interface member variable for MovieGridRecyclerAdapterOnClickHandler and the same is passed as
        //parameter to Adapter, this onClick method is called whenever onClick is called from MovieGridRecyclerAdapter
        mGridRecyclerAdapter = new MovieGridRecyclerAdapter(getActivity(),
                new MovieGridRecyclerAdapter.MovieGridRecyclerAdapterOnClickHandler(){
                    @Override
                    void onClick(int movieId, MovieGridRecyclerAdapter.MovieGridRecyclerAdapterViewHolder viewHolder) {
                        mCallback.onMovieGridItemSelected(movieId, mMovieCategory, viewHolder)
                    }
                })
//        mGridAdapter = new MovieGridAdapter(getActivity(),null,0)
        mRecyclerView.setAdapter(mGridRecyclerAdapter)
//        mGridView.setAdapter(mGridAdapter)
        //The following line ensure the gridview works smoothly inside nestedscrollview
//        ViewCompat.setNestedScrollingEnabled(mGridView,true)
        LogDisplay.callLog(LOG_TAG,"Movie Category->$mMovieCategory",LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
        //The more load feature is not needed for user list
        if(mMovieCategory == GlobalStaticVariables.MOVIE_CATEGORY_POPULAR ||
           mMovieCategory == GlobalStaticVariables.MOVIE_CATEGORY_TOP_RATED ||
           mMovieCategory == GlobalStaticVariables.MOVIE_CATEGORY_UPCOMING ||
           mMovieCategory == GlobalStaticVariables.MOVIE_CATEGORY_NOW_PLAYING) {
            mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    LogDisplay.callLog(LOG_TAG, "state=$newState",LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
                    //Pause / resume Picasso based on scroll state
                    if(newState == RecyclerView.SCROLL_STATE_IDLE || newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        Picasso.with(getActivity()).resumeTag(GlobalStaticVariables.PICASSO_POSTER_IMAGE_TAG)
                    } else {
                        Picasso.with(getActivity()).pauseTag(GlobalStaticVariables.PICASSO_POSTER_IMAGE_TAG)
                    }
                }

                @Override
                void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    final GridLayoutManager gridLayoutManager = recyclerView.getLayoutManager() as GridLayoutManager
                    final int totalItemCount = gridLayoutManager.getItemCount()
                    final int lastVisibleItemPosition = gridLayoutManager.findLastVisibleItemPosition()

                    LogDisplay.callLog(LOG_TAG, "Last visible item=$lastVisibleItemPosition : ToatlItemCount = $totalItemCount",LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
                    // If the total item count is zero and the previous isn't, assume the
                    // list is invalidated and should be reset back to initial state
                    if (totalItemCount < mPreviousRecordCount) {
                        LogDisplay.callLog(LOG_TAG, 'List invalidated and reset took place.', LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
                        mCurrentPage = mStartPage
                        mPreviousRecordCount = totalItemCount
                        mReTryCounter = 0
                        if (totalItemCount == 0) {
                            isMoreDataToLoad = true
                        }
                    }
                    // If it's still loading, we check to see if the dataset count has
                    // changed, if so we conclude it has finished loading and update the current page
                    // number and total item count.
                    if (isMoreDataToLoad && (totalItemCount > mPreviousRecordCount)) {
                        LogDisplay.callLog(LOG_TAG, 'Just started or loaded a new page and cursor updated.', LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
                        isMoreDataToLoad = false
                        mPreviousRecordCount = totalItemCount
                        mCurrentPage++
                        mReTryCounter = 0
                    }
                    // If it isn’t currently loading, we check to see if we have breached
                    // the visibleThreshold and need to reload more data.
                    // If we do need to reload some more data, we execute LoadMoreMovies to fetch the data.
                    // threshold should reflect how many total columns there are too
                    if (!isMoreDataToLoad && (lastVisibleItemPosition + mThreasholdCount) >= totalItemCount) {
                        isMoreDataToLoad = true
                        if (mMovieCategory != 'error') {
                            String[] movieCategory = [mMovieCategory] as String[]
                            LogDisplay.callLog(LOG_TAG, 'Going to load more data...', LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
                            new LoadMoreMovies(getActivity(), mCurrentPage).execute(movieCategory)
                        }
                    }

                    //Last API called failed, so give it another try but try max 5 times only
                    //If still does not work, then stop
                    if (isDataLoadFailed && mReTryCounter < 5) {
                        isDataLoadFailed = false
                        final String[] movieCategory = [mMovieCategory] as String[]
                        mReTryCounter++
                        LogDisplay.callLog(LOG_TAG, "Last API call failed, going to re-try...try # $mReTryCounter", LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
                        new LoadMoreMovies(getActivity(), mCurrentPage).execute(movieCategory)
                    }
                }
            })
        }  else {
            LogDisplay.callLog(LOG_TAG, "User list or collection grid view, so load more logic is skipped.Movie Category->$mMovieCategory",LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
        }


//        if(mMovieCategory == GlobalStaticVariables.MOVIE_CATEGORY_POPULAR ||
//           mMovieCategory == GlobalStaticVariables.MOVIE_CATEGORY_TOP_RATED ||
//           mMovieCategory == GlobalStaticVariables.MOVIE_CATEGORY_UPCOMING ||
//           mMovieCategory == GlobalStaticVariables.MOVIE_CATEGORY_NOW_PLAYING) {
//            mGridView.setOnScrollListener(new AbsListView.OnScrollListener() {
//                @Override
//                void onScrollStateChanged(AbsListView view, int scrollState) {
//                    LogDisplay.callLog(LOG_TAG, "ScrollState = $scrollState", LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
//                }
//                @Override
//                void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
//                    LogDisplay.callLog(LOG_TAG, "First visible item=$firstVisibleItem : " +
//                            "VisibleItemCount = $visibleItemCount : ToatlItemCount = $totalItemCount",
//                            LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
//                    // If the total item count is zero and the previous isn't, assume the
//                    // list is invalidated and should be reset back to initial state
//                    if (totalItemCount < mPreviousRecordCount) {
//                        LogDisplay.callLog(LOG_TAG, 'List invalidated and reset took place.', LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
//                        mCurrentPage = mStartPage
//                        mPreviousRecordCount = totalItemCount
//                        mReTryCounter = 0
//                        if (totalItemCount == 0) {
//                            isMoreDataToLoad = true
//                        }
//                    }
//                    // If it's still loading, we check to see if the dataset count has
//                    // changed, if so we conclude it has finished loading and update the current page
//                    // number and total item count.
//                    if (isMoreDataToLoad && (totalItemCount > mPreviousRecordCount)) {
//                        LogDisplay.callLog(LOG_TAG, 'Just started or loaded a new page and cursor updated.', LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
//                        isMoreDataToLoad = false
//                        mPreviousRecordCount = totalItemCount
//                        mCurrentPage++
//                        mReTryCounter = 0
//                    }
//                    if (!isMoreDataToLoad && (totalItemCount - visibleItemCount) <= firstVisibleItem + mThreasholdCount) {
//                        isMoreDataToLoad = true
//                        if (mMovieCategory != 'error') {
//                            String[] movieCategory = [mMovieCategory] as String[]
//                            LogDisplay.callLog(LOG_TAG, 'Going to load more data...', LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
//                            new LoadMoreMovies(getActivity(), mCurrentPage).execute(movieCategory)
//                        }
//                    }
//                    //Last API called failed, so give it another try but try max 5 times only
//                    //If still does not work, then stop
//                    if (isDataLoadFailed && mReTryCounter < 5) {
//                        isDataLoadFailed = false
//                        final String[] movieCategory = [mMovieCategory] as String[]
//                        mReTryCounter++
//                        LogDisplay.callLog(LOG_TAG, "Last API call failed, going to re-try...try # $mReTryCounter", LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
//                        new LoadMoreMovies(getActivity(), mCurrentPage).execute(movieCategory)
//                    }
//                }
//            })
//        }  else {
//            LogDisplay.callLog(LOG_TAG, "User list, so load more logic is skipped.Movie Category->$mMovieCategory",LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
//        }
        //Add onClickListner
//        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//            void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                final Cursor cursor = mGridAdapter.getCursor()
//                cursor.moveToPosition(position)
//                final int movieId = cursor.getInt(COL_MOVIE_ID)
////                final long movieRowId = cursor.getLong(COL_MOVIE_ROW_ID)
////                final ImageView imageViewId = view.findViewById(R.id.grid_image_view) as ImageView
////                mCallback.onItemSelected(movieId, movieRowId, imageViewId)
////                mCallback.onItemSelected(movieId, imageViewId)
//                mCallback.onItemSelected(movieId)
//                Toast.makeText(getActivity(), "Item clicked- positon: $position, id:$id & movieId:$movieId", Toast.LENGTH_SHORT).show()
//                }
//            })
        return mRootView
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        LogDisplay.callLog(LOG_TAG, 'onActivityCreated is called', LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
        super.onActivityCreated(savedInstanceState)
        //If savedInstanceState (i.e. in case of restore), restore the value of mMovieCategory
        if (savedInstanceState) {
            //TODO: looks like this is not needed, so commented out
//            mMovieCategory = savedInstanceState.getString(GlobalStaticVariables.MOVIE_BASIC_INFO_CATEGORY, 'error')
//            mMovieCollectionId = savedInstanceState.getInt(GlobalStaticVariables.MOVIE_BASIC_INFO_COLL_ID, 0)
        }
        //If it's a fresh start then call init loader
        if(savedInstanceState == null) {
            LogDisplay.callLog(LOG_TAG, 'onActivityCreated:first time, so init loaders', LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
            getLoaderManager().initLoader(MOVIE_GRID_FRAGMENT_LOADER_ID, null, this)
        } else {        //If it's restore then restart the loader
            LogDisplay.callLog(LOG_TAG, 'onActivityCreated:not first time, so restart loaders', LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
            getLoaderManager().restartLoader(MOVIE_GRID_FRAGMENT_LOADER_ID, null, this)
        }
    }

    @Override
    void onSaveInstanceState(Bundle outState) {
        LogDisplay.callLog(LOG_TAG, 'onSaveInstanceState is called', LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
        //save the mMovieCategory & mMovieCollectionId, so that in case the fragment restores it can retrieve the value properly
        //TODO: looks like this is not needed, so commented out
//        outState.putString(GlobalStaticVariables.MOVIE_BASIC_INFO_CATEGORY,mMovieCategory)
//        outState.putInt(GlobalStaticVariables.MOVIE_BASIC_INFO_COLL_ID,mMovieCollectionId)
        // Now call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(outState)
    }

    @Override
    Loader<Cursor> onCreateLoader(int id, Bundle args) {
        LogDisplay.callLog(LOG_TAG, "onCreateLoader is called.loader id->$id", LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
        //Sort Order: Ascending id, this ensures the list is populated as returned by the API
        //Also it ensures last page number is correct
        final String sortOrder = "$MovieMagicContract.MovieBasicInfo._ID ASC"
        //Build the URI with movie category
        final Uri movieCategoryUri = MovieMagicContract.MovieBasicInfo.buildMovieUriWithMovieCategory(mMovieCategory)
        //Decide the uri based on request type (i.e. collection movies or rest)
        final Uri uri
        if(mMovieCollectionId == 0) {
            uri = movieCategoryUri
        } else {
            uri = mMovieCategoryAndCollectionIdUri
            mCollectionGridFlag = true
        }

        switch (id) {
            case MOVIE_GRID_FRAGMENT_LOADER_ID:
                return new CursorLoader(
                        getActivity(),          //Parent Activity Context
                        uri,                    //Table to query
                        MOVIE_COLUMNS,          //Projection to return
                        null,                   //Selection Clause, null->will return all data
                        null,                   //Selection Arg, null-> will return all data
                        sortOrder)              //Sort order, will be sorted by date in ascending order
            default:
                return null
        }
    }

    @Override
    void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        LogDisplay.callLog(LOG_TAG,'onLoadFinished is called',LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
        data.moveToLast()
        if(data.getCount() > 0) {
            mStartPage = data.getInt(COL_MOVIE_PAGE_NUM)
            mCurrentPage = mStartPage
            mMovieListType = data.getString(COL_MOVIE_LIST_TYPE)
            LogDisplay.callLog(LOG_TAG, "Start Page # $mStartPage", LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
//            mGridRecyclerAdapter.swapCursor(data)
            if(mCollectionGridFlag) {
                //Call CollectionColorChangeCallback which Collection activity will use to change the grid color
                mCollectionColorChangeCallback.notifyCollectionColorChange()
                mCollectionGridFlag = false
            }
        } else {
            LogDisplay.callLog(LOG_TAG, 'Empty cursor returned by loader', LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
        }
//        mGridAdapter.swapCursor(data)
          mGridRecyclerAdapter.swapCursor(data)
    }

    @Override
    void onLoaderReset(Loader<Cursor> loader) {
        LogDisplay.callLog(LOG_TAG,'onLoaderReset is called',LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
        mGridRecyclerAdapter.swapCursor(null)
//        mGridAdapter.swapCursor(null)
    }

//    public static MovieGridRecyclerAdapter getRecyclerAdapter() {
//        return mGridRecyclerAdapter
//    }
    @Override
    public void onAttach(Context context) {
        LogDisplay.callLog(LOG_TAG,'onAttach is called',LogDisplay.GRID_MOVIE_FRAGMENT_LOG_FLAG)
        super.onAttach(context)
        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            if(context instanceof Activity) {
                mCallback = (Callback) context
            }
        } catch (ClassCastException e) {
            throw new ClassCastException(getActivity().toString()
                    + " must implement Callback interface")
        }
        try {
            if(context instanceof Activity) {
                mCollectionColorChangeCallback = (CollectionColorChangeCallback) context
            }
        } catch (ClassCastException e) {
            throw new ClassCastException(getActivity().toString()
                    + " must implement Callback interface")
        }
    }
    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selection.
     */
    public interface Callback {
        /**
         * GridFragmentCallback when an item has been selected.
         */
//        public void onItemSelected(int movieId, long movie_magic_row_ID, ImageView gridImageView)
//        public void onItemSelected(int movieId, ImageView gridImageView)
        public void onMovieGridItemSelected(int movieId, String movieCategory, MovieGridRecyclerAdapter.MovieGridRecyclerAdapterViewHolder viewHolder)
    }

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selection.
     */
    public interface CollectionColorChangeCallback {
        /**
         * GridFragmentCallback to change cloro for collection grid when data is loaded
         */
        public void notifyCollectionColorChange()
    }
}