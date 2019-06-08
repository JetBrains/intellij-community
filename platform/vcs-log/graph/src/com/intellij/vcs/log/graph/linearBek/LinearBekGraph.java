// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.linearBek;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.collapsing.EdgeStorageWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class LinearBekGraph implements LinearGraph {
  @NotNull protected final LinearGraph myGraph;
  @NotNull protected final EdgeStorageWrapper myHiddenEdges;
  @NotNull protected final EdgeStorageWrapper myDottedEdges;

  public LinearBekGraph(@NotNull LinearGraph graph) {
    myGraph = graph;
    myHiddenEdges = EdgeStorageWrapper.createSimpleEdgeStorage();
    myDottedEdges = EdgeStorageWrapper.createSimpleEdgeStorage();
  }

  @Override
  public int nodesCount() {
    return myGraph.nodesCount();
  }

  @NotNull
  @Override
  public List<GraphEdge> getAdjacentEdges(int nodeIndex, @NotNull EdgeFilter filter) {
    List<GraphEdge> result = new ArrayList<>();
    result.addAll(myDottedEdges.getAdjacentEdges(nodeIndex, filter));
    result.addAll(myGraph.getAdjacentEdges(nodeIndex, filter));
    result.removeAll(myHiddenEdges.getAdjacentEdges(nodeIndex, filter));
    return result;
  }

  @NotNull
  @Override
  public GraphNode getGraphNode(int nodeIndex) {
    return myGraph.getGraphNode(nodeIndex);
  }

  @Override
  public int getNodeId(int nodeIndex) {
    return myGraph.getNodeId(nodeIndex);
  }

  @Nullable
  @Override
  public Integer getNodeIndex(int nodeId) {
    return myGraph.getNodeIndex(nodeId);
  }

  public Collection<GraphEdge> expandEdge(@NotNull final GraphEdge edge) {
    Set<GraphEdge> result = new HashSet<>();

    assert edge.getType() == GraphEdgeType.DOTTED;
    myDottedEdges.removeEdge(edge);

    Integer tail = edge.getUpNodeIndex();
    Integer firstChild = edge.getDownNodeIndex();
    assert tail != null : "Collapsed from to an unloaded node";
    assert firstChild != null : "Collapsed edge to an unloaded node";

    List<GraphEdge> downDottedEdges = myHiddenEdges.getAdjacentEdges(tail, EdgeFilter.NORMAL_DOWN);
    List<GraphEdge> upDottedEdges = myHiddenEdges.getAdjacentEdges(firstChild, EdgeFilter.NORMAL_UP);
    for (GraphEdge e : ContainerUtil.concat(downDottedEdges, upDottedEdges)) {
      myHiddenEdges.removeEdge(e);
      if (e.getType() == GraphEdgeType.DOTTED) {
        result.addAll(expandEdge(e));
      }
      else {
        result.add(e);
      }
    }

    return result;
  }

  public static class WorkingLinearBekGraph extends LinearBekGraph {
    private final LinearBekGraph myLinearGraph;

    public WorkingLinearBekGraph(@NotNull LinearBekGraph graph) {
      super(graph.myGraph);
      myLinearGraph = graph;
    }

    public Collection<GraphEdge> getAddedEdges() {
      Set<GraphEdge> result = myDottedEdges.getEdges();
      result.removeAll(ContainerUtil.filter(myHiddenEdges.getEdges(), graphEdge -> graphEdge.getType() == GraphEdgeType.DOTTED));
      result.removeAll(myLinearGraph.myDottedEdges.getEdges());
      return result;
    }

    public Collection<GraphEdge> getRemovedEdges() {
      Set<GraphEdge> result = new HashSet<>();
      Set<GraphEdge> hidden = myHiddenEdges.getEdges();
      result.addAll(ContainerUtil.filter(hidden, graphEdge -> graphEdge.getType() != GraphEdgeType.DOTTED));
      result.addAll(ContainerUtil.intersection(hidden, myLinearGraph.myDottedEdges.getEdges()));
      result.removeAll(myLinearGraph.myHiddenEdges.getEdges());
      return result;
    }

    public void applyChanges() {
      myLinearGraph.myDottedEdges.removeAll();
      myLinearGraph.myHiddenEdges.removeAll();

      for (GraphEdge e : myDottedEdges.getEdges()) {
        myLinearGraph.myDottedEdges.createEdge(e);
      }
      for (GraphEdge e : myHiddenEdges.getEdges()) {
        myLinearGraph.myHiddenEdges.createEdge(e);
      }
    }
  }
}
