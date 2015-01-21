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
import com.intellij.vcs.log.graph.api.EdgeFilter;
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
        initVisibility.set(delegateGraph.getNodeId(i), true);
      }
    }

    UnsignedBitSet visibleNodesId = initVisibility.clone(); // todo mm?
    GraphNodesVisibility delegateNodesVisibility = new GraphNodesVisibility(delegateGraph, visibleNodesId);
    UpdatableIntToIntMap nodesMap = ListIntToIntMap.newInstance(delegateNodesVisibility.asFlags());
    GraphAdditionalEdges graphAdditionalEdges = GraphAdditionalEdges
      .newInstance(createGetNodeIndexById(delegateGraph, visibleNodesId, nodesMap), createGetNodeIdByIndex(delegateGraph, nodesMap));
    return new CollapsedGraph(delegateGraph, delegateNodesVisibility, nodesMap, graphAdditionalEdges);
  }

  public static CollapsedGraph updateInstance(@NotNull CollapsedGraph prevCollapsedGraph, @NotNull LinearGraph newDelegateGraph) {
    UnsignedBitSet visibleNodesId = prevCollapsedGraph.myDelegateNodesVisibility.getNodeVisibilityById();
    GraphNodesVisibility delegateNodesVisibility = new GraphNodesVisibility(newDelegateGraph, visibleNodesId);
    UpdatableIntToIntMap nodesMap = ListIntToIntMap.newInstance(delegateNodesVisibility.asFlags());
    GraphAdditionalEdges graphAdditionalEdges =
      GraphAdditionalEdges
        .updateInstance(prevCollapsedGraph.myGraphAdditionalEdges, createGetNodeIndexById(newDelegateGraph, visibleNodesId, nodesMap),
                        createGetNodeIdByIndex(newDelegateGraph, nodesMap));
    return new CollapsedGraph(newDelegateGraph, delegateNodesVisibility, nodesMap, graphAdditionalEdges);
  }

  private static Function<Integer, Integer> createGetNodeIdByIndex(final LinearGraph delegateGraph, final IntToIntMap nodesMap) {
    return new Function<Integer, Integer>() {
      @Override
      public Integer fun(Integer nodeIndex) {
        int delegateIndex = nodesMap.getLongIndex(nodeIndex);
        return delegateGraph.getNodeId(delegateIndex);
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
        Integer delegateIndex = delegateGraph.getNodeIndex(nodeId);
        assert delegateIndex != null;
        return nodesMap.getShortIndex(delegateIndex);
      }
    };
  }

  @NotNull
  private final LinearGraph myDelegatedGraph;
  @NotNull
  private final GraphNodesVisibility myDelegateNodesVisibility;
  @NotNull
  private final UpdatableIntToIntMap myNodesMap;
  @NotNull
  private final GraphAdditionalEdges myGraphAdditionalEdges;
  @NotNull
  private final CompiledGraph myCompiledGraph;
  @Nullable private Modification myCurrentModification = null;


  private CollapsedGraph(@NotNull LinearGraph delegatedGraph,
                         @NotNull GraphNodesVisibility delegateNodesVisibility,
                         @NotNull UpdatableIntToIntMap nodesMap,
                         @NotNull GraphAdditionalEdges graphAdditionalEdges) {
    myDelegatedGraph = delegatedGraph;
    myDelegateNodesVisibility = delegateNodesVisibility;
    myNodesMap = nodesMap;
    myGraphAdditionalEdges = graphAdditionalEdges;
    myCompiledGraph = new CompiledGraph();
  }

  @NotNull
  public LinearGraph getDelegatedGraph() {
    return myDelegatedGraph;
  }

  public boolean isNodeVisible(int delegateNodeIndex) {
    return myDelegateNodesVisibility.isVisible(delegateNodeIndex);
  }

  @NotNull
  public Modification startModification() {
    assert myCurrentModification == null;
    myCurrentModification = new Modification();
    return myCurrentModification;
  }

  @NotNull
  public LinearGraph getCompiledGraph() {
    assertNotUnderModification();
    return myCompiledGraph;
  }

  @Deprecated
  public void setNodeVisibility(int nodeId, boolean visible) {
    myDelegateNodesVisibility.getNodeVisibilityById().set(nodeId, visible);
  }

  @Deprecated
  public void updateNodeMapping(int fromDelegateNodeIndex, int toDelegateNodeIndex) {
    myNodesMap.update(fromDelegateNodeIndex, toDelegateNodeIndex);
  }

  @Deprecated
  @NotNull
  public GraphAdditionalEdges getGraphAdditionalEdges() {
    return myGraphAdditionalEdges;
  }

  // all nodeIndexes means node indexes in delegated graph
  public class Modification {
    private boolean done = false;
    private int minAffectedNodeIndex = Integer.MAX_VALUE;
    private int maxAffectedNodeIndex = Integer.MIN_VALUE;

    private void touchIndex(int nodeIndex) {
      assert !done;
      minAffectedNodeIndex = Math.min(minAffectedNodeIndex, nodeIndex);
      maxAffectedNodeIndex = Math.max(maxAffectedNodeIndex, nodeIndex);
    }

    private void touchEdge(@NotNull GraphEdge edge) {
      assert !done;
      if (edge.getUpNodeIndex() != null) touchIndex(edge.getUpNodeIndex());
      if (edge.getDownNodeIndex() != null) touchIndex(edge.getDownNodeIndex());
    }

    public void showNode(int nodeIndex) {
      touchIndex(nodeIndex);
      myDelegateNodesVisibility.show(nodeIndex);
    }

    public void hideNode(int nodeIndex) {
      touchIndex(nodeIndex);
      myDelegateNodesVisibility.hide(nodeIndex);
    }

    public void createEdge(@NotNull GraphEdge edge) {
      touchEdge(edge);
      myGraphAdditionalEdges.createEdge(edge);
    }

    public void removeEdge(@NotNull GraphEdge edge) { // todo add support for removing edge from delegate graph
      touchEdge(edge);
      myGraphAdditionalEdges.removeEdge(edge);
    }

    public void apply() {
      assert myCurrentModification == this;
      done = true;
      myCurrentModification = null;

      if (minAffectedNodeIndex == Integer.MAX_VALUE || maxAffectedNodeIndex == Integer.MIN_VALUE) return;

      myNodesMap.update(minAffectedNodeIndex, maxAffectedNodeIndex);
    }
  }

  private void assertNotUnderModification() {
    if (myCurrentModification != null) throw new IllegalStateException("CompiledGraph is under modification");
  }

  private class CompiledGraph implements LinearGraph {

    @Override
    public int nodesCount() {
      assertNotUnderModification();
      return myNodesMap.shortSize();
    }

    @NotNull
    private GraphEdge createEdge(@NotNull GraphEdge delegateEdge, @Nullable Integer upNodeIndex, @Nullable Integer downNodeIndex) {
      return new GraphEdge(upNodeIndex, downNodeIndex, delegateEdge.getTargetId(), delegateEdge.getType());
    }

    @Nullable
    private Integer compiledNodeIndex(@Nullable Integer delegateNodeIndex) {
      if (delegateNodeIndex == null)
        return null;
      if (myDelegateNodesVisibility.isVisible(delegateNodeIndex)) {
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
    public List<GraphEdge> getAdjacentEdges(int nodeIndex, @NotNull EdgeFilter filter) {
      assertNotUnderModification();
      List<GraphEdge> result = ContainerUtil.newSmartList();
      int delegateIndex = myNodesMap.getLongIndex(nodeIndex);

      // add delegate edges
      for (GraphEdge delegateEdge : myDelegatedGraph.getAdjacentEdges(delegateIndex, filter)) {
        Integer compiledUpIndex = compiledNodeIndex(delegateEdge.getUpNodeIndex());
        Integer compiledDownIndex = compiledNodeIndex(delegateEdge.getDownNodeIndex());
        if (isVisibleEdge(compiledUpIndex, compiledDownIndex))
          result.add(createEdge(delegateEdge, compiledUpIndex, compiledDownIndex));
      }

      myGraphAdditionalEdges.appendAdditionalEdges(result, nodeIndex, filter);

      return result;
    }

    @NotNull
    @Override
    public GraphNode getGraphNode(int nodeIndex) {
      assertNotUnderModification();
      int delegateIndex = myNodesMap.getLongIndex(nodeIndex);
      GraphNode graphNode = myDelegatedGraph.getGraphNode(delegateIndex);
      return new GraphNode(nodeIndex, graphNode.getType());
    }

    @Override
    public int getNodeId(int nodeIndex) {
      assertNotUnderModification();
      int delegateIndex = myNodesMap.getLongIndex(nodeIndex);
      return myDelegatedGraph.getNodeId(delegateIndex);
    }

    @Override
    @Nullable
    public Integer getNodeIndex(int nodeId) {
      assertNotUnderModification();
      Integer delegateIndex = myDelegatedGraph.getNodeIndex(nodeId);
      if (delegateIndex == null)
        return null;
      if (myDelegateNodesVisibility.isVisible(delegateIndex))
        return myNodesMap.getShortIndex(delegateIndex);
      else
        return null;
    }
  }
}
