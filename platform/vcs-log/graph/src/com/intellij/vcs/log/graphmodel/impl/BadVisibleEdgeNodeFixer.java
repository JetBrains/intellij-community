package com.intellij.vcs.log.graphmodel.impl;

import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.mutable.GraphDecorator;
import com.intellij.vcs.log.graph.mutable.elements.MutableNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ListIterator;

/**
 * @author erokhins
 */
public class BadVisibleEdgeNodeFixer implements GraphDecorator {
  private final GraphDecorator decorator;

  public BadVisibleEdgeNodeFixer(GraphDecorator decorator) {
    this.decorator = decorator;
  }


  private boolean isVisibleBadEdgeNode(@NotNull Node node) {
    if (node.getType() != Node.NodeType.EDGE_NODE || decorator.isVisibleNode(node)) {
      return false;
    }
    boolean isBad = false;
    for (Edge edge : ((MutableNode)node).getInnerUpEdges()) {
      Node upNode = edge.getUpNode();
      if (!decorator.isVisibleNode(upNode) || isVisibleBadEdgeNode(upNode)) {
        isBad = true;
      }
    }
    return isBad;
  }

  @Override
  public boolean isVisibleNode(@NotNull Node node) {
    if (isVisibleBadEdgeNode(node)) {
      return false;
    }
    return decorator.isVisibleNode(node);
  }

  @NotNull
  private Node getDownNode(@NotNull Node edgeNode) {
    return decorator.getDownEdges(edgeNode, ((MutableNode)edgeNode).getInnerDownEdges()).get(0).getDownNode();
  }


  @NotNull
  @Override
  public List<Edge> getDownEdges(@NotNull Node node, @NotNull List<Edge> innerDownEdges) {
    List<Edge> prevDownEdges = decorator.getDownEdges(node, innerDownEdges);
    for (ListIterator<Edge> edgeIterator = prevDownEdges.listIterator(); edgeIterator.hasNext(); ) {
      Edge edge = edgeIterator.next();
      if (isVisibleBadEdgeNode(edge.getDownNode())) {
        Node downNode = edge.getDownNode();
        while (isVisibleBadEdgeNode(downNode)) {
          downNode = getDownNode(downNode);
        }
        Edge newEdge = null;
        edgeIterator.set(newEdge);
      }
    }
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @NotNull
  @Override
  public List<Edge> getUpEdges(@NotNull Node node, @NotNull List<Edge> innerUpEdges) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
