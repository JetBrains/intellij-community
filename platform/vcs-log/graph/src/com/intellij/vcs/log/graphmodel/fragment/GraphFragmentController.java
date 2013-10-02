package com.intellij.vcs.log.graphmodel.fragment;

import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graphmodel.FragmentManager;
import com.intellij.vcs.log.graphmodel.GraphFragment;
import com.intellij.vcs.log.graphmodel.fragment.elements.HideFragmentEdge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author erokhins
 */
public class GraphFragmentController implements FragmentManager.GraphPreDecorator {
  private final Set<Node> hideNodes = new HashSet<Node>();
  private final Map<Edge, GraphFragment> hideFragments = new HashMap<Edge, GraphFragment>();
  private final MultiMap<Node, Edge> upNodeEdges = new MultiMap<Node, Edge>();
  private final MultiMap<Node, Edge> downNodeEdges = new MultiMap<Node, Edge>();

  @Override
  public boolean isVisibleNode(@NotNull Node node) {
    return !hideNodes.contains(node);
  }

  @Override
  @Nullable
  public Edge getHideFragmentUpEdge(@NotNull Node node) {
    Collection<Edge> edges = downNodeEdges.get(node);
    for (Edge edge : edges) {
      if (isVisibleNode(edge.getUpNode())) {
        return edge;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public Edge getHideFragmentDownEdge(@NotNull Node node) {
    Collection<Edge> edges = upNodeEdges.get(node);
    for (Edge edge : edges) {
      if (isVisibleNode(edge.getDownNode())) {
        return edge;
      }
    }
    return null;
  }

  @NotNull
  private Edge getHideFragmentEdge(@NotNull GraphFragment fragment) {
    if (!fragment.getIntermediateNodes().isEmpty()) {
      throw new IllegalArgumentException("is not hide fragment: " + fragment);
    }
    List<Edge> downEdges = fragment.getUpNode().getDownEdges();
    if (downEdges.size() == 1) {
      Edge edge = downEdges.get(0);
      if (edge.getType() == Edge.EdgeType.HIDE_FRAGMENT && edge.getDownNode() == fragment.getDownNode()) {
        return edge;
      }
    }
    throw new IllegalArgumentException("is bad hide fragment: " + fragment);
  }

  public void show(@NotNull GraphFragment fragment) {
    Edge hideFragmentEdge = getHideFragmentEdge(fragment);
    upNodeEdges.remove(hideFragmentEdge.getUpNode(), hideFragmentEdge);
    downNodeEdges.remove(hideFragmentEdge.getDownNode(), hideFragmentEdge);

    GraphFragment hideFragment = hideFragments.remove(hideFragmentEdge);
    hideNodes.removeAll(hideFragment.getIntermediateNodes());
  }

  public void hide(@NotNull GraphFragment fragment) {
    Edge edge = new HideFragmentEdge(fragment.getUpNode(), fragment.getDownNode());
    upNodeEdges.putValue(edge.getUpNode(), edge);
    downNodeEdges.putValue(edge.getDownNode(), edge);
    hideNodes.addAll(fragment.getIntermediateNodes());
    hideFragments.put(edge, fragment);
  }

  public void showAll() {
    hideNodes.clear();
    hideFragments.clear();
    upNodeEdges.clear();
    downNodeEdges.clear();
  }

}
