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

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.UnsignedBitSet;
import com.intellij.vcs.log.graph.utils.UpdatableIntToIntMap;
import com.intellij.vcs.log.graph.utils.impl.ListIntToIntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CollapsedGraph {

  // initVisibility == null means, what all nodes is Visible
  public static CollapsedGraph newInstance(@NotNull LinearGraph delegateGraph, @NotNull UnsignedBitSet initVisibility) {
    UnsignedBitSet visibleNodesId = initVisibility.clone(); // todo mm?
    return new CollapsedGraph(delegateGraph, visibleNodesId, new EdgeStorage());
  }

  public static CollapsedGraph updateInstance(@NotNull CollapsedGraph prevCollapsedGraph, @NotNull LinearGraph newDelegateGraph) {
    UnsignedBitSet visibleNodesId = prevCollapsedGraph.myDelegateNodesVisibility.getNodeVisibilityById();
    return new CollapsedGraph(newDelegateGraph, visibleNodesId, prevCollapsedGraph.myEdgeStorage);
  }

  @NotNull
  private final LinearGraph myDelegatedGraph;
  @NotNull
  private final GraphNodesVisibility myDelegateNodesVisibility;
  @NotNull
  private final UpdatableIntToIntMap myNodesMap;
  @NotNull
  private final EdgeStorage myEdgeStorage;
  @NotNull
  private final CompiledGraph myCompiledGraph;
  @Nullable private Modification myCurrentModification = null;


  private CollapsedGraph(@NotNull LinearGraph delegatedGraph, @NotNull UnsignedBitSet visibleNodesId, @NotNull EdgeStorage edgeStorage) {
    myDelegatedGraph = delegatedGraph;
    myDelegateNodesVisibility = new GraphNodesVisibility(delegatedGraph, visibleNodesId);
    myNodesMap = ListIntToIntMap.newInstance(myDelegateNodesVisibility.asFlags());
    myEdgeStorage = edgeStorage;
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

  public int convertToDelegateNodeIndex(int compiledNodeIndex) {
    assertNotUnderModification();
    return myNodesMap.getLongIndex(compiledNodeIndex);
  }

  // all nodeIndexes means node indexes in delegated graph
  public class Modification {
    @NotNull
    private final EdgeStorageAdapter myEdgeStorageAdapter;

    private boolean done = false;
    private int minAffectedNodeIndex = Integer.MAX_VALUE;
    private int maxAffectedNodeIndex = Integer.MIN_VALUE;

    public Modification() {
      myEdgeStorageAdapter = new EdgeStorageAdapter(myEdgeStorage, getDelegatedGraph());
    }

    @NotNull
    public EdgeStorageAdapter getEdgeStorageAdapter() {
      return myEdgeStorageAdapter;
    }

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
      myEdgeStorageAdapter.createEdge(edge);
    }

    public void removeEdge(@NotNull GraphEdge edge) { // todo add support for removing edge from delegate graph
      touchEdge(edge);
      myEdgeStorageAdapter.removeEdge(edge);
    }

    public void apply() {
      assert myCurrentModification == this;
      done = true;
      myCurrentModification = null;

      if (minAffectedNodeIndex == Integer.MAX_VALUE || maxAffectedNodeIndex == Integer.MIN_VALUE) return;

      myNodesMap.update(minAffectedNodeIndex, maxAffectedNodeIndex);
    }

    public void removeAdditionalEdges() {
      minAffectedNodeIndex = 0;
      maxAffectedNodeIndex = getDelegatedGraph().nodesCount() - 1;
      myEdgeStorage.removeAll();
    }
  }

  private void assertNotUnderModification() {
    if (myCurrentModification != null) throw new IllegalStateException("CompiledGraph is under modification");
  }

  private class CompiledGraph implements LinearGraph {
    @NotNull
    private final EdgeStorageAdapter myEdgeStorageAdapter;

    private CompiledGraph() {
      myEdgeStorageAdapter = new EdgeStorageAdapter(myEdgeStorage, this);
    }

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

      result.addAll(myEdgeStorageAdapter.getAdditionalEdges(nodeIndex, filter));

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
