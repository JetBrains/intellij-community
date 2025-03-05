// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.impl.permanent;

import com.intellij.util.SmartList;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.Flags;
import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.vcs.log.graph.api.elements.GraphEdgeType.USUAL;

public class PermanentLinearGraphImpl implements LinearGraph {
  private final @NotNull Flags mySimpleNodes;

  private final @NotNull IntList myNodeToEdgeIndex;
  private final @NotNull IntList myLongEdges;

  /*package*/ PermanentLinearGraphImpl(@NotNull Flags simpleNodes, int[] nodeToEdgeIndex, int[] longEdges) {
    mySimpleNodes = simpleNodes;
    myNodeToEdgeIndex = new IntImmutableList(nodeToEdgeIndex);
    myLongEdges = new IntImmutableList(longEdges);
  }

  @Override
  public int nodesCount() {
    return mySimpleNodes.size();
  }

  @Override
  public @NotNull List<GraphEdge> getAdjacentEdges(int nodeIndex, @NotNull EdgeFilter filter) {
    List<GraphEdge> result = new SmartList<>();

    boolean hasUpSimpleEdge = nodeIndex != 0 && mySimpleNodes.get(nodeIndex - 1);
    if (hasUpSimpleEdge && filter.upNormal) result.add(new GraphEdge(nodeIndex - 1, nodeIndex, null, USUAL));

    for (int i = myNodeToEdgeIndex.getInt(nodeIndex); i < myNodeToEdgeIndex.getInt(nodeIndex + 1); i++) {
      int adjacentNode = myLongEdges.getInt(i);

      if (adjacentNode < 0 && filter.special) {
        result.add(GraphEdge.createEdgeWithTargetId(nodeIndex, adjacentNode, GraphEdgeType.NOT_LOAD_COMMIT));
      }
      if (adjacentNode < 0) continue;

      if (nodeIndex > adjacentNode && filter.upNormal) result.add(new GraphEdge(adjacentNode, nodeIndex, null, USUAL));
      if (nodeIndex < adjacentNode && filter.downNormal) result.add(new GraphEdge(nodeIndex, adjacentNode, null, USUAL));
    }

    if (mySimpleNodes.get(nodeIndex) && filter.downNormal) result.add(new GraphEdge(nodeIndex, nodeIndex + 1, null, USUAL));

    return result;
  }

  @Override
  public @NotNull GraphNode getGraphNode(int nodeIndex) {
    return new GraphNode(nodeIndex);
  }

  @Override
  public int getNodeId(int nodeIndex) {
    assert nodeIndex >= 0 && nodeIndex < nodesCount() : "Bad nodeIndex: " + nodeIndex;
    return nodeIndex;
  }

  @Override
  public @Nullable Integer getNodeIndex(int nodeId) {
    if (nodeId >= 0 && nodeId < nodesCount()) {
      return nodeId;
    }
    return null;
  }
}
