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
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.impl.facade.GraphChanges;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class LinearGraphUtils {
  public static final LinearGraphController.LinearGraphAnswer DEFAULT_GRAPH_ANSWER = new LinearGraphController.LinearGraphAnswer() {
    @Nullable
    @Override
    public GraphChanges<Integer> getGraphChanges() {
      return null;
    }

    @Nullable
    @Override
    public Cursor getCursorToSet() {
      return null;
    }

    @Nullable
    @Override
    public Integer getCommitToJump() {
      return null;
    }
  };


  public static boolean intEqual(@Nullable Integer value, int number) {
    return value != null && value == number;
  }

  public static boolean isEdgeToUp(@NotNull GraphEdge edge, int nodeIndex) {
    return intEqual(edge.getDownNodeIndex(), nodeIndex);
  }

  public static boolean isEdgeToDown(@NotNull GraphEdge edge, int nodeIndex) {
    return intEqual(edge.getUpNodeIndex(), nodeIndex);
  }

  public static boolean isNormalEdge(@NotNull GraphEdge edge) {
    if (edge.getType().isNormalEdge()) {
      assert edge.getUpNodeIndex() != null && edge.getDownNodeIndex() != null;
      return true;
    }
    return false;
  }

  @Nullable
  public static Pair<Integer, Integer> asNormalEdge(@NotNull GraphEdge edge) {
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
            return graphEdge.getAdditionInfo();
          return graphEdge.getDownNodeIndex();
        }
        return null;
      }
    });
  }

}
