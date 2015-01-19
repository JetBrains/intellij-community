/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.utils;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class LinearGraphUtils {
  public static final LinearGraphController.LinearGraphAnswer DEFAULT_GRAPH_ANSWER = new LinearGraphController.LinearGraphAnswer(null, null, null);

  public static boolean intEqual(@Nullable Integer value, int number) {
    return value != null && value == number;
  }

  public static boolean isEdgeToUp(@NotNull GraphEdge edge, int nodeIndex) {
    return intEqual(edge.getDownNodeIndex(), nodeIndex);
  }

  public static boolean isEdgeToDown(@NotNull GraphEdge edge, int nodeIndex) {
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
  public static Pair<Integer, Integer> asNormalEdge(@Nullable GraphEdge edge) {
    if (isNormalEdge(edge)) {
      assert edge.getUpNodeIndex() != null && edge.getDownNodeIndex() != null;
      return Pair.create(edge.getUpNodeIndex(), edge.getDownNodeIndex());
    }
    return null;
  }

  public static int getNotNullNodeIndex(@NotNull GraphEdge edge) {
    if (edge.getUpNodeIndex() != null)
      return edge.getUpNodeIndex();
    assert edge.getDownNodeIndex() != null;
    return edge.getDownNodeIndex();
  }

  @NotNull
  public static List<Integer> getUpNodes(@NotNull LinearGraph graph, final int nodeIndex) {
    return ContainerUtil.mapNotNull(graph.getAdjacentEdges(nodeIndex), new Function<GraphEdge, Integer>() {
      @Nullable
      @Override
      public Integer fun(GraphEdge graphEdge) {
        if (isEdgeToUp(graphEdge, nodeIndex))
          return graphEdge.getUpNodeIndex();
        return null;
      }
    });
  }

  @NotNull
  public static List<Integer> getDownNodes(@NotNull LinearGraph graph, final int nodeIndex) {
    return ContainerUtil.mapNotNull(graph.getAdjacentEdges(nodeIndex), new Function<GraphEdge, Integer>() {
      @Nullable
      @Override
      public Integer fun(GraphEdge graphEdge) {
        if (isEdgeToDown(graphEdge, nodeIndex))
          return graphEdge.getDownNodeIndex();
        return null;
      }
    });
  }

  @NotNull
  public static List<Integer> getDownNodesIncludeNotLoad(@NotNull final LinearGraph graph, final int nodeIndex) {
    return ContainerUtil.mapNotNull(graph.getAdjacentEdges(nodeIndex), new Function<GraphEdge, Integer>() {
      @Nullable
      @Override
      public Integer fun(GraphEdge graphEdge) {
        if (isEdgeToDown(graphEdge, nodeIndex)) {
          if (graphEdge.getType() == GraphEdgeType.NOT_LOAD_COMMIT)
            return graphEdge.getTargetId();
          return graphEdge.getDownNodeIndex();
        }
        return null;
      }
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
      public List<Integer> getNodes(final int nodeIndex, final NodeFilter filter) {
        return ContainerUtil.mapNotNull(graph.getAdjacentEdges(nodeIndex), new Function<GraphEdge, Integer>() {
          @Nullable
          @Override
          public Integer fun(GraphEdge edge) {
            if (isEdgeToDown(edge, nodeIndex) && filter.down)
              return edge.getDownNodeIndex();

            if (isEdgeToUp(edge, nodeIndex) && filter.up)
              return edge.getUpNodeIndex();
            return null;
          }
        });
      }
    };
  }

  @NotNull
  public static LinearGraphController.LinearGraphAnswer createCursorAnswer(boolean handCursor) {
    Cursor cursor = Cursor.getDefaultCursor();
    if (handCursor) cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

    return new LinearGraphController.LinearGraphAnswer(null, cursor, null);
  }

  @Nullable
  public static GraphEdge getEdge(@NotNull LinearGraph graph, int up, int down) {
    List<GraphEdge> edges = graph.getAdjacentEdges(up);
    for (GraphEdge edge : edges) {
      if (edge.getDownNodeIndex() != null && edge.getDownNodeIndex() == down) {
        return edge;
      }
    }
    return null;
  }

}
