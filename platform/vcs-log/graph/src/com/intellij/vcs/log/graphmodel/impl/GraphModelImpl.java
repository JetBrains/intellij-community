package com.intellij.vcs.log.graphmodel.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.graph.Graph;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.mutable.GraphAppendBuilder;
import com.intellij.vcs.log.graph.mutable.MutableGraph;
import com.intellij.vcs.log.graphmodel.FragmentManager;
import com.intellij.vcs.log.graphmodel.GraphModel;
import com.intellij.vcs.log.graphmodel.fragment.FragmentManagerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author erokhins
 */
public class GraphModelImpl implements GraphModel {
  private final MutableGraph graph;
  private final Collection<VcsRef> myRefs;
  private final FragmentManagerImpl fragmentManager;
  private final BranchVisibleNodes visibleNodes;
  private final List<Consumer<UpdateRequest>> listeners = new ArrayList<Consumer<UpdateRequest>>();
  private final GraphBranchShowFixer branchShowFixer;

  private static final Logger LOG = Logger.getInstance(GraphModelImpl.class);

  private Function<Node, Boolean> isStartedBranchVisibilityNode = new Function<Node, Boolean>() {
    @NotNull
    @Override
    public Boolean fun(@NotNull Node key) {
      return true;
    }
  };

  public GraphModelImpl(MutableGraph graph, Collection<VcsRef> allRefs) {
    this.graph = graph;
    myRefs = allRefs;
    this.fragmentManager = new FragmentManagerImpl(graph, new FragmentManagerImpl.CallBackFunction() {
      @Override
      public UpdateRequest runIntermediateUpdate(@NotNull Node upNode, @NotNull Node downNode) {
        return GraphModelImpl.this.updateIntermediate(upNode, downNode);
      }

      @Override
      public void fullUpdate() {
        GraphModelImpl.this.fullUpdate();
      }
    });
    this.visibleNodes = new BranchVisibleNodes(graph);
    visibleNodes.setVisibleNodes(visibleNodes.generateVisibleBranchesNodes(isStartedBranchVisibilityNode));
    branchShowFixer = new GraphBranchShowFixer(graph, fragmentManager);
    graph.setGraphDecorator(new GraphDecoratorImpl(fragmentManager.getGraphPreDecorator(), new Function<Node, Boolean>() {
      @NotNull
      @Override
      public Boolean fun(@NotNull Node key) {
        return visibleNodes.isVisibleNode(key);
      }
    }));
    graph.updateVisibleRows();
  }

  @NotNull
  private UpdateRequest updateIntermediate(@NotNull Node upNode, @NotNull Node downNode) {
    int upRowIndex = upNode.getRowIndex();
    int downRowIndex = downNode.getRowIndex();
    graph.updateVisibleRows();

    UpdateRequest updateRequest = UpdateRequest.buildFromToInterval(upRowIndex, downRowIndex, upNode.getRowIndex(), downNode.getRowIndex());
    callUpdateListener(updateRequest);
    return updateRequest;
  }

  private void fullUpdate() {
    int oldSize = graph.getNodeRows().size();
    graph.updateVisibleRows();

    int newSize = graph.getNodeRows().size();
    if (newSize == 0) { // empty log. Possible only right after git init
      return;
    }
    if (oldSize == 0) { // this shouldn't happen unless the log is empty
      LOG.error("Old size can't be 0 if newSize is not 0. newSize: " + newSize);
      return;
    }
    int newTo = newSize - 1;
    int oldTo = oldSize - 1;
    UpdateRequest updateRequest = UpdateRequest.buildFromToInterval(0, oldTo, 0, newTo);
    callUpdateListener(updateRequest);
  }

  @NotNull
  @Override
  public Graph getGraph() {
    return graph;
  }

  @Override
  public void appendCommitsToGraph(@NotNull List<GraphCommit> commitParentses) {
    int oldSize = graph.getNodeRows().size();
    new GraphAppendBuilder(graph, myRefs).appendToGraph(commitParentses);
    visibleNodes.setVisibleNodes(visibleNodes.generateVisibleBranchesNodes(isStartedBranchVisibilityNode));
    graph.updateVisibleRows();

    UpdateRequest updateRequest = UpdateRequest.buildFromToInterval(0, oldSize - 1, 0, graph.getNodeRows().size() - 1);
    callUpdateListener(updateRequest);
  }

  @Override
  public void setVisibleBranchesNodes(@NotNull Function<Node, Boolean> isStartedNode) {
    this.isStartedBranchVisibilityNode = isStartedNode;
    Set<Node> prevVisibleNodes = visibleNodes.getVisibleNodes();
    Set<Node> newVisibleNodes = visibleNodes.generateVisibleBranchesNodes(isStartedNode);
    branchShowFixer.fixCrashBranches(prevVisibleNodes, newVisibleNodes);
    visibleNodes.setVisibleNodes(newVisibleNodes);
    fullUpdate();
  }

  @NotNull
  @Override
  public FragmentManager getFragmentManager() {
    return fragmentManager;
  }

  private void callUpdateListener(@NotNull UpdateRequest updateRequest) {
    for (Consumer<UpdateRequest> listener : listeners) {
      listener.consume(updateRequest);
    }
  }

  @Override
  public void addUpdateListener(@NotNull Consumer<UpdateRequest> listener) {
    listeners.add(listener);
  }

  @Override
  public void removeAllListeners() {
    listeners.clear();
  }
}
