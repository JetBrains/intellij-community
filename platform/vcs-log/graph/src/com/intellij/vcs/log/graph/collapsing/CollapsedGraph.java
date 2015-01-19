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

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.*;
import com.intellij.vcs.log.graph.utils.impl.ListIntToIntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CollapsedGraph {

  // initVisibility == null means, what all nodes is Visible
  public static CollapsedGraph newInstance(@NotNull LinearGraph delegateGraph, @Nullable UnsignedBitSet initVisibility) {
    if (initVisibility == null) { // todo fix performance
      initVisibility = new UnsignedBitSet();
      for (int i = 0; i < delegateGraph.nodesCount(); i++) {
        initVisibility.set(delegateGraph.getGraphNode(i).getNodeId(), true);
      }
    }

    UnsignedBitSet visibleNodesId = initVisibility.clone();
    Flags delegateNodesVisibility = createDelegateNodesVisibility(delegateGraph, visibleNodesId);
    UpdatableIntToIntMap nodesMap = ListIntToIntMap.newInstance(delegateNodesVisibility);
    GraphAdditionalEdges graphAdditionalEdges = GraphAdditionalEdges
      .newInstance(createGetNodeIndexById(delegateGraph, visibleNodesId, nodesMap), createGetNodeIdByIndex(delegateGraph, nodesMap));
    return new CollapsedGraph(delegateGraph, visibleNodesId, delegateNodesVisibility, nodesMap, graphAdditionalEdges);
  }

  public static CollapsedGraph updateInstance(@NotNull CollapsedGraph prevCollapsedGraph, @NotNull LinearGraph delegateGraph) {
    UnsignedBitSet visibleNodesId = prevCollapsedGraph.myVisibleNodesId;
    Flags delegateNodesVisibility = createDelegateNodesVisibility(delegateGraph, visibleNodesId);
    UpdatableIntToIntMap nodesMap = ListIntToIntMap.newInstance(delegateNodesVisibility);
    GraphAdditionalEdges graphAdditionalEdges =
      GraphAdditionalEdges
        .updateInstance(prevCollapsedGraph.myGraphAdditionalEdges, createGetNodeIndexById(delegateGraph, visibleNodesId, nodesMap),
                        createGetNodeIdByIndex(delegateGraph, nodesMap));
    return new CollapsedGraph(delegateGraph, visibleNodesId, delegateNodesVisibility, nodesMap, graphAdditionalEdges);
  }

  @NotNull
  private static Flags createDelegateNodesVisibility(@NotNull final LinearGraph delegateGraph,
                                                     @NotNull final UnsignedBitSet visibleNodesId) {
    return new Flags() {
      @Override
      public int size() {
        return delegateGraph.nodesCount();
      }

      @Override
      public boolean get(int index) {
        GraphNode graphNode = delegateGraph.getGraphNode(index);
        return visibleNodesId.get(graphNode.getNodeId());
      }

      @Override
      public void set(int index, boolean value) {
        GraphNode graphNode = delegateGraph.getGraphNode(index);
        visibleNodesId.set(graphNode.getNodeId(), value);
      }

      @Override
      public void setAll(boolean value) {
        for (int i = 0; i < delegateGraph.nodesCount(); i++) {
          set(i, value);
        }
      }
    };
  }

  private static Function<Integer, Integer> createGetNodeIdByIndex(final LinearGraph delegateGraph, final IntToIntMap nodesMap) {
    return new Function<Integer, Integer>() {
      @Override
      public Integer fun(Integer nodeIndex) {
        int delegateIndex = nodesMap.getLongIndex(nodeIndex);
        return delegateGraph.getGraphNode(delegateIndex).getNodeId();
      }
    };
  }

  private static Function<Integer, Integer> createGetNodeIndexById(final LinearGraph delegateGraph,
                                                            final UnsignedBitSet visibleNodesId,
                                                            final IntToIntMap nodesMap) {
    return new Function<Integer, Integer>() {
      @Override
      public Integer fun(Integer nodeId) {
        assert visibleNodesId.get(nodeId);
        Integer delegateIndex = delegateGraph.getNodeIndexById(nodeId);
        assert delegateIndex != null;
        return nodesMap.getShortIndex(delegateIndex);
      }
    };
  }


  @NotNull
  private final LinearGraph myDelegateGraph;
  @NotNull
  private final UnsignedBitSet myVisibleNodesId;
  @NotNull
  private final Flags myDelegateNodesVisibility;
  @NotNull
  private final UpdatableIntToIntMap myNodesMap;
  @NotNull
  private final GraphAdditionalEdges myGraphAdditionalEdges;
  @NotNull
  private final CompiledGraph myCompiledGraph;


  private CollapsedGraph(@NotNull LinearGraph delegateGraph,
                        @NotNull UnsignedBitSet visibleNodesId,
                        @NotNull Flags delegateNodesVisibility,
                        @NotNull UpdatableIntToIntMap nodesMap,
                        @NotNull GraphAdditionalEdges graphAdditionalEdges) {
    myDelegateGraph = delegateGraph;
    myVisibleNodesId = visibleNodesId;
    myDelegateNodesVisibility = delegateNodesVisibility;
    myNodesMap = nodesMap;
    myGraphAdditionalEdges = graphAdditionalEdges;
    myCompiledGraph = new CompiledGraph();
  }

  @NotNull
  public LinearGraph getCompiledGraph() {
    return myCompiledGraph;
  }

  public void setNodeVisibility(int nodeId, boolean visible) {
    myVisibleNodesId.set(nodeId, visible);
  }

  public boolean getNodeVisibility(int nodeId) {
    return myVisibleNodesId.get(nodeId);
  }

  public void updateNodeMapping(int fromDelegateNodeIndex, int toDelegateNodeIndex) {
    myNodesMap.update(fromDelegateNodeIndex, toDelegateNodeIndex);
  }

  @NotNull
  public GraphAdditionalEdges getGraphAdditionalEdges() {
    return myGraphAdditionalEdges;
  }

  @NotNull
  public LinearGraph getDelegateGraph() {
    return myDelegateGraph;
  }

  private class CompiledGraph implements LinearGraph {

    @Override
    public int nodesCount() {
      return myNodesMap.shortSize();
    }

    @NotNull
    private GraphEdge createEdge(@NotNull GraphEdge delegateEdge, @Nullable Integer upNodeIndex, @Nullable Integer downNodeIndex) {
      return new GraphEdge(upNodeIndex, downNodeIndex, delegateEdge.getAdditionInfo(), delegateEdge.getType());
    }

    @Nullable
    private Integer compiledNodeIndex(@Nullable Integer delegateNodeIndex) {
      if (delegateNodeIndex == null)
        return null;
      if (myDelegateNodesVisibility.get(delegateNodeIndex)) {
        return myNodesMap.getShortIndex(delegateNodeIndex);
      } else
        return -1;
    }

    private boolean isVisibleEdge(@Nullable Integer compiledUpNode, @Nullable Integer compiledDownNode) {
      if (compiledUpNode != null && compiledUpNode == -1)
        return false;
      if (compiledDownNode != null && compiledDownNode == -1)
        return false;
      return true;
    }

    @NotNull
    @Override
    public List<GraphEdge> getAdjacentEdges(int nodeIndex) {
      List<GraphEdge> result = ContainerUtil.newSmartList();
      int delegateIndex = myNodesMap.getLongIndex(nodeIndex);

      // add delegate edges
      for (GraphEdge delegateEdge : myDelegateGraph.getAdjacentEdges(delegateIndex)) {
        Integer compiledUpIndex = compiledNodeIndex(delegateEdge.getUpNodeIndex());
        Integer compiledDownIndex = compiledNodeIndex(delegateEdge.getDownNodeIndex());
        if (isVisibleEdge(compiledUpIndex, compiledDownIndex))
          result.add(createEdge(delegateEdge, compiledUpIndex, compiledDownIndex));
      }

      myGraphAdditionalEdges.appendAdditionalEdges(result, nodeIndex);

      return result;
    }

    @NotNull
    @Override
    public GraphNode getGraphNode(int nodeIndex) {
      int delegateIndex = myNodesMap.getLongIndex(nodeIndex);
      GraphNode graphNode = myDelegateGraph.getGraphNode(delegateIndex);
      return new GraphNode(graphNode.getNodeId(), nodeIndex, graphNode.getType());
    }

    @Override
    @Nullable
    public Integer getNodeIndexById(int nodeId) {
      Integer delegateIndex = myDelegateGraph.getNodeIndexById(nodeId);
      if (delegateIndex == null)
        return null;
      if (myDelegateNodesVisibility.get(delegateIndex))
        return myNodesMap.getShortIndex(delegateIndex);
      else
        return null;
    }
  }
}
