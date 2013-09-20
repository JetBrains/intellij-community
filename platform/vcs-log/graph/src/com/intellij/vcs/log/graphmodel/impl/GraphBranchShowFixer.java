package com.intellij.vcs.log.graphmodel.impl;

import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.mutable.MutableGraph;
import com.intellij.vcs.log.graph.mutable.elements.MutableNode;
import com.intellij.vcs.log.graph.mutable.elements.MutableNodeRow;
import com.intellij.vcs.log.graphmodel.FragmentManager;
import com.intellij.vcs.log.graphmodel.GraphFragment;
import com.intellij.vcs.log.graphmodel.fragment.FragmentManagerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class GraphBranchShowFixer {
  private final MutableGraph graph;
  private final FragmentManagerImpl fragmentManager;

  private Set<Node> prevVisibleNodes;
  private Set<Node> newVisibleNodes;
  private FragmentManager.GraphPreDecorator fragmentGraphDecorator;

  public GraphBranchShowFixer(MutableGraph graph, FragmentManagerImpl fragmentManager) {
    this.graph = graph;
    this.fragmentManager = fragmentManager;
  }

  public void fixCrashBranches(@NotNull Set<Node> prevVisibleNodes, @NotNull Set<Node> newVisibleNodes) {
    this.prevVisibleNodes = prevVisibleNodes;
    this.newVisibleNodes = newVisibleNodes;
    fragmentGraphDecorator = fragmentManager.getGraphPreDecorator();
    Set<Node> badHiddenNode = new HashSet<Node>();
    for (MutableNodeRow row : graph.getAllRows()) {
      for (MutableNode node : row.getInnerNodeList()) {
        if (isEssentialNode(node)) {
          badHiddenNode.addAll(badHiddenNodes(node));
        }
      }
    }
    showNodes(badHiddenNode);
  }

  private Set<Node> badHiddenNodes(@NotNull MutableNode node) {
    if (fragmentGraphDecorator.getHideFragmentDownEdge(node) != null) {
      return Collections.emptySet();
    }
    Set<Node> badHiddenNodes = new HashSet<Node>();
    for (Edge edge : node.getInnerDownEdges()) {
      Node downNode = edge.getDownNode();
      if (!fragmentGraphDecorator.isVisibleNode(downNode)) {
        badHiddenNodes.add(downNode);
      }
    }
    return badHiddenNodes;
  }

  private boolean isEssentialNode(@NotNull Node node) {
    if (newVisibleNodes.contains(node) && !prevVisibleNodes.contains(node)) {
      if (fragmentGraphDecorator.isVisibleNode(node)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private Edge getExternalHideEdge(@NotNull Node node) {
    while (!fragmentGraphDecorator.isVisibleNode(node)) {
      MutableNode thisNode = (MutableNode)node;
      if (thisNode.getInnerDownEdges().size() == 0) {
        throw new IllegalStateException("not found down visible node of hide node");
      }
      node = thisNode.getInnerDownEdges().get(0).getDownNode();
    }
    return fragmentGraphDecorator.getHideFragmentUpEdge(node);
  }

  private void showNodes(Set<Node> nodes) {
    for (Node node : nodes) {
      while (!fragmentGraphDecorator.isVisibleNode(node)) {
        Edge hideEdge = getExternalHideEdge(node);
        GraphFragment fragment = fragmentManager.relateFragment(hideEdge);
        assert fragment != null : "bad hide edge" + hideEdge;
        fragmentManager.show(fragment);
      }
    }
  }


}
