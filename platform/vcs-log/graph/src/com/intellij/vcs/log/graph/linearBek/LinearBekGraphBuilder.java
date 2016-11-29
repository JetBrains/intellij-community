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
package com.intellij.vcs.log.graph.linearBek;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.impl.print.PrintElementGeneratorImpl;
import com.intellij.vcs.log.graph.utils.IntIntMultiMap;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

class LinearBekGraphBuilder {
  private static final int MAX_BLOCK_SIZE = 200;
  private static final int MAGIC_SET_SIZE = PrintElementGeneratorImpl.LONG_EDGE_SIZE;
  private static final GraphEdgeToDownNode GRAPH_EDGE_TO_DOWN_NODE = new GraphEdgeToDownNode();
  @NotNull private final GraphLayout myGraphLayout;
  private final LinearBekGraph myLinearBekGraph;

  public LinearBekGraphBuilder(@NotNull LinearBekGraph bekGraph, @NotNull GraphLayout graphLayout) {
    myLinearBekGraph = bekGraph;
    myGraphLayout = graphLayout;
  }

  public void collapseAll() {
    for (int i = myLinearBekGraph.myGraph.nodesCount() - 1; i >= 0; i--) {
      MergeFragment fragment = getFragment(i);
      if (fragment != null) {
        fragment.collapse(myLinearBekGraph);
      }
    }
  }

  @Nullable
  public MergeFragment collapseFragment(int mergeCommit) {
    MergeFragment fragment = getFragment(mergeCommit);
    if (fragment != null) {
      fragment.collapse(myLinearBekGraph);
      return fragment;
    }
    return null;
  }

  @Nullable
  public MergeFragment getFragment(int mergeCommit) {
    List<Integer> downNodes = ContainerUtil.sorted(LinearGraphUtils.getDownNodes(myLinearBekGraph, mergeCommit));
    if (downNodes.size() != 2) return null;

    return getFragment(downNodes.get(1), downNodes.get(0), mergeCommit);
  }

  @Nullable
  private MergeFragment getFragment(int leftChild, int rightChild, int parent) {
    MergeFragment fragment = new MergeFragment(parent, leftChild, rightChild);

    int leftLi = myGraphLayout.getLayoutIndex(leftChild);
    int rightLi = myGraphLayout.getLayoutIndex(rightChild);
    int rowsCount = 1;
    int blockSize = 1;

    PriorityQueue<GraphEdge> queue = new PriorityQueue<>(MAX_BLOCK_SIZE, new GraphEdgeComparator());
    queue.addAll(myLinearBekGraph.getAdjacentEdges(rightChild, EdgeFilter.NORMAL_DOWN));

    @Nullable Set<Integer> magicSet = null;

    while (!queue.isEmpty()) {
      GraphEdge nextEdge = queue.poll();
      Integer next = nextEdge.getDownNodeIndex();
      Integer upNodeIndex = nextEdge.getUpNodeIndex();
      assert upNodeIndex != null; // can not happen
      if (next == null) {
        fragment.addTail(upNodeIndex);
        continue; // allow very long edges down
      }

      if (next == leftChild) {
        // found first child
        fragment.addTail(upNodeIndex);
        fragment.setMergeWithOldCommit(true);
      }
      else if (next == rightChild + rowsCount) {
        // all is fine, continuing
        rowsCount++;
        blockSize++;
        queue.addAll(myLinearBekGraph.getAdjacentEdges(next, EdgeFilter.NORMAL_DOWN));
        fragment.addBody(upNodeIndex);
      }
      else if (next > rightChild + rowsCount && next < leftChild) {
        rowsCount = next - rightChild + 1;
        blockSize++;
        queue.addAll(myLinearBekGraph.getAdjacentEdges(next, EdgeFilter.NORMAL_DOWN));
        fragment.addBody(upNodeIndex);
      }
      else if (next > leftChild) {

        int li = myGraphLayout.getLayoutIndex(next);
        if (leftLi > rightLi && !fragment.isMergeWithOldCommit()) {

          if (next > leftChild + MAGIC_SET_SIZE) {
            return null;
          }
          if (magicSet == null) {
            magicSet = calculateMagicSet(leftChild);
          }

          if (magicSet.contains(next)) {
            fragment.addTailEdge(upNodeIndex, next);
          }
          else {
            return null;
          }
        }
        else {
          if ((li > leftLi && li < rightLi) || (li == leftLi)) {
            fragment.addTailEdge(upNodeIndex, next);
          }
          else {
            if (li >= rightLi) {
              return null;
            }
            else {
              if (next > leftChild + MAGIC_SET_SIZE) {
                if (!fragment.hasTailEdge(upNodeIndex) && !fragment.isBody(upNodeIndex)) return null;
              }
              else {
                if (magicSet == null) {
                  magicSet = calculateMagicSet(leftChild);
                }

                if (magicSet.contains(next)) {
                  fragment.addTailEdge(upNodeIndex, next);
                }
                else {
                  return null;
                }
              }
            }
          }
        }
      }

      if (blockSize >= MAX_BLOCK_SIZE) {
        return null;
      }
    }

    if (fragment.getTails().isEmpty()) {
      return null; // this can happen if we ran into initial import
    }

    return fragment;
  }

