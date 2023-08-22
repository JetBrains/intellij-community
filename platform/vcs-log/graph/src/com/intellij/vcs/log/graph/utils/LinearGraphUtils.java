// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.utils;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LinearGraphUtils {
  public static final LinearGraphController.LinearGraphAnswer DEFAULT_GRAPH_ANSWER =
    new LinearGraphController.LinearGraphAnswer(Cursor.getDefaultCursor(), null);

  public static boolean intEqual(@Nullable Integer value, int number) {
    return value != null && value == number;
  }

  public static boolean isEdgeUp(@NotNull GraphEdge edge, int nodeIndex) {
    return intEqual(edge.getDownNodeIndex(), nodeIndex);
  }

  public static boolean isEdgeDown(@NotNull GraphEdge edge, int nodeIndex) {
    return intEqual(edge.getUpNodeIndex(), nodeIndex);
  }

  public static boolean isNormalEdge(@Nullable GraphEdge edge) {
    if (edge != null && edge.getType().isNormalEdge()) {
      assert edge.getUpNodeIndex() != null && edge.getDownNodeIndex() != null;
      return true;
    }
    return false;
  }

  @Nullable
  public static NormalEdge asNormalEdge(@Nullable GraphEdge edge) {
    if (isNormalEdge(edge)) {
      assert edge.getUpNodeIndex() != null && edge.getDownNodeIndex() != null;
      return NormalEdge.create(edge.getUpNodeIndex(), edge.getDownNodeIndex());
    }
    return null;
  }

  public static int getNotNullNodeIndex(@NotNull GraphEdge edge) {
    if (edge.getUpNodeIndex() != null) return edge.getUpNodeIndex();
    assert edge.getDownNodeIndex() != null;
    return edge.getDownNodeIndex();
  }

  @NotNull
  public static List<Integer> getUpNodes(@NotNull LinearGraph graph, final int nodeIndex) {
    return ContainerUtil.mapNotNull(graph.getAdjacentEdges(nodeIndex, EdgeFilter.NORMAL_UP), graphEdge -> graphEdge.getUpNodeIndex());
  }

  @NotNull
  public static List<Integer> getDownNodes(@NotNull LinearGraph graph, final int nodeIndex) {
    return ContainerUtil.mapNotNull(graph.getAdjacentEdges(nodeIndex, EdgeFilter.NORMAL_DOWN), graphEdge -> graphEdge.getDownNodeIndex());
  }

  @NotNull
  public static List<Integer> getDownNodesIncludeNotLoad(@NotNull final LinearGraph graph, final int nodeIndex) {
    return ContainerUtil.mapNotNull(graph.getAdjacentEdges(nodeIndex, EdgeFilter.ALL), graphEdge -> {
      if (isEdgeDown(graphEdge, nodeIndex)) {
        if (graphEdge.getType() == GraphEdgeType.NOT_LOAD_COMMIT) return graphEdge.getTargetId();
        return graphEdge.getDownNodeIndex();
      }
      return null;
    });
  }

  @NotNull
  public static LiteLinearGraph asLiteLinearGraph(@NotNull final LinearGraph graph) {
    return new LiteLinearGraph() {
      @Override
      public int nodesCount() {
        return graph.nodesCount();
      }

      @NotNull
      @Override
      public List<Integer> getNodes(final int nodeIndex, @NotNull final NodeFilter filter) {
        return ContainerUtil.mapNotNull(graph.getAdjacentEdges(nodeIndex, filter.edgeFilter), edge -> {
          if (isEdgeUp(edge, nodeIndex)) return edge.getUpNodeIndex();
          if (isEdgeDown(edge, nodeIndex)) return edge.getDownNodeIndex();

          return null;
        });
      }
    };
  }

  @NotNull
  public static Cursor getCursor(boolean hand) {
    if (hand) {
      return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }
    else {
      return Cursor.getDefaultCursor();
    }
  }

  public static LinearGraphController.LinearGraphAnswer createSelectedAnswer(@NotNull LinearGraph linearGraph,
                                                                             @NotNull Collection<Integer> selectedNodeIndexes) {
    Set<Integer> selectedIds = new HashSet<>();
    for (Integer nodeIndex : selectedNodeIndexes) {
      if (nodeIndex == null) continue;
      selectedIds.add(linearGraph.getNodeId(nodeIndex));
    }
    return new LinearGraphController.LinearGraphAnswer(getCursor(true), selectedIds);
  }

  @Nullable
  public static GraphEdge getEdge(@NotNull LinearGraph graph, int up, int down) {
    List<GraphEdge> edges = graph.getAdjacentEdges(up, EdgeFilter.NORMAL_DOWN);
    for (GraphEdge edge : edges) {
      if (intEqual(edge.getDownNodeIndex(), down)) {
        return edge;
      }
    }
    return null;
  }

  @NotNull
  public static Set<Integer> convertNodeIndexesToIds(@NotNull final LinearGraph graph, @NotNull Collection<Integer> nodeIndexes) {
    return ContainerUtil.map2Set(nodeIndexes, nodeIndex -> graph.getNodeId(nodeIndex));
  }

  @NotNull
  public static Set<Integer> convertIdsToNodeIndexes(@NotNull final LinearGraph graph, @NotNull Collection<Integer> ids) {
    List<Integer> result = ContainerUtil.mapNotNull(ids, id -> graph.getNodeIndex(id));
    return new HashSet<>(result);
  }
}
