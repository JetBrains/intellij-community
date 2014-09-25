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
package com.intellij.vcs.log.graph.impl.facade.bek;

import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.GraphColorManager;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DelegatedPermanentGraphInfo<CommitId> implements PermanentGraphInfo<CommitId> {

  @NotNull
  private final PermanentGraphInfo<CommitId> myDelegateInfo;
  @NotNull
  private final BekIntMap myBekIntMap;

  public DelegatedPermanentGraphInfo(@NotNull PermanentGraphInfo<CommitId> delegateInfo, @NotNull BekIntMap bekIntMap) {
    myDelegateInfo = delegateInfo;
    myBekIntMap = bekIntMap;
  }

  @NotNull
  @Override
  public PermanentCommitsInfo<CommitId> getPermanentCommitsInfo() {
    final PermanentCommitsInfo<CommitId> commitsInfo = myDelegateInfo.getPermanentCommitsInfo();
    return new PermanentCommitsInfo<CommitId>() {
      @NotNull
      @Override
      public CommitId getCommitId(int nodeId) {
        if (nodeId < 0)
          return commitsInfo.getCommitId(nodeId);
        return commitsInfo.getCommitId(myBekIntMap.getUsualIndex(nodeId));
      }

      @Override
      public long getTimestamp(int nodeId) {
        if (nodeId < 0)
          return commitsInfo.getTimestamp(nodeId);
        return commitsInfo.getTimestamp(myBekIntMap.getUsualIndex(nodeId));
      }

      @Override
      public int getNodeId(@NotNull CommitId commitId) {
        int nodeId = commitsInfo.getNodeId(commitId);
        if (nodeId < 0)
          return nodeId;
        return myBekIntMap.getBekIndex(nodeId);
      }

      @NotNull
      @Override
      public Set<Integer> convertToNodeIds(@NotNull Collection<CommitId> heads) {
        Set<Integer> nodeIds = commitsInfo.convertToNodeIds(heads);
        return ContainerUtil.map2Set(nodeIds, new Function<Integer, Integer>() {
          @Override
          public Integer fun(Integer nodeId) {
            if (nodeId < 0)
              return nodeId;
            return myBekIntMap.getBekIndex(nodeId);
          }
        });
      }
    };
  }

  @NotNull
  @Override
  public LinearGraph getPermanentLinearGraph() {
    return new DelegateLinearGraph(myDelegateInfo.getPermanentLinearGraph());
  }

  private class DelegateLinearGraph implements LinearGraph {
    @NotNull
    private final LinearGraph myDelegateGraph;

    private DelegateLinearGraph(@NotNull LinearGraph delegateGraph) {
      this.myDelegateGraph = delegateGraph;
    }

    @Override
    public int nodesCount() {
      return myDelegateGraph.nodesCount();
    }

    @NotNull
    @Override
    public List<Integer> getUpNodes(int nodeIndex) {
      return LinearGraphUtils.getUpNodes(this, nodeIndex);
    }

    @NotNull
    @Override
    public List<Integer> getDownNodes(int nodeIndex) {
      return LinearGraphUtils.getDownNodes(this, nodeIndex);
    }

    @Nullable
    private Integer convertToBek(@Nullable Integer usualIndex) {
      if (usualIndex == null)
        return null;
      return myBekIntMap.getBekIndex(usualIndex);
    }

    @NotNull
    @Override
    public List<GraphEdge> getAdjacentEdges(int nodeIndex) {
      return ContainerUtil.map(myDelegateGraph.getAdjacentEdges(myBekIntMap.getUsualIndex(nodeIndex)), new Function<GraphEdge, GraphEdge>() {
        @Override
        public GraphEdge fun(GraphEdge edge) {
          return new GraphEdge(convertToBek(edge.getUpNodeIndex()), convertToBek(edge.getDownNodeIndex()), edge.getAdditionInfo(), edge.getType());
        }
      });
    }

    @NotNull
    @Override
    public GraphNode getGraphNode(int nodeIndex) {
      GraphNode delegateNode = myDelegateGraph.getGraphNode(myBekIntMap.getUsualIndex(nodeIndex));
      return new GraphNode(delegateNode.getNodeId(), nodeIndex, delegateNode.getType());
    }

    @Nullable
    @Override
    public Integer getNodeIndexById(int nodeId) {
      Integer usualIndex = myDelegateGraph.getNodeIndexById(nodeId);
      if (usualIndex == null)
        return null;

      return myBekIntMap.getBekIndex(usualIndex);
    }
  }

  @NotNull
  @Override
  public GraphLayout getPermanentGraphLayout() {
    final GraphLayout graphLayout = myDelegateInfo.getPermanentGraphLayout();
    return new GraphLayout() {
      @Override
      public int getLayoutIndex(int nodeIndex) {
        return graphLayout.getLayoutIndex(myBekIntMap.getUsualIndex(nodeIndex));
      }

      @Override
      public int getOneOfHeadNodeIndex(int nodeIndex) {
        int usualIndex = graphLayout.getOneOfHeadNodeIndex(myBekIntMap.getUsualIndex(nodeIndex));
        return myBekIntMap.getBekIndex(usualIndex);
      }
    };
  }

  @NotNull
  @Override
  public Condition<Integer> getNotCollapsedNodes() {
    final Condition<Integer> notCollapsedNodes = myDelegateInfo.getNotCollapsedNodes();
    return new Condition<Integer>() {
      @Override
      public boolean value(Integer bekNodeId) {
        if (bekNodeId < 0)
          return notCollapsedNodes.value(bekNodeId);
        return notCollapsedNodes.value(myBekIntMap.getUsualIndex(bekNodeId));
      }
    };
  }

  @NotNull
  @Override
  public GraphColorManager<CommitId> getGraphColorManager() {
    return myDelegateInfo.getGraphColorManager();
  }

}
