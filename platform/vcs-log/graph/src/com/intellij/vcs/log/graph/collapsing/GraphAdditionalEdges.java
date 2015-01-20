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
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.utils.IntIntMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;


public class GraphAdditionalEdges {
  private static final int EDGE_TYPE_BITS = 4;
  private static final int EDGE_BITS_OFFSET = Integer.SIZE - EDGE_TYPE_BITS;
  private static final int COMPRESSED_NODE_ID_MASK = 0xffffffff >>> EDGE_TYPE_BITS;
  private static final int MAX_EDGE_TYPE_COUNT = 1 << EDGE_TYPE_BITS;
  private static final int MAX_NODE_ID = Integer.MAX_VALUE >> EDGE_TYPE_BITS;
  private static final int MIN_NODE_ID = Integer.MIN_VALUE >> EDGE_TYPE_BITS;

  public static final int NULL_ID = MIN_NODE_ID;

  public static GraphAdditionalEdges newInstance(@NotNull Function<Integer, Integer> getNodeIndexById,
                                                 @NotNull Function<Integer, Integer> getNodeIdByIndex) {
    return new GraphAdditionalEdges(getNodeIndexById, getNodeIdByIndex, new IntIntMultiMap());
  }

  public static GraphAdditionalEdges updateInstance(@NotNull GraphAdditionalEdges prevAdditionEdges,
                                                    @NotNull Function<Integer, Integer> getNodeIndexById,
                                                    @NotNull Function<Integer, Integer> getNodeIdByIndex) {
    return new GraphAdditionalEdges(getNodeIndexById, getNodeIdByIndex, prevAdditionEdges.myAdditionEdges);
  }

  @NotNull private final Function<Integer, Integer> myGetNodeIndexById;
  @NotNull private final Function<Integer, Integer> myGetNodeIdByIndex;
  @NotNull private final IntIntMultiMap myAdditionEdges;

  private GraphAdditionalEdges(@NotNull Function<Integer, Integer> getNodeIndexById,
                               @NotNull Function<Integer, Integer> getNodeIdByIndex,
                               @NotNull IntIntMultiMap additionEdges) {
    myGetNodeIndexById = getNodeIndexById;
    myGetNodeIdByIndex = getNodeIdByIndex;
    myAdditionEdges = additionEdges;

    assert GraphEdgeType.values().length <= MAX_EDGE_TYPE_COUNT;
  }


  public void createEdge(@NotNull GraphEdge graphEdge) {
    Pair<Integer, Integer> nodeIds = getNodeIds(graphEdge);
    createEdge(nodeIds.first, nodeIds.second, graphEdge.getType());
  }

  public void createEdge(int mainNodeId, int additionId, GraphEdgeType edgeType) {
    if (edgeType.isNormalEdge()) {
      myAdditionEdges.putValue(mainNodeId, compressEdge(additionId, edgeType));
      myAdditionEdges.putValue(additionId, compressEdge(mainNodeId, edgeType));
    }
    else {
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
    }
    else {
      myAdditionEdges.remove(mainNodeId, compressEdge(additionId, edgeType));
    }
  }

  @TestOnly
  public int[] getKnownIds() {
    return myAdditionEdges.keys();
  }

  private static boolean correctEdge(int startNodeIndex, @NotNull GraphEdge edge, @NotNull EdgeFilter filter) {
    if (edge.getType().isNormalEdge()) {
      return (startNodeIndex == convertToInt(edge.getDownNodeIndex()) && filter.upNormal)
        || (startNodeIndex == convertToInt(edge.getUpNodeIndex()) && filter.downNormal);
    }
    else return filter.special;
  }

  public void appendAdditionalEdges(@NotNull List<GraphEdge> result, int nodeIndex, @NotNull EdgeFilter filter) {
    for (int compressEdge : myAdditionEdges.get(myGetNodeIdByIndex.fun(nodeIndex))) {
      GraphEdge edge = decompressEdge(nodeIndex, compressEdge);
      if (edge != null && correctEdge(nodeIndex, edge, filter)) result.add(edge);
    }
  }

  public void removeAdditionalEdges(@NotNull List<GraphEdge> result, int nodeIndex, @NotNull EdgeFilter filter) {
    for (int compressedEdge : myAdditionEdges.get(myGetNodeIdByIndex.fun(nodeIndex))) {
      GraphEdge edge = decompressEdge(nodeIndex, compressedEdge);
      if (edge != null && correctEdge(nodeIndex, edge, filter)) result.remove(edge);
    }
  }

  public boolean hasEdge(int fromIndex, int toIndex) {
    for (int compressedEdge : myAdditionEdges.get(myGetNodeIdByIndex.fun(fromIndex))) {
      int retrievedId = retrievedNodeId(compressedEdge);
      int anotherNodeIndex = myGetNodeIndexById.fun(retrievedId);
      if (anotherNodeIndex == toIndex) return true;
    }
    return false;
  }

  @Nullable
  private GraphEdge decompressEdge(int nodeIndex, int compressedEdge) {
    GraphEdgeType edgeType = retrievedType(compressedEdge);
    int retrievedId = retrievedNodeId(compressedEdge);
    if (edgeType.isNormalEdge()) {
      assert retrievedId != NULL_ID;
      int anotherNodeIndex = myGetNodeIndexById.fun(retrievedId);
      if (anotherNodeIndex == -1) return null; // todo edge to hide node

      return GraphEdge.createNormalEdge(nodeIndex, anotherNodeIndex, edgeType);
    }
    else {
      return GraphEdge.createEdgeWithTargetId(nodeIndex, retrievedId != NULL_ID ? retrievedId : null, edgeType);
    }
  }

  @NotNull
  private Pair<Integer, Integer> getNodeIds(@NotNull GraphEdge graphEdge) {
    if (graphEdge.getUpNodeIndex() != null) {
      Integer mainId = myGetNodeIdByIndex.fun(graphEdge.getUpNodeIndex());
      if (graphEdge.getDownNodeIndex() != null) {
        return Pair.create(mainId, myGetNodeIdByIndex.fun(graphEdge.getDownNodeIndex()));
      }
      else {
        return Pair.create(mainId, convertToInt(graphEdge.getTargetId()));
      }
    }
    else {
      assert graphEdge.getDownNodeIndex() != null;
      return Pair.create(myGetNodeIdByIndex.fun(graphEdge.getDownNodeIndex()), convertToInt(graphEdge.getTargetId()));
    }
  }

  private static int convertToInt(@Nullable Integer value) {
    return value == null ? NULL_ID : value;
  }

  private static int compressEdge(int nodeId, GraphEdgeType edgeType) {
    assert nodeId == NULL_ID || (nodeId < MAX_NODE_ID && nodeId > MIN_NODE_ID);
    int type = edgeType.ordinal();
    return (type << EDGE_BITS_OFFSET) | (COMPRESSED_NODE_ID_MASK & nodeId);
  }

  @NotNull
  private static GraphEdgeType retrievedType(int compressEdge) {
    int type = compressEdge >>> EDGE_BITS_OFFSET;
    return GraphEdgeType.values()[type];
  }

  private static int retrievedNodeId(int compressEdge) {
    return (compressEdge << EDGE_TYPE_BITS) >> EDGE_TYPE_BITS;
  }

  public void removeAll() {
    myAdditionEdges.clear();
  }
}
