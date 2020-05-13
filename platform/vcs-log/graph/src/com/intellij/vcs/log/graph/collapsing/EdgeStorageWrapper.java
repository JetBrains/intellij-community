// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.collapsing;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import com.intellij.util.SmartList;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.intEqual;

public class EdgeStorageWrapper {
  @NotNull private final EdgeStorage myEdgeStorage;
  @NotNull private final Function<? super Integer, Integer> myGetNodeIndexById;
  @NotNull private final Function<? super Integer, Integer> myGetNodeIdByIndex;

  public EdgeStorageWrapper(@NotNull EdgeStorage edgeStorage, @NotNull final LinearGraph graph) {
    this(edgeStorage, nodeId -> graph.getNodeIndex(nodeId), nodeIndex -> graph.getNodeId(nodeIndex));
  }

  public EdgeStorageWrapper(@NotNull EdgeStorage edgeStorage,
                            @NotNull Function<? super Integer, Integer> getNodeIndexById,
                            @NotNull Function<? super Integer, Integer> getNodeIdByIndex) {
    myEdgeStorage = edgeStorage;
    myGetNodeIndexById = getNodeIndexById;
    myGetNodeIdByIndex = getNodeIdByIndex;
  }

  public void removeEdge(@NotNull GraphEdge graphEdge) {
    Pair<Integer, Integer> nodeIds = getNodeIds(graphEdge);
    myEdgeStorage.removeEdge(nodeIds.first, nodeIds.second, graphEdge.getType());
  }

  public void createEdge(@NotNull GraphEdge graphEdge) {
    Pair<Integer, Integer> nodeIds = getNodeIds(graphEdge);
    myEdgeStorage.createEdge(nodeIds.first, nodeIds.second, graphEdge.getType());
  }

  public boolean hasEdge(int fromIndex, int toIndex) {
    int toId = myGetNodeIdByIndex.fun(toIndex);
    for (Pair<Integer, GraphEdgeType> edge : myEdgeStorage.getEdges(myGetNodeIdByIndex.fun(fromIndex))) {
      if (edge.second.isNormalEdge() && intEqual(edge.first, toId)) return true;
    }
    return false;
  }

  @NotNull
  public List<GraphEdge> getAdjacentEdges(int nodeIndex, @NotNull EdgeFilter filter) {
    List<GraphEdge> result = new SmartList<>();
    for (Pair<Integer, GraphEdgeType> retrievedEdge : myEdgeStorage.getEdges(myGetNodeIdByIndex.fun(nodeIndex))) {
      GraphEdge edge = decompressEdge(nodeIndex, retrievedEdge.first, retrievedEdge.second);
      if (matchedEdge(nodeIndex, edge, filter)) result.add(edge);
    }
    return result;
  }

  @NotNull
  public Set<GraphEdge> getEdges() {
    Set<GraphEdge> result = new HashSet<>();
    for (int id : myEdgeStorage.getKnownIds()) {
      result.addAll(getAdjacentEdges(myGetNodeIndexById.fun(id), EdgeFilter.ALL));
    }
    return result;
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

  @Nullable
  private GraphEdge decompressEdge(int nodeIndex, @Nullable Integer targetId, @NotNull GraphEdgeType edgeType) {
    if (edgeType.isNormalEdge()) {
      assert targetId != null;
      Integer anotherNodeIndex = myGetNodeIndexById.fun(targetId);
      if (anotherNodeIndex == null) return null; // todo edge to hide node

      return GraphEdge.createNormalEdge(nodeIndex, anotherNodeIndex, edgeType);
    }
    else {
      return GraphEdge.createEdgeWithTargetId(nodeIndex, targetId, edgeType);
    }
  }

  private static boolean matchedEdge(int startNodeIndex, @Nullable GraphEdge edge, @NotNull EdgeFilter filter) {
    if (edge == null) return false;
    if (edge.getType().isNormalEdge()) {
      return (startNodeIndex == convertToInt(edge.getDownNodeIndex()) && filter.upNormal) ||
             (startNodeIndex == convertToInt(edge.getUpNodeIndex()) && filter.downNormal);
    }
    else {
      return filter.special;
    }
  }

  private static int convertToInt(@Nullable Integer value) {
    return value == null ? EdgeStorage.NULL_ID : value;
  }

  public void removeAll() {
    myEdgeStorage.removeAll();
  }

  public static EdgeStorageWrapper createSimpleEdgeStorage() {
    return new EdgeStorageWrapper(new EdgeStorage(), Functions.id(), Functions.id());
  }
}
