/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class VcsLogFiltererImpl implements VcsLogFilterer {
  private static final Logger LOG = Logger.getInstance(VcsLogFiltererImpl.class);

  @NotNull private final SingleTaskController<Request, VisiblePack> myTaskController;
  @NotNull private final VisiblePackBuilder myVisiblePackBuilder;
  @NotNull private final VcsLogData myLogData;

  @NotNull private VcsLogFilterCollection myFilters;
  @NotNull private PermanentGraph.SortType mySortType;
  @NotNull private CommitCountStage myCommitCount = CommitCountStage.INITIAL;
  @NotNull private List<MoreCommitsRequest> myRequestsToRun = ContainerUtil.newArrayList();
  @NotNull private List<VisiblePackChangeListener> myVisiblePackChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  @NotNull private volatile VisiblePack myVisiblePack = VisiblePack.EMPTY;
  private volatile boolean myIsValid = true;

  public VcsLogFiltererImpl(@NotNull final Project project,
                            @NotNull VcsLogData logData,
                            @NotNull PermanentGraph.SortType initialSortType) {
    myLogData = logData;
    myVisiblePackBuilder = myLogData.createVisiblePackBuilder();
    myFilters = new VcsLogFilterCollectionImpl(null, null, null, null, null, null, null);
    mySortType = initialSortType;

    myTaskController = new SingleTaskController<Request, VisiblePack>(visiblePack -> {
      myVisiblePack = visiblePack;
      for (VisiblePackChangeListener listener : myVisiblePackChangeListeners) {
        listener.onVisiblePackChange(visiblePack);
      }
    }) {
      @Override
      protected void startNewBackgroundTask() {
        UIUtil.invokeLaterIfNeeded(() -> {
          MyTask task = new MyTask(project, "Applying filters...");
          ProgressManager.getInstance().runProcessWithProgressAsynchronously(task,
                                                                             myLogData.getProgress().createProgressIndicator());
        });
      }
    };
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
    return myIsValid;
  }

  private class MyTask extends Task.Backgroundable {

    public MyTask(@Nullable Project project, @NotNull String title) {
      super(project, title, false);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      VisiblePack visiblePack = null;
      List<Request> requests;
      while (!(requests = myTaskController.popRequests()).isEmpty()) {
        try {
          visiblePack = getVisiblePack(visiblePack, requests);
        }
        catch (ProcessCanceledException reThrown) {
          throw reThrown;
        }
        catch (Throwable t) {
          LOG.error("Error while filtering log", t);
        }
      }

      // visible pack can be null (e.g. when filter is set during initialization) => we just remember filters set by user
      myTaskController.taskCompleted(visiblePack);

      if (visiblePack != null && myIsValid) {
        final List<MoreCommitsRequest> requestsToRun = myRequestsToRun;
        myRequestsToRun = ContainerUtil.newArrayList();

        ApplicationManager.getApplication().invokeLater(() -> {
          for (MoreCommitsRequest request : requestsToRun) {
            request.onLoaded.run();
          }
        });
      }
    }

    @Nullable
    private VisiblePack getVisiblePack(@Nullable VisiblePack visiblePack, @NotNull List<Request> requests) {
      ValidateRequest validateRequest = ContainerUtil.findLastInstance(requests, ValidateRequest.class);
      FilterRequest filterRequest = ContainerUtil.findLastInstance(requests, FilterRequest.class);
      SortTypeRequest sortTypeRequest = ContainerUtil.findLastInstance(requests, SortTypeRequest.class);
      List<MoreCommitsRequest> moreCommitsRequests = ContainerUtil.findAll(requests, MoreCommitsRequest.class);

      myRequestsToRun.addAll(moreCommitsRequests);
      if (filterRequest != null) {
        myFilters = filterRequest.filters;
      }
      if (sortTypeRequest != null) {
        mySortType = sortTypeRequest.sortType;
      }

      // On validate requests vs refresh requests.
      // Validate just changes validity (myIsValid field). If myIsValid is already what it needs to be it does nothing.
      // Refresh just tells that new data pack arrived. It does not make this filterer valid (or invalid).
      // So, this two requests bring here two completely different pieces of information.
      // Refresh requests are not explicitly used in this code. Basically what is done is a check that there are some requests apart from
      // instances of ValidateRequest (also we get into this method only when there are some requests in the queue).
      // Refresh request does not carry inside any additional information since current DataPack is just taken from VcsLogDataManager.

      if (!myIsValid) {
        if (validateRequest != null && validateRequest.validate) {
          myIsValid = true;
          return refresh(visiblePack, filterRequest, moreCommitsRequests);
        }
        else { // validateRequest == null || !validateRequest.validate
          // remember filters
          return visiblePack;
        }
      }
      else {
        if (validateRequest != null && !validateRequest.validate) {
          myIsValid = false;
          // invalidate
          VisiblePack frozenVisiblePack = visiblePack == null ? myVisiblePack : visiblePack;
          if (filterRequest != null) {
            frozenVisiblePack = refresh(visiblePack, filterRequest, moreCommitsRequests);
          }
          return new FakeVisiblePackBuilder(myLogData.getHashMap()).build(frozenVisiblePack);
        }

        Request nonValidateRequest = ContainerUtil.find(requests, request -> !(request instanceof ValidateRequest));

        if (nonValidateRequest != null) {
          // only doing something if there are some other requests
          return refresh(visiblePack, filterRequest, moreCommitsRequests);
        }
        else {
          return visiblePack;
        }
      }
    }

    private VisiblePack refresh(@Nullable VisiblePack visiblePack,
                                @Nullable FilterRequest filterRequest,
                                @NotNull List<MoreCommitsRequest> moreCommitsRequests) {
      DataPack dataPack = myLogData.getDataPack();

      if (dataPack == DataPack.EMPTY) { // when filter is set during initialization, just remember filters
        return visiblePack;
      }

      if (filterRequest != null) {
        // "more commits needed" has no effect if filter changes; it also can't come after filter change request
        myCommitCount = CommitCountStage.INITIAL;
      }
      else if (!moreCommitsRequests.isEmpty()) {
        myCommitCount = myCommitCount.next();
      }

      Pair<VisiblePack, CommitCountStage> pair = myVisiblePackBuilder.build(dataPack, mySortType, myFilters, myCommitCount);
      visiblePack = pair.first;
      myCommitCount = pair.second;
      return visiblePack;
    }
  }

  private interface Request {
  }

  private static final class RefreshRequest implements Request {
  }

  private static final class ValidateRequest implements Request {
    private final boolean validate;

    private ValidateRequest(boolean validate) {
      this.validate = validate;
    }
  }

  private static final class FilterRequest implements Request {
    private final VcsLogFilterCollection filters;

    FilterRequest(VcsLogFilterCollection filters) {
      this.filters = filters;
    }
  }

  private static final class SortTypeRequest implements Request {
    private final PermanentGraph.SortType sortType;

    SortTypeRequest(PermanentGraph.SortType sortType) {
      this.sortType = sortType;
    }
  }

  private static final class MoreCommitsRequest implements Request {
    @NotNull private final Runnable onLoaded;

    MoreCommitsRequest(@NotNull Runnable onLoaded) {
      this.onLoaded = onLoaded;
    }
  }
}
