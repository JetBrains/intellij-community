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
import java.util.Iterator;
import java.util.Set;

/**
 * @author erokhins
 */
public class FragmentGenerator {
  private static final int SEARCH_LIMIT = 20; // 20 nodes

  private final ShortFragmentGenerator shortFragmentGenerator;
  private Function<Node, Boolean> isUnhiddenNodes = new Function<Node, Boolean>() {
    @NotNull
    @Override
    public Boolean fun(@NotNull Node key) {
      return false;
    }
  };

  public FragmentGenerator(Graph graph) {
    shortFragmentGenerator = new ShortFragmentGenerator(graph);
  }

  public void setUnconcealedNodeFunction(Function<Node, Boolean> isUnconcealedNode) {
    shortFragmentGenerator.setUnconcealedNodeFunction(isUnconcealedNode);
    this.isUnhiddenNodes = isUnconcealedNode;
  }

  @NotNull
  public Set<Node> allCommitsCurrentBranch(@NotNull Node node) {
    Set<Node> nodes = new HashSet<Node>();
    //down walk
    Set<Node> downNodes = new HashSet<Node>();
    downNodes.add(node);
    while (!downNodes.isEmpty()) {
      Iterator<Node> iteratorNode = downNodes.iterator();
      Node nextNode = iteratorNode.next();
      iteratorNode.remove();
      if (nodes.add(nextNode)) {
        for (Edge edge: nextNode.getDownEdges()) {
          downNodes.add(edge.getDownNode());
        }
      }
    }

    Set<Node> upNodes = new HashSet<Node>();
    upNodes.add(node);
    nodes.remove(node); // for start process
    while (!upNodes.isEmpty()) {
      Iterator<Node> nodeIterator = upNodes.iterator();
      Node nextNode = nodeIterator.next();
      nodeIterator.remove();
      if (nodes.add(nextNode)) {
        for (Edge edge: nextNode.getUpEdges()) {
          upNodes.add(edge.getUpNode());
        }
      }
    }

    return nodes;
  }

  @NotNull
  public Set<Node> getUpNodes(@NotNull Node node) {
    Set<Node> nodes = new HashSet<Node>();

    Set<Node> upNodes = new HashSet<Node>();
    upNodes.add(node);
    while (!upNodes.isEmpty()) {
      Iterator<Node> nodeIterator = upNodes.iterator();
      Node nextNode = nodeIterator.next();
      nodeIterator.remove();
      if (nodes.add(nextNode)) {
        for (Edge edge: nextNode.getUpEdges()) {
          upNodes.add(edge.getUpNode());
        }
      }
    }

    return nodes;
  }

  public GraphFragment getFragment(@NotNull Node node) {
    int countTry = SEARCH_LIMIT;
    GraphFragment downFragment = null;
    while (countTry > 0 && ((downFragment = getMaximumDownFragment(node)) == null)) {
      countTry--;
      if (node.getUpEdges().isEmpty()) {
        return null;
      }
      else {
        node = node.getUpEdges().get(0).getUpNode();
      }
    }
    if (downFragment == null) {
      return null;
    }
    GraphFragment upFragment = getMaximumUpFragment(node);
    if (upFragment == null) {
      return downFragment;
    }
    else {
      Set<Node> intermediateNodes = new HashSet<Node>(downFragment.getIntermediateNodes());
      intermediateNodes.addAll(upFragment.getIntermediateNodes());
      intermediateNodes.add(node);
      return new SimpleGraphFragment(upFragment.getUpNode(), downFragment.getDownNode(), intermediateNodes);
    }
  }

  @Nullable
  public GraphFragment getMaximumDownFragment(@NotNull Node startNode) {
    if (startNode.getType() != Node.NodeType.COMMIT_NODE) {
      return null;
    }
    GraphFragment fragment = shortFragmentGenerator.getDownShortFragment(startNode);
    if (fragment == null) {
      return null;
    }
    Set<Node> intermediateNodes = new HashSet<Node>(fragment.getIntermediateNodes());
    Node endNode = fragment.getDownNode();
    while ((fragment = shortFragmentGenerator.getDownShortFragment(endNode)) != null && !isUnhiddenNodes.fun(endNode)) {
      intermediateNodes.addAll(fragment.getIntermediateNodes());
      intermediateNodes.add(endNode);
      endNode = fragment.getDownNode();
    }
    if (intermediateNodes.isEmpty()) {
      return null;
    }
    else {
      return new SimpleGraphFragment(startNode, endNode, intermediateNodes);
    }
  }


  @Nullable
  public GraphFragment getMaximumUpFragment(@NotNull Node startNode) {
    GraphFragment fragment = shortFragmentGenerator.getUpShortFragment(startNode);
    if (fragment == null) {
      return null;
    }
    Set<Node> intermediateNodes = new HashSet<Node>(fragment.getIntermediateNodes());
    Node endNode = fragment.getDownNode();
    while ((fragment = shortFragmentGenerator.getUpShortFragment(endNode)) != null && !isUnhiddenNodes.fun(endNode)) {
      intermediateNodes.addAll(fragment.getIntermediateNodes());
      intermediateNodes.add(endNode);
      endNode = fragment.getUpNode();
    }
    if (intermediateNodes.isEmpty()) {
      return null;
    }
    else {
      return new SimpleGraphFragment(endNode, startNode, intermediateNodes);
    }
  }

}
