// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.linearBek;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LinearBekGraph extends LinearGraphWrapper {
  public LinearBekGraph(@NotNull LinearGraph graph) {
    super(graph);
  }

  public Collection<GraphEdge> expandEdge(final @NotNull GraphEdge edge) {
    Set<GraphEdge> result = new HashSet<>();

    assert edge.getType() == GraphEdgeType.DOTTED;
    getDottedEdges().removeEdge(edge);

    Integer tail = edge.getUpNodeIndex();
    Integer firstChild = edge.getDownNodeIndex();
    assert tail != null : "Collapsed from to an unloaded node";
    assert firstChild != null : "Collapsed edge to an unloaded node";

    List<GraphEdge> downDottedEdges = getHiddenEdges().getAdjacentEdges(tail, EdgeFilter.NORMAL_DOWN);
    List<GraphEdge> upDottedEdges = getHiddenEdges().getAdjacentEdges(firstChild, EdgeFilter.NORMAL_UP);
    for (GraphEdge e : ContainerUtil.concat(downDottedEdges, upDottedEdges)) {
      getHiddenEdges().removeEdge(e);
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
      super(graph.getGraph());
      myLinearGraph = graph;
    }

    public Collection<GraphEdge> getAddedEdges() {
      Set<GraphEdge> result = getDottedEdges().getEdges();
      result.removeAll(ContainerUtil.filter(getHiddenEdges().getEdges(), graphEdge -> graphEdge.getType() == GraphEdgeType.DOTTED));
      result.removeAll(myLinearGraph.getDottedEdges().getEdges());
      return result;
    }

    public Collection<GraphEdge> getRemovedEdges() {
      Set<GraphEdge> result = new HashSet<>();
      Set<GraphEdge> hidden = getHiddenEdges().getEdges();
      result.addAll(ContainerUtil.filter(hidden, graphEdge -> graphEdge.getType() != GraphEdgeType.DOTTED));
      result.addAll(ContainerUtil.intersection(hidden, myLinearGraph.getDottedEdges().getEdges()));
      result.removeAll(myLinearGraph.getHiddenEdges().getEdges());
      return result;
    }

    public void applyChanges() {
      myLinearGraph.getDottedEdges().removeAll();
      myLinearGraph.getHiddenEdges().removeAll();

      for (GraphEdge e : getDottedEdges().getEdges()) {
        myLinearGraph.getDottedEdges().createEdge(e);
      }
      for (GraphEdge e : getHiddenEdges().getEdges()) {
        myLinearGraph.getHiddenEdges().createEdge(e);
      }
    }
  }
}
