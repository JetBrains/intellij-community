// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.SingleTaskController;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogProgress;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.graph.PermanentGraph;
import kotlin.Pair;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

import static com.intellij.vcs.log.visible.VcsLogFiltererImplKt.areFiltersAffectedByIndexing;

public class VisiblePackRefresherImpl implements VisiblePackRefresher, Disposable {
  private static final Logger LOG = Logger.getInstance(VisiblePackRefresherImpl.class);

  private final @NotNull String myLogId;
  private final @NotNull SingleTaskController<Request, State> myTaskController;
  private final @NotNull VcsLogFilterer myVcsLogFilterer;
  private final @NotNull VcsLogData myLogData;
  private final @NotNull VcsLogIndex.IndexingFinishedListener myIndexingFinishedListener;
  private final @NotNull List<VisiblePackChangeListener> myVisiblePackChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private volatile @NotNull State myState;

  public VisiblePackRefresherImpl(@NotNull Project project,
                                  @NotNull VcsLogData logData,
                                  @NotNull VcsLogFilterCollection filters,
                                  @NotNull PermanentGraph.SortType sortType,
                                  @NotNull VcsLogFilterer filterer,
                                  @NotNull String logId) {
    myLogData = logData;
    myVcsLogFilterer = filterer;
    myLogId = logId;
    myState = new State(filters, sortType);

    myTaskController = new SingleTaskController<>("visible " + StringUtil.trimMiddle(logId, 40), state -> {
      boolean hasChanges = myState.getVisiblePack() != state.getVisiblePack();
      myState = state;
      if (hasChanges) {
        for (VisiblePackChangeListener listener : myVisiblePackChangeListeners) {
          listener.onVisiblePackChange(state.getVisiblePack());
        }
      }
    }, this) {
      @Override
      protected @NotNull SingleTask startNewBackgroundTask() {
        ProgressIndicator indicator = myLogData.getProgress().createProgressIndicator(new VisiblePackProgressKey(myLogId, false));
        MyTask task = new MyTask(project, VcsLogBundle.message("vcs.log.applying.filters.process"));
        Future<?> future = ((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(task, indicator, null);
        return new SingleTaskImpl(future, indicator);
      }

      @Override
      protected boolean cancelRunningTasks(@NotNull List<Request> requests) {
        return ContainerUtil.findInstance(requests, IndexingFinishedRequest.class) != null ||
               ContainerUtil.findInstance(requests, FilterRequest.class) != null;
      }
    };

    myIndexingFinishedListener = root -> myTaskController.request(new IndexingFinishedRequest(root));
    myLogData.getIndex().addListener(myIndexingFinishedListener);
  }

  @Override
  public void addVisiblePackChangeListener(@NotNull VisiblePackChangeListener listener) {
    myVisiblePackChangeListeners.add(listener);
  }

  @Override
  public void removeVisiblePackChangeListener(@NotNull VisiblePackChangeListener listener) {
    myVisiblePackChangeListeners.remove(listener);
  }

  @Override
  public void onRefresh() {
    myTaskController.request(new RefreshRequest());
  }

  @Override
  public void setValid(boolean validate, boolean refresh) {
    if (refresh) {
      myTaskController.request(new RefreshRequest(), new ValidateRequest(validate));
    }
    else {
      myTaskController.request(new ValidateRequest(validate));
    }
  }

  @Override
  public void onFiltersChange(@NotNull VcsLogFilterCollection newFilters) {
    myTaskController.request(new FilterRequest(newFilters));
  }

  @Override
  public void onSortTypeChange(@NotNull PermanentGraph.SortType sortType) {
    myTaskController.request(new SortTypeRequest(sortType));
  }

  @Override
  public void moreCommitsNeeded(@NotNull Runnable onLoaded) {
    myTaskController.request(new MoreCommitsRequest(onLoaded));
  }

  @Override
  public boolean isValid() {
    return myState.isValid();
  }

  @Override
  public String toString() {
    return "VisiblePackRefresher '" + myLogId + "' state = " + myState;
  }

  @Override
  public void dispose() {
    myLogData.getIndex().removeListener(myIndexingFinishedListener);
  }

  private class MyTask extends Task.Backgroundable {

    MyTask(@Nullable Project project, @Nls(capitalization = Nls.Capitalization.Title) @NotNull String title) {
      super(project, title, false);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      State state = myState;
      List<Request> requests;
      if (!(requests = myTaskController.peekRequests()).isEmpty()) {
        try {
          state = computeState(state, requests);
          myTaskController.removeRequests(requests);
        }
        catch (ProcessCanceledException reThrown) {
          LOG.debug("Filtering cancelled");
          // need to start new bg task if cancelled
          myTaskController.taskCompleted(null);
          throw reThrown;
        }
        catch (Throwable t) {
          LOG.error("Error while processing requests " + requests, t);
          myTaskController.removeRequests(requests);
        }
      }

      List<MoreCommitsRequest> requestsToRun = new ArrayList<>();
      if (state.getVisiblePack() != myState.getVisiblePack() && state.isValid() &&
          !(state.getVisiblePack() instanceof VisiblePack.ErrorVisiblePack)) {
        requestsToRun.addAll(state.getRequestsToRun());
        state = state.withRequests(new ArrayList<>());
      }

      myTaskController.taskCompleted(state);

      if (!requestsToRun.isEmpty()) {
        ApplicationManager.getApplication().invokeLater(() -> {
          for (MoreCommitsRequest request : requestsToRun) {
            request.onLoaded.run();
          }
        });
      }
    }

    private @NotNull State computeState(@NotNull State state, @NotNull List<? extends Request> requests) {
      ValidateRequest validateRequest = ContainerUtil.findLastInstance(requests, ValidateRequest.class);
      FilterRequest filterRequest = ContainerUtil.findLastInstance(requests, FilterRequest.class);
      SortTypeRequest sortTypeRequest = ContainerUtil.findLastInstance(requests, SortTypeRequest.class);
      List<MoreCommitsRequest> moreCommitsRequests = ContainerUtil.findAll(requests, MoreCommitsRequest.class);
      List<IndexingFinishedRequest> indexingRequests = ContainerUtil.findAll(requests, IndexingFinishedRequest.class);

      state = state.withRequests(ContainerUtil.concat(state.getRequestsToRun(), moreCommitsRequests));
      if (filterRequest != null) {
        state = state.withFilters(filterRequest.filters);
      }
      if (sortTypeRequest != null) {
        state = state.withSortType(sortTypeRequest.sortType);
      }

      // On validate requests vs refresh requests.
      // Validate just changes validity (myIsValid field). If myIsValid is already what it needs to be it does nothing.
      // Refresh just tells that new data pack arrived. It does not make this filterer valid (or invalid).
      // So, this two requests bring here two completely different pieces of information.
      // Refresh requests are not explicitly used in this code. Basically what is done is a check that there are some requests apart from
      // instances of ValidateRequest (also we get into this method only when there are some requests in the queue).
      // Refresh request does not carry inside any additional information since current DataPack is just taken from VcsLogDataManager.

      if (!state.isValid()) {
        if (validateRequest != null && validateRequest.validate) {
          state = state.withValid(true);
          return refresh(state, filterRequest != null, moreCommitsRequests);
        }
        else { // validateRequest == null || !validateRequest.validate
          // remember filters
          return state;
        }
      }
      else {
        if (validateRequest != null && !validateRequest.validate) {
          state = state.withValid(false);
          // invalidate
          if (filterRequest != null) {
            state = refresh(state, true, moreCommitsRequests);
          }
          return state.withVisiblePack(new SnapshotVisiblePackBuilder(myLogData.getStorage()).build(state.getVisiblePack()));
        }

        // only doing something if there are some other requests or a relevant indexing request
        boolean indexingFinished = !indexingRequests.isEmpty() &&
                                   areFiltersAffectedByIndexing(state.getFilters(),
                                                                ContainerUtil.map(indexingRequests, IndexingFinishedRequest::getRoot));
        Request nonValidateRequest =
          ContainerUtil.find(requests, request -> !(request instanceof ValidateRequest) && !(request instanceof IndexingFinishedRequest));
        if (nonValidateRequest != null || indexingFinished) {
          // "more commits needed" has no effect if filter changes; it also can't come after filter change request
          return refresh(state, filterRequest != null, moreCommitsRequests);
        }
        return state;
      }
    }

    private @NotNull State refresh(@NotNull State state,
                                   boolean resetCommitCount,
                                   @NotNull List<MoreCommitsRequest> moreCommitsRequests) {
      DataPack dataPack = myLogData.getDataPack();

      if (dataPack == DataPack.EMPTY && !myVcsLogFilterer.canFilterEmptyPack(state.getFilters())) {
        // when filter is set during initialization, just remember filters
        // unless our builder can do something with an empty pack, for example in file history
        return state;
      }

      if (resetCommitCount) {
        state = state.withCommitCount(CommitCountStage.INITIAL);
      }
      else if (!moreCommitsRequests.isEmpty()) {
        state = state.withCommitCount(state.getCommitCount().next());
      }

      VcsLogProgress.updateCurrentKey(new VisiblePackProgressKey(myLogId, resetCommitCount ||
                                                                          state.getVisiblePack().getDataPack() != dataPack ||
                                                                          moreCommitsRequests.isEmpty()));

      try {
        Pair<VisiblePack, CommitCountStage> pair = myVcsLogFilterer.filter(dataPack, state.getVisiblePack(), state.getSortType(),
                                                                           state.getFilters(), state.getCommitCount());
        return state.withVisiblePack(pair.getFirst()).withCommitCount(pair.getSecond());
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable t) {
        return state.withVisiblePack(new VisiblePack.ErrorVisiblePack(dataPack, state.getFilters(), t));
      }
      finally {
        VcsLogProgress.updateCurrentKey(new VisiblePackProgressKey(myLogId, false));
      }
    }
  }

  private static class State {
    private final @NotNull VcsLogFilterCollection myFilters;
    private final @NotNull PermanentGraph.SortType mySortType;
    private final @NotNull CommitCountStage myCommitCount;
    private final @NotNull List<MoreCommitsRequest> myRequestsToRun;
    private final @NotNull VisiblePack myVisiblePack;
    private final boolean myIsValid;

    State(@NotNull VcsLogFilterCollection filters, @NotNull PermanentGraph.SortType sortType) {
      this(filters, sortType, CommitCountStage.INITIAL, new ArrayList<>(), VisiblePack.EMPTY, true);
    }

    State(@NotNull VcsLogFilterCollection filters,
          @NotNull PermanentGraph.SortType sortType,
          @NotNull CommitCountStage commitCountStage,
          @NotNull List<MoreCommitsRequest> requests,
          @NotNull VisiblePack visiblePack,
          boolean isValid) {
      myFilters = filters;
      mySortType = sortType;
      myCommitCount = commitCountStage;
      myRequestsToRun = Collections.unmodifiableList(requests);
      myVisiblePack = visiblePack;
      myIsValid = isValid;
    }

    public boolean isValid() {
      return myIsValid;
    }

    public @NotNull VisiblePack getVisiblePack() {
      return myVisiblePack;
    }

    public @NotNull List<MoreCommitsRequest> getRequestsToRun() {
      return myRequestsToRun;
    }

    public @NotNull VcsLogFilterCollection getFilters() {
      return myFilters;
    }

    public @NotNull PermanentGraph.SortType getSortType() {
      return mySortType;
    }

    public @NotNull CommitCountStage getCommitCount() {
      return myCommitCount;
    }

    public @NotNull State withValid(boolean valid) {
      return new State(myFilters, mySortType, myCommitCount, myRequestsToRun, myVisiblePack, valid);
    }

    public @NotNull State withVisiblePack(@NotNull VisiblePack visiblePack) {
      return new State(myFilters, mySortType, myCommitCount, myRequestsToRun, visiblePack, myIsValid);
    }

    public @NotNull State withCommitCount(@NotNull CommitCountStage commitCount) {
      return new State(myFilters, mySortType, commitCount, myRequestsToRun, myVisiblePack, myIsValid);
    }

    public @NotNull State withRequests(@NotNull List<MoreCommitsRequest> requests) {
      return new State(myFilters, mySortType, myCommitCount, requests, myVisiblePack, myIsValid);
    }

    public @NotNull State withFilters(@NotNull VcsLogFilterCollection filters) {
      return new State(filters, mySortType, myCommitCount, myRequestsToRun, myVisiblePack, myIsValid);
    }

    public @NotNull State withSortType(@NotNull PermanentGraph.SortType type) {
      return new State(myFilters, type, myCommitCount, myRequestsToRun, myVisiblePack, myIsValid);
    }

    @Override
    public @NonNls String toString() {
      return "State{" +
             "myFilters=" + myFilters +
             ", mySortType=" + mySortType +
             ", myCommitCount=" + myCommitCount +
             ", myRequestsToRun=" + myRequestsToRun +
             ", myVisiblePack=" + myVisiblePack +
             ", myIsValid=" + myIsValid +
             '}';
    }
  }

  private interface Request {
  }

  private static final class RefreshRequest implements Request {
    @Override
    public String toString() {
      return "RefreshRequest";
    }
  }

  private static final class ValidateRequest implements Request {
    private final boolean validate;

    private ValidateRequest(boolean validate) {
      this.validate = validate;
    }

    @Override
    public String toString() {
      return "ValidateRequest " + validate;
    }
  }

  private static final class FilterRequest implements Request {
    private final VcsLogFilterCollection filters;

    FilterRequest(VcsLogFilterCollection filters) {
      this.filters = filters;
    }

    @Override
    public String toString() {
      return "FilterRequest by " + filters;
    }
  }

  private static final class SortTypeRequest implements Request {
    private final PermanentGraph.SortType sortType;

    SortTypeRequest(PermanentGraph.SortType sortType) {
      this.sortType = sortType;
    }

    @Override
    public String toString() {
      return "SortTypeRequest " + sortType;
    }
  }

  private static final class MoreCommitsRequest implements Request {
    private final @NotNull Runnable onLoaded;

    MoreCommitsRequest(@NotNull Runnable onLoaded) {
      this.onLoaded = onLoaded;
    }
  }

  private static final class IndexingFinishedRequest implements Request {
    private final @NotNull VirtualFile root;

    IndexingFinishedRequest(@NotNull VirtualFile root) {
      this.root = root;
    }

    public @NotNull VirtualFile getRoot() {
      return root;
    }

    @Override
    public String toString() {
      return "IndexingFinishedRequest for " + root;
    }
  }

  public static class VisiblePackProgressKey extends VcsLogProgress.ProgressKey {
    private final @NotNull String myLogId;
    private final boolean myVisible;

    public VisiblePackProgressKey(@NotNull String logId, boolean visible) {
      super("visible pack for " + logId);
      myLogId = logId;
      myVisible = visible;
    }

    public boolean isVisible() {
      return myVisible;
    }

    public @NotNull String getLogId() {
      return myLogId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      VisiblePackProgressKey key = (VisiblePackProgressKey)o;
      return myVisible == key.myVisible &&
             Objects.equals(myLogId, key.myLogId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), myLogId, myVisible);
    }
  }

  public static boolean isVisibleKeyFor(@NotNull VcsLogProgress.ProgressKey key, @NotNull String logId) {
    if (key instanceof VisiblePackProgressKey visiblePackProgressKey) {
      return visiblePackProgressKey.getLogId().equals(logId) && visiblePackProgressKey.isVisible();
    }
    return false;
  }
}
