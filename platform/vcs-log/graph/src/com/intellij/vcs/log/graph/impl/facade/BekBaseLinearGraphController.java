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
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.util.Function;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.elements.GraphNodeType;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.facade.bek.BekChecker;
import com.intellij.vcs.log.graph.impl.facade.bek.BekIntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map;

public class BekBaseLinearGraphController extends CascadeLinearGraphController {
  @NotNull
  private final BekIntMap myBekIntMap;
  @NotNull
  private final LinearGraph myBekGraph;

  public BekBaseLinearGraphController(@NotNull PermanentGraphInfo permanentGraphInfo, @NotNull BekIntMap bekIntMap) {
    super(null, permanentGraphInfo);
    myBekIntMap = bekIntMap;
    myBekGraph = new BekLinearGraph();

    assert BekChecker.checkLinearGraph(myBekGraph); // todo drop later
  }

  @NotNull
  @Override
  protected LinearGraphAnswer performDelegateUpdate(@NotNull LinearGraphAnswer delegateAnswer) {
    throw new IllegalStateException();
  }

  @Nullable
  @Override
  protected LinearGraphAnswer performAction(@NotNull LinearGraphAction action) {
    return null;
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

  private class BekLinearGraph implements LinearGraph {
    @NotNull
    private final LinearGraph myPermanentGraph;

    private BekLinearGraph() {
      myPermanentGraph = myPermanentGraphInfo.getPermanentLinearGraph();
    }

    @Override
    public int nodesCount() {
      return myPermanentGraph.nodesCount();
    }

    @Nullable
    private Integer getNodeIndex(@Nullable Integer nodeId) {
      if (nodeId == null) return null;

      return myBekIntMap.getBekIndex(nodeId);
    }

    @NotNull
    @Override
    public List<GraphEdge> getAdjacentEdges(int nodeIndex, @NotNull EdgeFilter filter) {
      return map(myPermanentGraph.getAdjacentEdges(myBekIntMap.getUsualIndex(nodeIndex), filter), new Function<GraphEdge, GraphEdge>() {
        @Override
        public GraphEdge fun(GraphEdge edge) {
          return new GraphEdge(getNodeIndex(edge.getUpNodeIndex()), getNodeIndex(edge.getDownNodeIndex()), edge.getTargetId(), edge.getType());
        }
      });
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
