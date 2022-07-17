// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.elements.GraphNodeType;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.facade.bek.BekChecker;
import com.intellij.vcs.log.graph.impl.facade.bek.BekIntMap;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map;

public class BekBaseController implements LinearGraphController {
  @NotNull private final BekIntMap myBekIntMap;
  @NotNull private final LinearGraph myBekGraph;

  public BekBaseController(@NotNull PermanentGraphInfo<?> permanentGraphInfo, @NotNull BekIntMap bekIntMap) {
    myBekIntMap = bekIntMap;
    myBekGraph = new BekLinearGraph(myBekIntMap, permanentGraphInfo.getLinearGraph());

    BekChecker.checkLinearGraph(myBekGraph);
  }

  @NotNull
  public BekIntMap getBekIntMap() {
    return myBekIntMap;
  }

  @NotNull
  @Override
  public LinearGraph getCompiledGraph() {
    return myBekGraph;
  }

  @NotNull
  @Override
  public LinearGraphController.LinearGraphAnswer performLinearGraphAction(@NotNull LinearGraphController.LinearGraphAction action) {
    return LinearGraphUtils.DEFAULT_GRAPH_ANSWER;
  }

  public static class BekLinearGraph implements LinearGraph {
    @NotNull private final LinearGraph myLinearGraph;
    @NotNull private final BekIntMap myBekIntMap;

    public BekLinearGraph(@NotNull BekIntMap bekIntMap, @NotNull LinearGraph linearGraph) {
      myLinearGraph = linearGraph;
      myBekIntMap = bekIntMap;
    }

    @Override
    public int nodesCount() {
      return myLinearGraph.nodesCount();
    }

    @Nullable
    private Integer getNodeIndex(@Nullable Integer nodeId) {
      if (nodeId == null) return null;

      return myBekIntMap.getBekIndex(nodeId);
    }

    @NotNull
    @Override
    public List<GraphEdge> getAdjacentEdges(int nodeIndex, @NotNull EdgeFilter filter) {
      return map(myLinearGraph.getAdjacentEdges(myBekIntMap.getUsualIndex(nodeIndex), filter),
                 edge -> new GraphEdge(getNodeIndex(edge.getUpNodeIndex()), getNodeIndex(edge.getDownNodeIndex()), edge.getTargetId(),
                                       edge.getType()));
    }

    @NotNull
    @Override
    public GraphNode getGraphNode(int nodeIndex) {
      assert inRanges(nodeIndex);

      return new GraphNode(nodeIndex, GraphNodeType.USUAL);
    }

    @Override
    public int getNodeId(int nodeIndex) {
      // see com.intellij.vcs.log.graph.impl.permanent.PermanentLinearGraphImpl.getNodeId
      return myBekIntMap.getUsualIndex(nodeIndex);
    }

    @Nullable
    @Override
    public Integer getNodeIndex(int nodeId) {
      if (!inRanges(nodeId)) return null;

      return myBekIntMap.getBekIndex(nodeId);
    }

    private boolean inRanges(int index) {
      return index >= 0 && index < nodesCount();
    }
  }
}
