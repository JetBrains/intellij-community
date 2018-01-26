/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.SingleTaskController;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl.VcsLogFilterCollectionBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

public class VisiblePackRefresherImpl implements VisiblePackRefresher, Disposable {
  private static final Logger LOG = Logger.getInstance(VisiblePackRefresherImpl.class);

  @NotNull private final SingleTaskController<Request, State> myTaskController;
  @NotNull private final VcsLogFilterer myVisiblePackBuilder;
  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogIndex.IndexingFinishedListener myIndexingFinishedListener;
  @NotNull private final List<VisiblePackChangeListener> myVisiblePackChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull private volatile State myState;

  public VisiblePackRefresherImpl(@NotNull Project project,
                                  @NotNull VcsLogData logData,
                                  @NotNull PermanentGraph.SortType initialSortType,
                                  @NotNull VcsLogFilterer builder) {
    myLogData = logData;
    myVisiblePackBuilder = builder;
    myState = new State(initialSortType);

    myTaskController = new SingleTaskController<Request, State>(project, state -> {
      boolean hasChanges = myState.getVisiblePack() != state.getVisiblePack();
      myState = state;
      if (hasChanges) {
        for (VisiblePackChangeListener listener : myVisiblePackChangeListeners) {
          listener.onVisiblePackChange(state.getVisiblePack());
        }
      }
    }, true, this) {
      @NotNull
      @Override
      protected SingleTask startNewBackgroundTask() {
        ProgressIndicator indicator = myLogData.getProgress().createProgressIndicator();
        MyTask task = new MyTask(project, "Applying filters...");
        Future<?> future = ((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(task, indicator, null);
        return new SingleTaskImpl(future, indicator);
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
  public void setValid(boolean validate) {
    myTaskController.request(new ValidateRequest(validate));
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
  public void dispose() {
    myLogData.getIndex().removeListener(myIndexingFinishedListener);
  }

  private class MyTask extends Task.Backgroundable {

    public MyTask(@Nullable Project project, @NotNull String title) {
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
          LOG.error("Error while filtering log by " + requests, t);
        }
      }

      List<MoreCommitsRequest> requestsToRun = ContainerUtil.newArrayList();
      if (state.getVisiblePack() != myState.getVisiblePack() && state.isValid()) {
        requestsToRun.addAll(state.getRequestsToRun());
        state = state.withRequests(ContainerUtil.newArrayList());
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

    @NotNull
    private State computeState(@NotNull State state, @NotNull List<Request> requests) {
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
          return refresh(state, filterRequest, moreCommitsRequests);
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
            state = refresh(state, filterRequest, moreCommitsRequests);
          }
          return state.withVisiblePack(new SnapshotVisiblePackBuilder(myLogData.getStorage()).build(state.getVisiblePack()));
        }

        Request nonValidateRequest =
          ContainerUtil.find(requests, request -> !(request instanceof ValidateRequest) && !(request instanceof IndexingFinishedRequest));

        // only doing something if there are some other requests or a relevant indexing request
        if (nonValidateRequest != null) {
          return refresh(state, filterRequest, moreCommitsRequests);
        }
        else if (!indexingRequests.isEmpty()) {
          if (myVisiblePackBuilder.areFiltersAffectedByIndexing(state.getFilters(),
                                                                ContainerUtil.map(indexingRequests, IndexingFinishedRequest::getRoot))) {
            return refresh(state, filterRequest, moreCommitsRequests);
          }
        }
        return state;
      }
    }

    @NotNull
    private State refresh(@NotNull State state,
                          @Nullable FilterRequest filterRequest,
                          @NotNull List<MoreCommitsRequest> moreCommitsRequests) {
      DataPack dataPack = myLogData.getDataPack();

      if (dataPack == DataPack.EMPTY) { // when filter is set during initialization, just remember filters
        return state;
      }

      if (filterRequest != null) {
        // "more commits needed" has no effect if filter changes; it also can't come after filter change request
        state = state.withCommitCount(CommitCountStage.INITIAL);
      }
      else if (!moreCommitsRequests.isEmpty()) {
        state = state.withCommitCount(state.getCommitCount().next());
      }

      Pair<VisiblePack, CommitCountStage> pair = myVisiblePackBuilder.filter(dataPack, state.getSortType(), state.getFilters(),
                                                                             state.getCommitCount());
      return state.withVisiblePack(pair.first).withCommitCount(pair.second);
    }
  }

  private static class State {
    @NotNull private final VcsLogFilterCollection myFilters;
    @NotNull private final PermanentGraph.SortType mySortType;
    @NotNull private final CommitCountStage myCommitCount;
    @NotNull private final List<MoreCommitsRequest> myRequestsToRun;
    @NotNull private final VisiblePack myVisiblePack;
    private final boolean myIsValid;

    public State(@NotNull PermanentGraph.SortType sortType) {
      this(new VcsLogFilterCollectionBuilder().build(), sortType, CommitCountStage.INITIAL, ContainerUtil.newArrayList(), VisiblePack.EMPTY,
           true);
    }

    public State(@NotNull VcsLogFilterCollection filters,
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

    @NotNull
    public VisiblePack getVisiblePack() {
      return myVisiblePack;
    }

    @NotNull
    public List<MoreCommitsRequest> getRequestsToRun() {
      return myRequestsToRun;
    }

    @NotNull
    public VcsLogFilterCollection getFilters() {
      return myFilters;
    }

    @NotNull
    public PermanentGraph.SortType getSortType() {
      return mySortType;
    }

    @NotNull
    public CommitCountStage getCommitCount() {
      return myCommitCount;
    }

    @NotNull
    public State withValid(boolean valid) {
      return new State(myFilters, mySortType, myCommitCount, myRequestsToRun, myVisiblePack, valid);
    }

    @NotNull
    public State withVisiblePack(@NotNull VisiblePack visiblePack) {
      return new State(myFilters, mySortType, myCommitCount, myRequestsToRun, visiblePack, myIsValid);
    }

    @NotNull
    public State withCommitCount(@NotNull CommitCountStage commitCount) {
      return new State(myFilters, mySortType, commitCount, myRequestsToRun, myVisiblePack, myIsValid);
    }

    @NotNull
    public State withRequests(@NotNull List<MoreCommitsRequest> requests) {
      return new State(myFilters, mySortType, myCommitCount, requests, myVisiblePack, myIsValid);
    }

    @NotNull
    public State withFilters(@NotNull VcsLogFilterCollection filters) {
      return new State(filters, mySortType, myCommitCount, myRequestsToRun, myVisiblePack, myIsValid);
    }

    @NotNull
    public State withSortType(@NotNull PermanentGraph.SortType type) {
      return new State(myFilters, type, myCommitCount, myRequestsToRun, myVisiblePack, myIsValid);
    }

    @Override
    public String toString() {
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
    @NotNull private final Runnable onLoaded;

    MoreCommitsRequest(@NotNull Runnable onLoaded) {
      this.onLoaded = onLoaded;
    }
  }

  private static final class IndexingFinishedRequest implements Request {
    @NotNull private final VirtualFile root;

    IndexingFinishedRequest(@NotNull VirtualFile root) {
      this.root = root;
    }

    @NotNull
    public VirtualFile getRoot() {
      return root;
    }

    @Override
    public String toString() {
      return "IndexingFinishedRequest for " + root;
    }
  }
}
