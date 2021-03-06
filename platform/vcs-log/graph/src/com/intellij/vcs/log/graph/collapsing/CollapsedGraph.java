// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.collapsing;

import com.intellij.util.SmartList;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.UnsignedBitSet;
import com.intellij.vcs.log.graph.utils.UpdatableIntToIntMap;
import com.intellij.vcs.log.graph.utils.impl.ListIntToIntMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class CollapsedGraph {
  public static CollapsedGraph newInstance(@NotNull LinearGraph delegateGraph, @NotNull UnsignedBitSet matchedNodeId) {
    return new CollapsedGraph(delegateGraph, matchedNodeId, matchedNodeId.clone(), new EdgeStorage());
  }

  public static CollapsedGraph updateInstance(@NotNull CollapsedGraph prevCollapsedGraph, @NotNull LinearGraph newDelegateGraph) {
    UnsignedBitSet visibleNodesId = prevCollapsedGraph.myDelegateNodesVisibility.getNodeVisibilityById();
    return new CollapsedGraph(newDelegateGraph, prevCollapsedGraph.myMatchedNodeId, visibleNodesId, prevCollapsedGraph.myEdgeStorage);
  }

  @NotNull private final LinearGraph myDelegatedGraph;
  @NotNull private final UnsignedBitSet myMatchedNodeId;
  @NotNull private final GraphNodesVisibility myDelegateNodesVisibility;
  @NotNull private final UpdatableIntToIntMap myNodesMap;
  @NotNull private final EdgeStorage myEdgeStorage;
  @NotNull private final CompiledGraph myCompiledGraph;
  @NotNull private final AtomicReference<Modification> myCurrentModification = new AtomicReference<>(null);


  private CollapsedGraph(@NotNull LinearGraph delegatedGraph,
                         @NotNull UnsignedBitSet matchedNodeId,
                         @NotNull UnsignedBitSet visibleNodesId,
                         @NotNull EdgeStorage edgeStorage) {
    myDelegatedGraph = delegatedGraph;
    myMatchedNodeId = matchedNodeId;
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
    Modification modification = new Modification();
    if (myCurrentModification.compareAndSet(null, modification)) {
      return modification;
    }
    throw new RuntimeException("Can not start a new modification while the other one is still running.");
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

  @NotNull
  public UnsignedBitSet getMatchedNodeId() {
    return myMatchedNodeId;
  }

  // todo proper name
  public boolean isMyCollapsedEdge(int upNodeIndex, int downNodeIndex) {
    return new EdgeStorageWrapper(myEdgeStorage, myDelegatedGraph).hasEdge(upNodeIndex, downNodeIndex);
  }

  // everywhere in this class "nodeIndexes" means "node indexes in delegated graph"
  public class Modification {
    private static final int COLLECTING = 0;
    private static final int APPLYING = 1;
    private static final int DONE = 2;

    @NotNull private final EdgeStorageWrapper myEdgesToAdd = EdgeStorageWrapper.createSimpleEdgeStorage();
    @NotNull private final EdgeStorageWrapper myEdgesToRemove = EdgeStorageWrapper.createSimpleEdgeStorage();
    @NotNull private final IntSet myNodesToHide = new IntOpenHashSet();
    @NotNull private final IntSet myNodesToShow = new IntOpenHashSet();
    private boolean myClearEdges = false;
    private boolean myClearVisibility = false;

    private volatile int myProgress = COLLECTING;
    private int minAffectedNodeIndex = Integer.MAX_VALUE;
    private int maxAffectedNodeIndex = Integer.MIN_VALUE;

    private void touchIndex(int nodeIndex) {
      assert myProgress == COLLECTING;
      minAffectedNodeIndex = Math.min(minAffectedNodeIndex, nodeIndex);
      maxAffectedNodeIndex = Math.max(maxAffectedNodeIndex, nodeIndex);
    }

    private void touchAll() {
      assert myProgress == COLLECTING;
      minAffectedNodeIndex = 0;
      maxAffectedNodeIndex = getDelegatedGraph().nodesCount() - 1;
    }

    private void touchEdge(@NotNull GraphEdge edge) {
      assert myProgress == COLLECTING;
      if (edge.getUpNodeIndex() != null) touchIndex(edge.getUpNodeIndex());
      if (edge.getDownNodeIndex() != null) touchIndex(edge.getDownNodeIndex());
    }

    public void showNode(int nodeIndex) {
      assert myProgress == COLLECTING;
      myNodesToShow.add(nodeIndex);
      touchIndex(nodeIndex);
    }

    public void hideNode(int nodeIndex) {
      assert myProgress == COLLECTING;
      myNodesToHide.add(nodeIndex);
      touchIndex(nodeIndex);
    }

    public void createEdge(@NotNull GraphEdge edge) {
      assert myProgress == COLLECTING;
      myEdgesToAdd.createEdge(edge);
      touchEdge(edge);
    }

    public void removeEdge(@NotNull GraphEdge edge) { // todo add support for removing edge from delegate graph
      assert myProgress == COLLECTING;
      myEdgesToRemove.createEdge(edge);
      touchEdge(edge);
    }

    public void removeAdditionalEdges() {
      assert myProgress == COLLECTING;
      myClearEdges = true;
      touchAll();
    }

    public void resetNodesVisibility() {
      assert myProgress == COLLECTING;
      myClearVisibility = true;
      touchAll();
    }

    // "package private" means "I'm not entirely happy about this method"
    @NotNull
    /*package private*/ EdgeStorageWrapper getEdgesToAdd() {
      assert myProgress == COLLECTING;
      return myEdgesToAdd;
    }

    /*package private*/
    boolean isNodeHidden(int nodeIndex) {
      assert myProgress == COLLECTING;
      return myNodesToHide.contains(nodeIndex);
    }

    /*package private*/
    boolean isNodeShown(int nodeIndex) {
      assert myProgress == COLLECTING;
      return myNodesToShow.contains(nodeIndex);
    }

    public int convertToDelegateNodeIndex(int nodeIndex) {
      return CollapsedGraph.this.convertToDelegateNodeIndex(nodeIndex);
    }

    public void apply() {
      assert myCurrentModification.get() == this;
      myProgress = APPLYING;

      if (myClearVisibility) {
        myDelegateNodesVisibility.setNodeVisibilityById(myMatchedNodeId.clone());
      }
      if (myClearEdges) {
        myEdgeStorage.removeAll();
      }

      IntIterator toShow = myNodesToShow.iterator();
      while (toShow.hasNext()) {
        myDelegateNodesVisibility.show(toShow.nextInt());
      }
      IntIterator toHide = myNodesToHide.iterator();
      while (toHide.hasNext()) {
        myDelegateNodesVisibility.hide(toHide.nextInt());
      }

      EdgeStorageWrapper edgeStorageWrapper = new EdgeStorageWrapper(myEdgeStorage, getDelegatedGraph());
      for (GraphEdge edge : myEdgesToAdd.getEdges()) {
        edgeStorageWrapper.createEdge(edge);
      }
      for (GraphEdge edge : myEdgesToRemove.getEdges()) {
        edgeStorageWrapper.removeEdge(edge);
      }

      if (minAffectedNodeIndex != Integer.MAX_VALUE && maxAffectedNodeIndex != Integer.MIN_VALUE) {
        myNodesMap.update(minAffectedNodeIndex, maxAffectedNodeIndex);
      }

      myProgress = DONE;
      myCurrentModification.set(null);
    }
  }

  private void assertNotUnderModification() {
    Modification modification = myCurrentModification.get();
    if (modification != null && modification.myProgress == Modification.APPLYING) {
      throw new IllegalStateException("CompiledGraph is under modification");
    }
  }

  private final class CompiledGraph implements LinearGraph {
    @NotNull private final EdgeStorageWrapper myEdgeStorageWrapper;

    private CompiledGraph() {
      myEdgeStorageWrapper = new EdgeStorageWrapper(myEdgeStorage, this);
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
      if (delegateNodeIndex == null) return null;
      if (myDelegateNodesVisibility.isVisible(delegateNodeIndex)) {
        return myNodesMap.getShortIndex(delegateNodeIndex);
      }
      else {
        return -1;
      }
    }

    private boolean isVisibleEdge(@Nullable Integer compiledUpNode, @Nullable Integer compiledDownNode) {
      if (compiledUpNode != null && compiledUpNode == -1) return false;
      if (compiledDownNode != null && compiledDownNode == -1) return false;
      return true;
    }

    @NotNull
    @Override
    public List<GraphEdge> getAdjacentEdges(int nodeIndex, @NotNull EdgeFilter filter) {
      assertNotUnderModification();
      List<GraphEdge> result = new SmartList<>();
      int delegateIndex = myNodesMap.getLongIndex(nodeIndex);

      // add delegate edges
      for (GraphEdge delegateEdge : myDelegatedGraph.getAdjacentEdges(delegateIndex, filter)) {
        Integer compiledUpIndex = compiledNodeIndex(delegateEdge.getUpNodeIndex());
        Integer compiledDownIndex = compiledNodeIndex(delegateEdge.getDownNodeIndex());
        if (isVisibleEdge(compiledUpIndex, compiledDownIndex)) result.add(createEdge(delegateEdge, compiledUpIndex, compiledDownIndex));
      }

      result.addAll(myEdgeStorageWrapper.getAdjacentEdges(nodeIndex, filter));

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
      if (delegateIndex == null) return null;
      if (myDelegateNodesVisibility.isVisible(delegateIndex)) {
        return myNodesMap.getShortIndex(delegateIndex);
      }
      else {
        return null;
      }
    }
  }
}
