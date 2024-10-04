// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.impl.facade.GraphChanges.EdgeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
public final class GraphChangesUtil {
  public static final GraphChanges<Integer> SOME_CHANGES = new GraphChanges<>() {
    @NotNull
    @Override
    public Collection<Node<Integer>> getChangedNodes() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public Collection<Edge<Integer>> getChangedEdges() {
      return Collections.emptyList();
    }
  };

  public static EdgeImpl<Integer> edgeChanged(@NotNull GraphEdge edge, @NotNull LinearGraph graph, boolean removed) {
    Integer up = null;
    Integer down = null;
    if (edge.getUpNodeIndex() != null) {
      up = graph.getNodeId(edge.getUpNodeIndex());
    }
    if (edge.getDownNodeIndex() != null) {
      down = graph.getNodeId(edge.getDownNodeIndex());
    }
    return new EdgeImpl<>(up, down, edge.getTargetId(), removed);
  }

  public static GraphChanges<Integer> edgesReplaced(Collection<GraphEdge> removedEdges,
                                                    Collection<GraphEdge> addedEdges,
                                                    LinearGraph delegateGraph) {
    final Set<GraphChanges.Edge<Integer>> edgeChanges = new HashSet<>();

    for (GraphEdge edge : removedEdges) {
      edgeChanges.add(edgeChanged(edge, delegateGraph, true));
    }

    for (GraphEdge edge : addedEdges) {
      edgeChanges.add(edgeChanged(edge, delegateGraph, false));
    }

    return new GraphChanges.GraphChangesImpl<>(Collections.emptySet(), edgeChanges);
  }
}
