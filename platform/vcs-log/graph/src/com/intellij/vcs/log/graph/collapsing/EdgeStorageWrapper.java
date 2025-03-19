// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.collapsing;

import com.intellij.openapi.util.Pair;
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
import java.util.function.Function;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.intEqual;

public class EdgeStorageWrapper {
  private final @NotNull EdgeStorage myEdgeStorage;
  private final @NotNull Function<? super Integer, Integer> myGetNodeIndexById;
  private final @NotNull Function<? super Integer, Integer> myGetNodeIdByIndex;

  public EdgeStorageWrapper(@NotNull EdgeStorage edgeStorage, final @NotNull LinearGraph graph) {
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
    int toId = myGetNodeIdByIndex.apply(toIndex);
    for (Pair<Integer, GraphEdgeType> edge : myEdgeStorage.getEdges(myGetNodeIdByIndex.apply(fromIndex))) {
      if (edge.second.isNormalEdge() && intEqual(edge.first, toId)) return true;
    }
    return false;
  }

  public @NotNull List<GraphEdge> getAdjacentEdges(int nodeIndex, @NotNull EdgeFilter filter) {
    List<GraphEdge> result = new SmartList<>();
    for (Pair<Integer, GraphEdgeType> retrievedEdge : myEdgeStorage.getEdges(myGetNodeIdByIndex.apply(nodeIndex))) {
      GraphEdge edge = decompressEdge(nodeIndex, retrievedEdge.first, retrievedEdge.second);
      if (matchedEdge(nodeIndex, edge, filter)) result.add(edge);
    }
    return result;
  }

  public @NotNull Set<GraphEdge> getEdges() {
    Set<GraphEdge> result = new HashSet<>();
    for (int id : myEdgeStorage.getKnownIds()) {
      result.addAll(getAdjacentEdges(myGetNodeIndexById.apply(id), EdgeFilter.ALL));
    }
    return result;
  }

  private @NotNull Pair<Integer, Integer> getNodeIds(@NotNull GraphEdge graphEdge) {
    if (graphEdge.getUpNodeIndex() != null) {
      Integer mainId = myGetNodeIdByIndex.apply(graphEdge.getUpNodeIndex());
      if (graphEdge.getDownNodeIndex() != null) {
        return Pair.create(mainId, myGetNodeIdByIndex.apply(graphEdge.getDownNodeIndex()));
      }
      else {
        return Pair.create(mainId, convertToInt(graphEdge.getTargetId()));
      }
    }
    else {
      assert graphEdge.getDownNodeIndex() != null;
      return Pair.create(myGetNodeIdByIndex.apply(graphEdge.getDownNodeIndex()), convertToInt(graphEdge.getTargetId()));
    }
  }

  private @Nullable GraphEdge decompressEdge(int nodeIndex, @Nullable Integer targetId, @NotNull GraphEdgeType edgeType) {
    if (edgeType.isNormalEdge()) {
      assert targetId != null;
      Integer anotherNodeIndex = myGetNodeIndexById.apply(targetId);
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
    return new EdgeStorageWrapper(new EdgeStorage(), Function.identity(), Function.identity());
  }
}
