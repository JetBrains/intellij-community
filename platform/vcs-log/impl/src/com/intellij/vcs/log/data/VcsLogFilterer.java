package com.intellij.vcs.log.data;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graphmodel.GraphModel;
import com.intellij.vcs.log.ui.VcsLogUI;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import com.intellij.vcs.log.ui.tables.NoGraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class VcsLogFilterer {

  private static final Logger LOG = Logger.getInstance(VcsLogFilterer.class);
  
  private static final Function<Node,Boolean> ALL_NODES_VISIBLE = new Function<Node, Boolean>() {
    @Override
    public Boolean fun(Node node) {
      return true;
    }
  };

  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final VcsLogUI myUI;

  public VcsLogFilterer(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUI ui) {
    myLogDataHolder = logDataHolder;
    myUI = ui;
  }

  public void applyFiltersAndUpdateUi(@NotNull Collection<VcsLogFilter> filters) {
    DataPack dataPack = myLogDataHolder.getDataPack();
    final GraphModel graphModel = dataPack.getGraphModel();
    List<VcsLogGraphFilter> graphFilters = ContainerUtil.findAll(filters, VcsLogGraphFilter.class);
    List<VcsLogDetailsFilter> detailsFilters = ContainerUtil.findAll(filters, VcsLogDetailsFilter.class);

    // it is important to apply graph filters first:
    // if we apply other filters first, we loose the graph and won't be able to apple graph filters in that case
    // (e.g. won't be able to find out if a commit belongs to the branch selected by user).

    // hide invisible nodes from the graph
    if (!graphFilters.isEmpty()) {
      applyGraphFilters(graphModel, graphFilters);
    }
    else {
      myUI.getTable().executeWithoutRepaint(new Runnable() {
        @Override
        public void run() {
          graphModel.setVisibleBranchesNodes(ALL_NODES_VISIBLE);
        }
      });
    }

    // apply details filters, and use simple table without graph (we can't filter by details and keep the graph yet).
    final AbstractVcsLogTableModel model;
    if (!detailsFilters.isEmpty()) {
      List<VcsFullCommitDetails> filteredCommits = filterByDetails(dataPack, graphModel, detailsFilters);
      model = new NoGraphTableModel(myUI, filteredCommits, dataPack.getRefsModel(), true);
    }
    else {
      model = new GraphTableModel(myLogDataHolder, myUI);
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
          model.requestToLoadMore();
        }
      }
    });
  }

  public void requestVcs(@NotNull Collection<VcsLogFilter> filters, final Runnable onSuccess) {
    myLogDataHolder.getFilteredDetailsFromTheVcs(filters, new Consumer<List<VcsFullCommitDetails>>() {
      @Override
      public void consume(List<VcsFullCommitDetails> details) {
        myUI.setModel(new NoGraphTableModel(myUI, details, myLogDataHolder.getDataPack().getRefsModel(), false));
        myUI.updateUI();
        onSuccess.run();
      }
    });
  }

  private void applyGraphFilters(final GraphModel graphModel, final List<VcsLogGraphFilter> onGraphFilters) {
    myUI.getTable().executeWithoutRepaint(new Runnable() {
      @Override
      public void run() {
        graphModel.setVisibleBranchesNodes(new Function<Node, Boolean>() {
          @Override
          public Boolean fun(final Node node) {
            return !ContainerUtil.exists(onGraphFilters, new Condition<VcsLogGraphFilter>() {
              @Override
              public boolean value(VcsLogGraphFilter filter) {
                return !filter.matches(node.getCommitIndex());
              }
            });
          }
        });
      }
    });
  }

  private List<VcsFullCommitDetails> filterByDetails(DataPack dataPack, final GraphModel graphModel,
                                                     final List<VcsLogDetailsFilter> detailsFilters) {
    List<VcsFullCommitDetails> result = ContainerUtil.newArrayList();
    int topCommits = myLogDataHolder.getSettings().getRecentCommitsCount();
    NodeAroundProvider nodeAroundProvider = new NodeAroundProvider(dataPack, myLogDataHolder);
    for (int i = 0; i < topCommits && i < graphModel.getGraph().getNodeRows().size(); i++) {
      Node node = graphModel.getGraph().getCommitNodeInRow(i);
      if (node == null) {
        // there can be nodes which contain no commits (IDEA-115442, branch filter case)
        continue;
      }
      final VcsFullCommitDetails details = getDetailsFromCache(node, nodeAroundProvider);
      if (details == null) {
        // Details for recent commits should be available in the cache.
        // However if they are not there for some reason, we stop filtering.
        // If we continue, if this commit without details matches filters,
        // if details of an older commit are found in the cache, and if this older commit matches the filter,
        // then we will return the list which incorrectly misses some matching commit in the middle.
        // => Instead we rather will return a smaller list: this is not a problem,
        // because the VCS will be requested for filtered details if there are not enough of them.
        LOG.debug("No details found for a recent commit " + myLogDataHolder.getHash(node.getCommitIndex()));
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
  private VcsFullCommitDetails getDetailsFromCache(@NotNull final Node node, @NotNull final NodeAroundProvider nodeAroundProvider) {
    final Ref<VcsFullCommitDetails> ref = Ref.create();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ref.set(myLogDataHolder.getCommitDetailsGetter().getCommitData(node, nodeAroundProvider));
      }
    });
    return ref.get();
  }

}
