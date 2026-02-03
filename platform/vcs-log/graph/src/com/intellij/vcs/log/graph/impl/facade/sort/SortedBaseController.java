// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort;

import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.elements.GraphNodeType;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map;

public final class SortedBaseController implements LinearGraphController {
  private final @NotNull SortIndexMap mySortIndexMap;
  private final @NotNull LinearGraph mySortedGraph;

  public SortedBaseController(@NotNull PermanentGraphInfo<?> permanentGraphInfo, @NotNull SortIndexMap sortIndexMap) {
    mySortIndexMap = sortIndexMap;
    mySortedGraph = new SortedLinearGraph(mySortIndexMap, permanentGraphInfo.getLinearGraph());

    SortChecker.checkLinearGraph(mySortedGraph);
  }

  public @NotNull SortIndexMap getBekIntMap() {
    return mySortIndexMap;
  }

  @Override
  public @NotNull LinearGraph getCompiledGraph() {
    return mySortedGraph;
  }

  @Override
  public @NotNull LinearGraphController.LinearGraphAnswer performLinearGraphAction(@NotNull LinearGraphController.LinearGraphAction action) {
    return LinearGraphUtils.DEFAULT_GRAPH_ANSWER;
  }

  public static final class SortedLinearGraph implements LinearGraph {
    private final @NotNull LinearGraph myLinearGraph;
    private final @NotNull SortIndexMap mySortIndexMap;

    public SortedLinearGraph(@NotNull SortIndexMap sortIndexMap, @NotNull LinearGraph linearGraph) {
      myLinearGraph = linearGraph;
      mySortIndexMap = sortIndexMap;
    }

    @Override
    public int nodesCount() {
      return myLinearGraph.nodesCount();
    }

    private @Nullable Integer getNodeIndex(@Nullable Integer nodeId) {
      if (nodeId == null) return null;

      return mySortIndexMap.getSortedIndex(nodeId);
    }

    @Override
    public @NotNull List<GraphEdge> getAdjacentEdges(int nodeIndex, @NotNull EdgeFilter filter) {
      return map(myLinearGraph.getAdjacentEdges(mySortIndexMap.getUsualIndex(nodeIndex), filter),
                 edge -> new GraphEdge(getNodeIndex(edge.getUpNodeIndex()), getNodeIndex(edge.getDownNodeIndex()), edge.getTargetId(),
                                       edge.getType()));
    }

    @Override
    public @NotNull GraphNode getGraphNode(int nodeIndex) {
      assert inRanges(nodeIndex);

      return new GraphNode(nodeIndex, GraphNodeType.USUAL);
    }

    @Override
    public int getNodeId(int nodeIndex) {
      // see com.intellij.vcs.log.graph.impl.permanent.PermanentLinearGraphImpl.getNodeId
      return mySortIndexMap.getUsualIndex(nodeIndex);
    }

    @Override
    public @Nullable Integer getNodeIndex(int nodeId) {
      if (!inRanges(nodeId)) return null;

      return mySortIndexMap.getSortedIndex(nodeId);
    }

    private boolean inRanges(int index) {
      return index >= 0 && index < nodesCount();
    }
  }
}
