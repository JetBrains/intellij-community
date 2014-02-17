package com.intellij.vcs.log.data;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogBranchFilter;
import com.intellij.vcs.log.VcsLogDetailsFilter;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogUI;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import com.intellij.vcs.log.ui.tables.NoGraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class VcsLogFilterer {

  private static final Logger LOG = Logger.getInstance(VcsLogFilterer.class);

  private static final int LOAD_MORE_COMMITS_FIRST_STEP_LIMIT = 200;

  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final VcsLogUI myUI;

  public VcsLogFilterer(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUI ui) {
    myLogDataHolder = logDataHolder;
    myUI = ui;
  }

  public void applyFiltersAndUpdateUi(@NotNull VcsLogFilterCollection filters) {
    DataPack dataPack = myLogDataHolder.getDataPack();
    List<VcsLogDetailsFilter> detailsFilters = filters.getDetailsFilters();

    // it is important to apply graph filters first:
    // if we apply other filters first, we loose the graph and won't be able to apple graph filters in that case
    // (e.g. won't be able to find out if a commit belongs to the branch selected by user).

    // hide invisible nodes from the graph
    applyGraphFilters(filters.getBranchFilter());

    // apply details filters, and use simple table without graph (we can't filter by details and keep the graph yet).
    final AbstractVcsLogTableModel model;
    if (!detailsFilters.isEmpty()) {
      List<VcsFullCommitDetails> filteredCommits = filterByDetails(detailsFilters);
      model = new NoGraphTableModel(myUI, filteredCommits, dataPack.getRefsModel(), LoadMoreStage.INITIAL);
    }
    else {
      model = new GraphTableModel(myLogDataHolder);
    }

    updateUi(model);
  }

  private void updateUi(final AbstractVcsLogTableModel model) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myUI.setModel(model);
        myUI.updateUI();

        if (model.getRowCount() == 0) {
          model.requestToLoadMore(EmptyRunnable.INSTANCE);
        }
      }
    });
  }

  public void requestVcs(@NotNull VcsLogFilterCollection filters, final LoadMoreStage loadMoreStage, @NotNull final Runnable onSuccess) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int maxCount = loadMoreStage == LoadMoreStage.INITIAL ? LOAD_MORE_COMMITS_FIRST_STEP_LIMIT : -1;
    myLogDataHolder.getFilteredDetailsFromTheVcs(filters, new Consumer<List<VcsFullCommitDetails>>() {
      @Override
      public void consume(List<VcsFullCommitDetails> details) {
        LoadMoreStage newLoadMoreStage = advanceLoadMoreStage(loadMoreStage);
        myUI.setModel(new NoGraphTableModel(myUI, details, myLogDataHolder.getDataPack().getRefsModel(), newLoadMoreStage));
        myUI.updateUI();
        onSuccess.run();
      }
    }, maxCount);
  }

  @NotNull
  private static LoadMoreStage advanceLoadMoreStage(@NotNull LoadMoreStage loadMoreStage) {
    LoadMoreStage newLoadMoreStage;
    if (loadMoreStage == LoadMoreStage.INITIAL) {
      newLoadMoreStage = LoadMoreStage.LOADED_MORE;
    }
    else if (loadMoreStage == LoadMoreStage.LOADED_MORE) {
      newLoadMoreStage = LoadMoreStage.ALL_REQUESTED;
    }
    else {
      LOG.warn("Incorrect previous load more stage: " + loadMoreStage);
      newLoadMoreStage = LoadMoreStage.ALL_REQUESTED;
    }
    return newLoadMoreStage;
  }

  private void applyGraphFilters(@Nullable final VcsLogBranchFilter branchFilter) {
    myUI.getTable().executeWithoutRepaint(new Runnable() {
      @Override
      public void run() {
        myLogDataHolder.getDataPack().getGraphFacade().setVisibleBranches(branchFilter != null ? branchFilter.getMatchingHeads() : null);
      }
    });
  }

  @NotNull
  private List<VcsFullCommitDetails> filterByDetails(@NotNull List<VcsLogDetailsFilter> detailsFilters) {
    List<VcsFullCommitDetails> result = ContainerUtil.newArrayList();
    int topCommits = myLogDataHolder.getSettings().getRecentCommitsCount();
    List<Integer> visibleCommits = VcsLogUtil.getVisibleCommits(myLogDataHolder.getDataPack().getGraphFacade());
    for (int i = 0; i < topCommits && i < visibleCommits.size(); i++) {
      int commitIndex = visibleCommits.get(i);
      final VcsFullCommitDetails details = getDetailsFromCache(commitIndex);
      if (details == null) {
        // Details for recent commits should be available in the cache.
        // However if they are not there for some reason, we stop filtering.
        // If we continue, if this commit without details matches filters,
        // if details of an older commit are found in the cache, and if this older commit matches the filter,
        // then we will return the list which incorrectly misses some matching commit in the middle.
        // => Instead we rather will return a smaller list: this is not a problem,
        // because the VCS will be requested for filtered details if there are not enough of them.
        LOG.debug("No details found for a recent commit " + myLogDataHolder.getHash(commitIndex));
        break;
      }
      boolean allFiltersMatch = !ContainerUtil.exists(detailsFilters, new Condition<VcsLogDetailsFilter>() {
        @Override
        public boolean value(VcsLogDetailsFilter filter) {
          return !filter.matches(details);
        }
      });
      if (allFiltersMatch) {
        result.add(details);
      }
    }
    return result;
  }

  @Nullable
  private VcsFullCommitDetails getDetailsFromCache(final int commitIndex) {
    final Ref<VcsFullCommitDetails> ref = Ref.create();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ref.set(myLogDataHolder.getCommitDetailsGetter().getCommitDataIfAvailable(myLogDataHolder.getHash(commitIndex)));
      }
    });
    return ref.get();
  }

}
