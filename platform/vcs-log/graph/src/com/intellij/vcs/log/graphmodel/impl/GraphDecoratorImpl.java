package com.intellij.vcs.log.graphmodel.impl;

import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.mutable.GraphDecorator;
import com.intellij.vcs.log.graphmodel.FragmentManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */

public class GraphDecoratorImpl implements GraphDecorator {
  private final FragmentManager.GraphPreDecorator preDecorator;
  private final Function<Node, Boolean> branchVisibleNodes;

  public GraphDecoratorImpl(FragmentManager.GraphPreDecorator preDecorator, Function<Node, Boolean> branchVisibleNodes) {
    this.preDecorator = preDecorator;
    this.branchVisibleNodes = branchVisibleNodes;
  }

  @Override
  public boolean isVisibleNode(@NotNull Node node) {
    return preDecorator.isVisibleNode(node) && branchVisibleNodes.fun(node);
  }

  @NotNull
  @Override
  public List<Edge> getDownEdges(@NotNull Node node, @NotNull List<Edge> innerDownEdges) {
    Edge edge = preDecorator.getHideFragmentDownEdge(node);
    if (edge != null) {
      return new SmartList<Edge>(edge);
    }
    else {
      return Collections.unmodifiableList(innerDownEdges);
    }
  }

  @NotNull
  @Override
  public List<Edge> getUpEdges(@NotNull Node node, @NotNull List<Edge> innerUpEdges) {
    Edge hideFragmentUpEdge = preDecorator.getHideFragmentUpEdge(node);
    if (hideFragmentUpEdge != null) {
      return new SmartList<Edge>(hideFragmentUpEdge);
    }

    List<Edge> edges = new ArrayList<Edge>();
    for (Edge edge : innerUpEdges) {
      if (isVisibleNode(edge.getUpNode())) {
        edges.add(edge);
      }
    }
    return edges;
  }
}
