package com.intellij.vcs.log.graphmodel.fragment;

import com.intellij.util.Function;
import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.GraphElement;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.mutable.MutableGraph;
import com.intellij.vcs.log.graphmodel.FragmentManager;
import com.intellij.vcs.log.graphmodel.GraphFragment;
import com.intellij.vcs.log.graphmodel.fragment.elements.SimpleGraphFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author erokhins
 */
public class FragmentManagerImpl implements FragmentManager {
  private final MutableGraph graph;
  private final FragmentGenerator fragmentGenerator;
  private final GraphFragmentController graphDecorator = new GraphFragmentController();
  private final CallBackFunction callBackFunction;

  private boolean updateFlag = true;


  public FragmentManagerImpl(MutableGraph graph, CallBackFunction callBackFunction) {
    this.graph = graph;
    this.callBackFunction = callBackFunction;
    fragmentGenerator = new FragmentGenerator(graph);
  }

  public interface CallBackFunction {
    public UpdateRequest runIntermediateUpdate(@NotNull Node upNode, @NotNull Node downNode);

    public void fullUpdate();
  }

  @Override
  public void setUnconcealedNodeFunction(@NotNull Function<Node, Boolean> isUnconcealedNode) {
    fragmentGenerator.setUnconcealedNodeFunction(isUnconcealedNode);
  }

  @NotNull
  @Override
  public GraphPreDecorator getGraphPreDecorator() {
    return graphDecorator;
  }

  @Nullable
  private Edge getHideEdge(@NotNull Node node) {
    for (Edge edge : node.getDownEdges()) {
      if (edge.getType() == Edge.EdgeType.HIDE_FRAGMENT) {
        return edge;
      }
    }
    for (Edge edge : node.getUpEdges()) {
      if (edge.getType() == Edge.EdgeType.HIDE_FRAGMENT) {
        return edge;
      }
    }
    return null;
  }

  @NotNull
  private Node getUpNode(@NotNull GraphElement graphElement) {
    Node node = graphElement.getNode();
    if (node == null) {
      node = graphElement.getEdge().getUpNode();
    }
    return node;
  }

  @NotNull
  @Override
  public Set<Node> allCommitsCurrentBranch(@NotNull GraphElement graphElement) {
    return fragmentGenerator.allCommitsCurrentBranch(getUpNode(graphElement));
  }

  @Override
  public Set<Node> getUpNodes(@NotNull GraphElement graphElement) {
    return fragmentGenerator.getUpNodes(getUpNode(graphElement));
  }



  @Nullable
  @Override
  public GraphFragment relateFragment(@NotNull GraphElement graphElement) {
    Node node = graphElement.getNode();
    if (node != null) {
      Edge edge = getHideEdge(node);
      if (edge != null) {
        return new SimpleGraphFragment(edge.getUpNode(), edge.getDownNode(), Collections.<Node>emptySet());
      }
      else {
        GraphFragment fragment = fragmentGenerator.getFragment(node);
        if (fragment != null && fragment.getDownNode().getRowIndex() >= node.getRowIndex()) {
          return fragment;
        }
        else {
          return null;
        }
      }
    }
    else {
      Edge edge = graphElement.getEdge();
      assert edge != null : "bad graphElement: edge & node is null";
      if (edge.getType() == Edge.EdgeType.HIDE_FRAGMENT) {
        return new SimpleGraphFragment(edge.getUpNode(), edge.getDownNode(), Collections.<Node>emptySet());
      }
      else {
        GraphFragment fragment = fragmentGenerator.getFragment(edge.getUpNode());
        if (fragment != null && fragment.getDownNode().getRowIndex() >= edge.getDownNode().getRowIndex()) {
          return fragment;
        }
        else {
          return null;
        }
      }
    }
  }


  @NotNull
  @Override
  public UpdateRequest changeVisibility(@NotNull GraphFragment fragment) {
    if (fragment.getIntermediateNodes().isEmpty()) {
      return show(fragment);
    }
    else {
      return hide(fragment);
    }
  }

  @NotNull
  public UpdateRequest show(@NotNull GraphFragment fragment) {
    graphDecorator.show(fragment);

    if (updateFlag) {
      return callBackFunction.runIntermediateUpdate(fragment.getUpNode(), fragment.getDownNode());
    }
    else {
      return UpdateRequest.ID_UpdateRequest;
    }
  }

  @NotNull
  public UpdateRequest hide(@NotNull GraphFragment fragment) {
    graphDecorator.hide(fragment);

    if (updateFlag) {
      return callBackFunction.runIntermediateUpdate(fragment.getUpNode(), fragment.getDownNode());
    }
    else {
      return UpdateRequest.ID_UpdateRequest;
    }
  }


  @Nullable
  private Node commitNodeInRow(int rowIndex) {
    for (Node node : graph.getNodeRows().get(rowIndex).getNodes()) {
      if (node.getType() == Node.NodeType.COMMIT_NODE) {
        return node;
      }
    }
    return null;
  }

  @Override
  public void hideAll() {
    int rowIndex = 0;
    updateFlag = false;
    while (rowIndex < graph.getNodeRows().size()) {
      Node node = commitNodeInRow(rowIndex);
      if (node != null) {
        GraphFragment fragment = fragmentGenerator.getMaximumDownFragment(node);
        if (fragment != null) {
          hide(fragment);
        }
      }
      rowIndex++;
    }
    updateFlag = true;
    callBackFunction.fullUpdate();
  }

  @Override
  public void showAll() {
    graphDecorator.showAll();
    callBackFunction.fullUpdate();
  }
}