  @NotNull
  private Set<Integer> calculateMagicSet(int node) {
    Set<Integer> magicSet;
    magicSet = ContainerUtil.newHashSet(MAGIC_SET_SIZE);

    PriorityQueue<Integer> magicQueue = new PriorityQueue<>(MAGIC_SET_SIZE);
    magicQueue.addAll(ContainerUtil.map(myLinearBekGraph.getAdjacentEdges(node, EdgeFilter.NORMAL_DOWN), GRAPH_EDGE_TO_DOWN_NODE));
    while (!magicQueue.isEmpty()) {
      Integer i = magicQueue.poll();
      if (i > node + MAGIC_SET_SIZE) break;
      magicSet.add(i);
      magicQueue.addAll(ContainerUtil.map(myLinearBekGraph.getAdjacentEdges(i, EdgeFilter.NORMAL_DOWN), GRAPH_EDGE_TO_DOWN_NODE));
    }
    return magicSet;
  }

  public static class MergeFragment {
    private final int myParent;
    private final int myLeftChild;
    private final int myRightChild;

    private boolean myMergeWithOldCommit = false;
    @NotNull private final IntIntMultiMap myTailEdges = new IntIntMultiMap();
    @NotNull private final TIntHashSet myBlockBody = new TIntHashSet();
    @NotNull private final TIntHashSet myTails = new TIntHashSet();

    private MergeFragment(int parent, int leftChild, int rightChild) {
      myParent = parent;
      myLeftChild = leftChild;
      myRightChild = rightChild;
    }

    public boolean isMergeWithOldCommit() {
      return myMergeWithOldCommit;
    }

    public void setMergeWithOldCommit(boolean mergeWithOldCommit) {
      myMergeWithOldCommit = mergeWithOldCommit;
    }

    public void addTail(int tail) {
      if (!myBlockBody.contains(tail)) {
        myTails.add(tail);
      }
    }

    public void addTailEdge(int upNodeIndex, int downNodeIndex) {
      if (!myBlockBody.contains(upNodeIndex)) {
        myTails.add(upNodeIndex);
        myTailEdges.putValue(upNodeIndex, downNodeIndex);
      }
    }

    public void addBody(int body) {
      myBlockBody.add(body);
    }

    @NotNull
    public TIntHashSet getTails() {
      return myTails;
    }

    public Set<Integer> getTailsAndBody() {
      Set<Integer> nodes = ContainerUtil.newHashSet();
      TIntIterator it = myBlockBody.iterator();
      while (it.hasNext()) {
        nodes.add(it.next());
      }
      it = myTails.iterator();
      while (it.hasNext()) {
        nodes.add(it.next());
      }
      return nodes;
    }

    public Set<Integer> getAllNodes() {
      Set<Integer> nodes = ContainerUtil.newHashSet();
      nodes.add(myParent);
      nodes.add(myLeftChild);
      nodes.add(myRightChild);
      nodes.addAll(getTailsAndBody());
      return nodes;
    }

    public void collapse(LinearBekGraph graph) {
      for (int upNodeIndex : myTailEdges.keys()) {
        for (int downNodeIndex : myTailEdges.get(upNodeIndex)) {
          removeEdge(graph, upNodeIndex, downNodeIndex);
        }
      }

      TIntIterator it = myTails.iterator();
      while (it.hasNext()) {
        int tail = it.next();
        if (!LinearGraphUtils.getDownNodes(graph, tail).contains(myLeftChild)) {
          addEdge(graph, tail, myLeftChild);
        }
        else {
          replaceEdge(graph, tail, myLeftChild);
        }
      }
      removeEdge(graph, myParent, myLeftChild);
    }

    private static void addEdge(LinearBekGraph graph, int up, int down) {
      graph.myDottedEdges.createEdge(new GraphEdge(up, down, null, GraphEdgeType.DOTTED));
    }

    private static void removeEdge(LinearBekGraph graph, int up, int down) {
      if (graph.myDottedEdges.hasEdge(up, down)) {
        graph.myDottedEdges.removeEdge(new GraphEdge(up, down, null, GraphEdgeType.DOTTED));
        graph.myHiddenEdges.createEdge(new GraphEdge(up, down, null, GraphEdgeType.DOTTED));
      }
      else {
        GraphEdge edge = LinearGraphUtils.getEdge(graph.myGraph, up, down);
        assert edge != null : "No edge between " + up + " and " + down;
        graph.myHiddenEdges.createEdge(edge);
      }
    }

    private static void replaceEdge(LinearBekGraph graph, int up, int down) {
      if (!graph.myDottedEdges.hasEdge(up, down)) {
        GraphEdge edge = LinearGraphUtils.getEdge(graph.myGraph, up, down);
        assert edge != null : "No edge between " + up + " and " + down;
        graph.myHiddenEdges.createEdge(edge);
        graph.myDottedEdges.createEdge(new GraphEdge(up, down, null, GraphEdgeType.DOTTED));
      }
    }

    public int getParent() {
      return myParent;
    }

    public boolean hasTailEdge(Integer index) {
      return !myTailEdges.get(index).isEmpty();
    }

    public boolean isBody(Integer index) {
      return myBlockBody.contains(index);
    }
  }

  private static class GraphEdgeComparator implements Comparator<GraphEdge> {
    @Override
    public int compare(@NotNull GraphEdge e1, @NotNull GraphEdge e2) {
      Integer d1 = e1.getDownNodeIndex();
      Integer d2 = e2.getDownNodeIndex();

      if (d1 == null) {
        if (d2 == null) return e1.hashCode() - e2.hashCode();
        return 1;
      }
      if (d2 == null) return -1;

      return d1.compareTo(d2);
    }
  }

  private static class GraphEdgeToDownNode implements Function<GraphEdge, Integer> {
    @Override
    public Integer fun(GraphEdge graphEdge) {
      return graphEdge.getDownNodeIndex();
    }
  }
}
