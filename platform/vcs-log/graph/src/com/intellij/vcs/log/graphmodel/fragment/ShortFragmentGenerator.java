package com.intellij.vcs.log.graphmodel.fragment;

import com.intellij.util.Function;
import com.intellij.vcs.log.graph.Graph;
import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graphmodel.GraphFragment;
import com.intellij.vcs.log.graphmodel.fragment.elements.SimpleGraphFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class ShortFragmentGenerator {
  private final static int MAX_FRAGMENT_SIZE = 100;

  private final Graph graph;
  private Function<Node, Boolean> isUnconcealedNodes = new Function<Node, Boolean>() {
    @NotNull
    @Override
    public Boolean fun(@NotNull Node key) {
      return false;
    }
  };

  public ShortFragmentGenerator(Graph graph) {
    this.graph = graph;
  }

  public void setUnconcealedNodeFunction(Function<Node, Boolean> isUnconcealedNodes) {
    this.isUnconcealedNodes = isUnconcealedNodes;
  }

  private void addDownNodeToSet(@NotNull Set<Node> nodes, @NotNull Node node) {
    for (Edge edge : node.getDownEdges()) {
      Node downNode = edge.getDownNode();
      nodes.add(downNode);
    }
  }

  private boolean allUpNodeHere(@NotNull Set<Node> here, @NotNull Node node) {
    for (Edge upEdge : node.getUpEdges()) {
      if (!here.contains(upEdge.getUpNode())) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public GraphFragment getDownShortFragment(@NotNull Node startNode) {
    if (startNode.getType() == Node.NodeType.EDGE_NODE) {
      throw new IllegalArgumentException("small fragment may start only with COMMIT_NODE, but this node is: " + startNode);
    }

    Set<Node> upNodes = new HashSet<Node>();
    upNodes.add(startNode);
    Set<Node> notAddedNodes = new HashSet<Node>();
    addDownNodeToSet(notAddedNodes, startNode);

    Node endNode = null;

    int startRowIndex = startNode.getRowIndex() + 1;
    int lastIndex = graph.getNodeRows().size() - 1;

    int countNodes = 0;
    boolean isEnd = false;
    for (int currentRowIndex = startRowIndex; currentRowIndex <= lastIndex && !isEnd; currentRowIndex++) {
      for (Node node : graph.getNodeRows().get(currentRowIndex).getNodes()) {
        if (notAddedNodes.remove(node)) {
          countNodes++;
          if (countNodes > MAX_FRAGMENT_SIZE) {
            isEnd = true;
            break;
          }
          if (notAddedNodes.isEmpty() && node.getType() == Node.NodeType.COMMIT_NODE) {
            if (allUpNodeHere(upNodes, node)) { // i.e. we found smallFragment
              endNode = node;
            }
            isEnd = true;
            break;
          }
          else {
            if (!allUpNodeHere(upNodes, node) || isUnconcealedNodes.fun(node)) {
              isEnd = true;
            }
            upNodes.add(node);
            addDownNodeToSet(notAddedNodes, node);
          }
        }
      }
    }
    if (endNode == null) {
      return null;
    }
    else {
      upNodes.remove(startNode);
      return new SimpleGraphFragment(startNode, endNode, upNodes);
    }
  }


  private void addUpNodeToSet(@NotNull Set<Node> nodes, @NotNull Node node) {
    for (Edge edge : node.getUpEdges()) {
      Node upNode = edge.getUpNode();
      nodes.add(upNode);
    }
  }

  private boolean allDownNodeHere(@NotNull Set<Node> here, @NotNull Node node) {
    for (Edge downEdge : node.getDownEdges()) {
      if (!here.contains(downEdge.getDownNode())) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public GraphFragment getUpShortFragment(@NotNull Node startNode) {
    if (startNode.getType() == Node.NodeType.EDGE_NODE) {
      throw new IllegalArgumentException("small fragment may start only with COMMIT_NODE, but this node is: " + startNode);
    }

    Set<Node> downNodes = new HashSet<Node>();
    downNodes.add(startNode);
    Set<Node> notAddedNodes = new HashSet<Node>();
    addUpNodeToSet(notAddedNodes, startNode);

    Node endNode = null;

    int startRowIndex = startNode.getRowIndex() - 1;
    int lastIndex = 0;

    boolean isEnd = false;
    for (int currentRowIndex = startRowIndex; currentRowIndex >= lastIndex && !isEnd; currentRowIndex--) {
      for (Node node : graph.getNodeRows().get(currentRowIndex).getNodes()) {
        if (notAddedNodes.remove(node)) {
          if (notAddedNodes.isEmpty() && node.getType() == Node.NodeType.COMMIT_NODE) {
            if (allDownNodeHere(downNodes, node)) { // i.e. we found smallFragment
              endNode = node;
            }
            isEnd = true;
            break;
          }
          else {
            if (!allDownNodeHere(downNodes, node) || isUnconcealedNodes.fun(node)) {
              isEnd = true;
            }
            downNodes.add(node);
            addUpNodeToSet(notAddedNodes, node);
          }
        }
      }
    }
    if (endNode == null) {
      return null;
    }
    else {
      downNodes.remove(startNode);
      return new SimpleGraphFragment(endNode, startNode, downNodes);
    }
  }


}
