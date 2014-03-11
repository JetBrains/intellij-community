package com.intellij.vcs.log.data;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import com.intellij.vcs.log.ui.tables.EmptyTableModel;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class VcsLogFilterer {

  private static final Logger LOG = Logger.getInstance(VcsLogFilterer.class);

  private static final int LOAD_MORE_COMMITS_FIRST_STEP_LIMIT = 200;

  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final VcsLogUiImpl myUI;

  public VcsLogFilterer(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUiImpl ui) {
    myLogDataHolder = logDataHolder;
    myUI = ui;
  }

  @NotNull
  public AbstractVcsLogTableModel applyFiltersAndUpdateUi(@NotNull DataPack dataPack, @NotNull VcsLogFilterCollection filters) {
    List<VcsLogDetailsFilter> detailsFilters = filters.getDetailsFilters();

    applyGraphFilters(dataPack, filters.getBranchFilter());
    return applyDetailsFilter(dataPack, detailsFilters);
  }

  private AbstractVcsLogTableModel applyDetailsFilter(DataPack dataPack, List<VcsLogDetailsFilter> detailsFilters) {
    if (!detailsFilters.isEmpty()) {
      List<Hash> filteredCommits = filterByDetails(dataPack, detailsFilters);
      if (filteredCommits.isEmpty()) {
        return new EmptyTableModel(dataPack, myLogDataHolder, myUI, LoadMoreStage.INITIAL);
      }
      else{
        Condition<Integer> filter = getFilterFromCommits(filteredCommits);
        dataPack.getGraphFacade().setFilter(filter);
      }
    }
    else {
      dataPack.getGraphFacade().setFilter(null);
    }
    return new GraphTableModel(dataPack, myLogDataHolder, myUI, LoadMoreStage.INITIAL);
  }

  private Condition<Integer> getFilterFromCommits(List<Hash> filteredCommits) {
    final Set<Integer> commitSet = ContainerUtil.map2Set(filteredCommits, new Function<Hash, Integer>() {
      @Override
      public Integer fun(Hash hash) {
        return myLogDataHolder.getCommitIndex(hash);
      }
    });
    return new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return commitSet.contains(integer);
      }
    };
  }

  public void requestVcs(@NotNull final DataPack dataPack, @NotNull VcsLogFilterCollection filters,
                         @NotNull final LoadMoreStage loadMoreStage, @NotNull final Runnable onSuccess) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int maxCount = loadMoreStage == LoadMoreStage.INITIAL ? LOAD_MORE_COMMITS_FIRST_STEP_LIMIT : -1;
    myLogDataHolder.getFilteredDetailsFromTheVcs(filters, new Consumer<List<Hash>>() {
      @Override
      public void consume(List<Hash> hashes) {
        LoadMoreStage newLoadMoreStage = advanceLoadMoreStage(loadMoreStage);
        TIntHashSet previouslySelected = myUI.getSelectedCommits();
        AbstractVcsLogTableModel model;
        if (hashes.isEmpty()) {
          model = new EmptyTableModel(dataPack, myLogDataHolder, myUI, newLoadMoreStage);
        }
        else {
          dataPack.getGraphFacade().setFilter(getFilterFromCommits(hashes));
          model = new GraphTableModel(dataPack, myLogDataHolder, myUI, newLoadMoreStage);
        }
        myUI.setModel(model, dataPack, previouslySelected);
        myUI.repaintUI();
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

  private void applyGraphFilters(@NotNull final DataPack dataPack, @Nullable final VcsLogBranchFilter branchFilter) {
    myUI.getTable().executeWithoutRepaint(new Runnable() {
      @Override
      public void run() {
        try {
          dataPack.getGraphFacade().setVisibleBranches(branchFilter != null ? getMatchingHeads(dataPack, branchFilter) : null);
        }
        catch (InvalidRequestException e) {
          if (!myLogDataHolder.isFullLogShowing()) {
            myLogDataHolder.showFullLog(EmptyRunnable.getInstance());
            throw new ProcessCanceledException();
          }
          else {
            throw e;
          }
        }
      }
    });
  }

  @NotNull
  private static Collection<Integer> getMatchingHeads(@NotNull DataPack dataPack, @NotNull VcsLogBranchFilter branchFilter) {
    final Collection<String> branchNames = new HashSet<String>(branchFilter.getBranchNames());
    return ContainerUtil.mapNotNull(dataPack.getRefsModel().getAllRefs(), new Function<VcsRef, Integer>() {
      @Override
      public Integer fun(VcsRef ref) {
        if (branchNames.contains(ref.getName())) {
          return ref.getCommitIndex();
        }
        return null;
      }
    });
  }

  @NotNull
  private List<Hash> filterByDetails(@NotNull DataPack dataPack, @NotNull List<VcsLogDetailsFilter> detailsFilters) {
    List<Hash> result = ContainerUtil.newArrayList();
    int topCommits = myLogDataHolder.getSettings().getRecentCommitsCount();
    List<Integer> visibleCommits = VcsLogUtil.getVisibleCommits(dataPack.getGraphFacade());
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
        result.add(details.getHash());
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
