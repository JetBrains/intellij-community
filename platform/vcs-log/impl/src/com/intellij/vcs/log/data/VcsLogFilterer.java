package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graphmodel.GraphModel;
import com.intellij.vcs.log.ui.VcsLogUI;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import com.intellij.vcs.log.ui.tables.NoGraphTableModel;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class VcsLogFilterer {

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
    GraphModel graphModel = myLogDataHolder.getDataPack().getGraphModel();
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
      graphModel.setVisibleBranchesNodes(ALL_NODES_VISIBLE);
    }

    // apply details filters, and use simple table without graph (we can't filter by details and keep the graph yet).
    if (!detailsFilters.isEmpty()) {
      List<Pair<VcsFullCommitDetails, VirtualFile>> filteredCommits = filterByDetails(graphModel, detailsFilters);
      myUI.setModel(new NoGraphTableModel(filteredCommits, myLogDataHolder.getDataPack().getRefsModel()));
    }
    else {
      myUI.setModel(new GraphTableModel(myLogDataHolder));
    }

    myUI.updateUI();
  }

  private static void applyGraphFilters(GraphModel graphModel, final List<VcsLogGraphFilter> onGraphFilters) {
    graphModel.setVisibleBranchesNodes(new Function<Node, Boolean>() {
      @Override
      public Boolean fun(final Node node) {
        return !ContainerUtil.exists(onGraphFilters, new Condition<VcsLogGraphFilter>() {
          @Override
          public boolean value(VcsLogGraphFilter filter) {
            return !filter.matches(node.getCommitHash());
          }
        });
      }
    });
  }

  private List<Pair<VcsFullCommitDetails, VirtualFile>> filterByDetails(final GraphModel graphModel,
                                                                        final List<VcsLogDetailsFilter> detailsFilters) {
    return ContainerUtil.mapNotNull(myLogDataHolder.getTopCommitDetails(),
                                    new Function<VcsFullCommitDetails, Pair<VcsFullCommitDetails,VirtualFile>>() {
      @Override
      public Pair<VcsFullCommitDetails, VirtualFile> fun(final VcsFullCommitDetails details) {
        boolean allFilterMatch = !ContainerUtil.exists(detailsFilters, new Condition<VcsLogDetailsFilter>() {
          @Override
          public boolean value(VcsLogDetailsFilter filter) {
            return !filter.matches(details);
          }
        });
        Node node = graphModel.getNodeIfVisible(details.getHash());
        if (node == null || !allFilterMatch) {
          return null;
        }
        return Pair.create(details, node.getBranch().getRepositoryRoot());
      }
    });
  }

}
