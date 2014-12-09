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
package com.intellij.vcs.log.graph.collapsing;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.utils.IntIntMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class GraphAdditionEdges {
  public static GraphAdditionEdges newInstance(@NotNull Function<Integer, Integer> getNodeIndexById,
                                               @NotNull Function<Integer, Integer> getNodeIdByIndex) {
    return new GraphAdditionEdges(getNodeIndexById, getNodeIdByIndex, new IntIntMultiMap());
  }

  public static GraphAdditionEdges updateInstance(@NotNull GraphAdditionEdges prevAdditionEdges,
                                                  @NotNull Function<Integer, Integer> getNodeIndexById,
                                                  @NotNull Function<Integer, Integer> getNodeIdByIndex) {
    return new GraphAdditionEdges(getNodeIndexById, getNodeIdByIndex, prevAdditionEdges.myAdditionEdges);
  }

  @NotNull
  private final Function<Integer, Integer> myGetNodeIndexById;
  @NotNull
  private final Function<Integer, Integer> myGetNodeIdByIndex;
  @NotNull
  private final IntIntMultiMap myAdditionEdges;

  private GraphAdditionEdges(@NotNull Function<Integer, Integer> getNodeIndexById,
                             @NotNull Function<Integer, Integer> getNodeIdByIndex,
                             @NotNull IntIntMultiMap additionEdges) {
    myGetNodeIndexById = getNodeIndexById;
    myGetNodeIdByIndex = getNodeIdByIndex;
    myAdditionEdges = additionEdges;
  }


  public void createEdge(@NotNull GraphEdge graphEdge) {
    Pair<Integer, Integer> nodeIds = getNodeIds(graphEdge);
    createEdge(nodeIds.first, nodeIds.second, graphEdge.getType());
  }

  public void createEdge(int mainNodeId, int additionId, GraphEdgeType edgeType) {
    if (edgeType.isNormalEdge()) {
      myAdditionEdges.putValue(mainNodeId, compressEdge(additionId, edgeType));
      myAdditionEdges.putValue(additionId, compressEdge(mainNodeId, edgeType));
    } else {
      myAdditionEdges.putValue(mainNodeId, compressEdge(additionId, edgeType));
    }
  }

  public void removeEdge(@NotNull GraphEdge graphEdge) {
    Pair<Integer, Integer> nodeIds = getNodeIds(graphEdge);
    removeEdge(nodeIds.first, nodeIds.second, graphEdge.getType());
  }

  public void removeEdge(int mainNodeId, int additionId, GraphEdgeType edgeType) {
    if (edgeType.isNormalEdge()) {
      myAdditionEdges.remove(mainNodeId, compressEdge(additionId, edgeType));
      myAdditionEdges.remove(additionId, compressEdge(mainNodeId, edgeType));
    } else {
      myAdditionEdges.remove(mainNodeId, compressEdge(additionId, edgeType));
    }
  }

  public void addToResultAdditionEdges(List<GraphEdge> result, int nodeIndex) {
    for (int compressEdge : myAdditionEdges.get(myGetNodeIdByIndex.fun(nodeIndex))) {
      GraphEdge edge = decompressEdge(nodeIndex, compressEdge);
      if (edge != null)
        result.add(edge);
    }
  }

  @Nullable
  private GraphEdge decompressEdge(int nodeIndex, int compressedEdge) {
    GraphEdgeType edgeType = retrievedType(compressedEdge);
    int retrievedId = retrievedNodeIndex(compressedEdge);
    switch (edgeType) {
      case DOTTED:
      case USUAL:
        int anotherNodeIndex = myGetNodeIndexById.fun(retrievedId);
        if (anotherNodeIndex == -1)
          return null; // todo edge to hide node
        return GraphEdge.createNormalEdge(nodeIndex, anotherNodeIndex, edgeType);

      case DOTTED_ARROW_DOWN:
      case DOTTED_ARROW_UP:
      case NOT_LOAD_COMMIT:
        return GraphEdge.createEdgeWithAdditionInfo(nodeIndex, retrievedId, edgeType);

      default:
        throw new IllegalStateException("Unexpected edgeType: " + edgeType);
    }
  }

  @NotNull
  private Pair<Integer, Integer> getNodeIds(@NotNull GraphEdge graphEdge) {
    if (graphEdge.getUpNodeIndex() != null) {
      Integer mainId = myGetNodeIdByIndex.fun(graphEdge.getUpNodeIndex());
      if (graphEdge.getDownNodeIndex() != null) {
        return Pair.create(mainId, myGetNodeIdByIndex.fun(graphEdge.getDownNodeIndex()));
      } else {
        assert graphEdge.getAdditionInfo() != null;
        return Pair.create(mainId, graphEdge.getAdditionInfo());
      }
    } else {
      assert graphEdge.getDownNodeIndex() != null && graphEdge.getAdditionInfo() != null;
      return Pair.create(myGetNodeIdByIndex.fun(graphEdge.getDownNodeIndex()), graphEdge.getAdditionInfo());
    }
  }

  private static int compressEdge(int nodeIndex, GraphEdgeType edgeType) {
    assert nodeIndex < 0xffffff || nodeIndex > 0x80ffffff;
    byte type = edgeType.getType();
    return (type << 24) | (0xffffff & nodeIndex);
  }

  @NotNull
  private static GraphEdgeType retrievedType(int compressEdge) {
    int type = compressEdge >> 24; // not important >> or >>>
    return GraphEdgeType.getByType((byte) type);
  }

  private static int retrievedNodeIndex(int compressEdge) {
    return (compressEdge << 8) >> 8;
  }

}
