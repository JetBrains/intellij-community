/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.impl.facade.GraphChanges.EdgeImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class GraphChangesUtil {
  public static final GraphChanges<Integer> SOME_CHANGES = new GraphChanges<Integer>() {
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
    final Set<GraphChanges.Edge<Integer>> edgeChanges = ContainerUtil.newHashSet();

    for (GraphEdge edge : removedEdges) {
      edgeChanges.add(edgeChanged(edge, delegateGraph, true));
    }

    for (GraphEdge edge : addedEdges) {
      edgeChanges.add(edgeChanged(edge, delegateGraph, false));
    }

    return new GraphChanges.GraphChangesImpl<>(Collections.<GraphChanges.Node<Integer>>emptySet(), edgeChanges);
  }
}
