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
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.IntToIntMap;
import com.intellij.vcs.log.graph.utils.UnsignedBitSet;
import com.intellij.vcs.log.graph.utils.impl.ListIntToIntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CollapsedGraph {
  @NotNull
  private final LinearGraph myDelegateGraph;
  @NotNull
  private final UnsignedBitSet myVisibleNodesId = new UnsignedBitSet();
  @NotNull
  private final GraphAdditionEdges myGraphAdditionEdges;
  @NotNull
  private final IntToIntMap myNodesMap;
  @NotNull
  private final Flags myDelegateNodesVisibility;
  @NotNull
  private final CompiledGraph myCompiledGraph;


  public CollapsedGraph(@NotNull final LinearGraph delegateGraph) {
    myDelegateGraph = delegateGraph;
    myDelegateNodesVisibility = createDelegateNodesVisibility();
    myNodesMap = ListIntToIntMap.newInstance(myDelegateNodesVisibility);
    myGraphAdditionEdges = createGraphAdditionEdges();
    myCompiledGraph = new CompiledGraph();
  }

  @NotNull
  private GraphAdditionEdges createGraphAdditionEdges() {
    return new GraphAdditionEdges(new Function<Integer, Integer>() {
      @Override
      public Integer fun(Integer nodeId) {
        assert myVisibleNodesId.get(nodeId);
        int delegateIndex = myDelegateGraph.getNodeIndexById(nodeId);
        return myNodesMap.getShortIndex(delegateIndex);
      }
    }, new Function<Integer, Integer>() {
      @Override
      public Integer fun(Integer nodeIndex) {
        int delegateIndex = myNodesMap.getLongIndex(nodeIndex);
        return myDelegateGraph.getGraphNode(delegateIndex).getNodeId();
      }
    });
  }

  private Flags createDelegateNodesVisibility() {
    return new Flags() {
      @Override
      public int size() {
        return myDelegateGraph.nodesCount();
      }

      @Override
      public boolean get(int index) {
        GraphNode graphNode = myDelegateGraph.getGraphNode(index);
        return myVisibleNodesId.get(graphNode.getNodeId());
      }

      @Override
      public void set(int index, boolean value) {
        GraphNode graphNode = myDelegateGraph.getGraphNode(index);
        myVisibleNodesId.set(graphNode.getNodeId(), value);
      }

      @Override
      public void setAll(boolean value) {
        for (int i = 0; i < myDelegateGraph.nodesCount(); i++) {
          set(i, value);
        }
      }
    };
  }

  @NotNull
  public LinearGraph getCompiledGraph() {
    return myCompiledGraph;
  }

  @NotNull
  public UnsignedBitSet getVisibleNodesId() {
    return myVisibleNodesId;
  }

  private class CompiledGraph implements LinearGraph {

    @Override
    public int nodesCount() {
      return myNodesMap.shortSize();
    }

    @NotNull
    @Override
    public List<Integer> getUpNodes(int nodeIndex) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public List<Integer> getDownNodes(int nodeIndex) {
      throw new UnsupportedOperationException();
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

      myGraphAdditionEdges.addToResultAdditionEdges(result, nodeIndex);

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
    public int getNodeIndexById(int nodeId) {
      int delegateIndex = myDelegateGraph.getNodeIndexById(nodeId);
      if (myDelegateNodesVisibility.get(delegateIndex))
        return myNodesMap.getShortIndex(delegateIndex);
      else
        return -1;
    }
  }
}
