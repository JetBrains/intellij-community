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
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.collapsing.GraphAdditionalEdges;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class LinearBekGraphBuilder implements GraphVisitorAlgorithm.GraphVisitor {
  private static final int MAX_BLOCK_SIZE = 20;
  private static final long MAX_DELTA_TIME = 60 * 60 * 24 * 3 * 1000l;
  @NotNull private final WorkingGraph myWorkingGraph;
  @NotNull private final GraphLayout myGraphLayout;
  @NotNull private final List<Integer> myHeads;
  @NotNull private final TimestampGetter myTimestampGetter;

  public LinearBekGraphBuilder(@NotNull LinearGraph graph, @NotNull GraphLayout graphLayout, @NotNull TimestampGetter timestampGetter) {
    myWorkingGraph = new WorkingGraph(graph);
    myGraphLayout = graphLayout;
    myHeads = graphLayout.getHeadNodeIndex();
    myTimestampGetter = timestampGetter;
  }

  public LinearBekGraph build() {
    new GraphVisitorAlgorithm(true).visitGraph(myWorkingGraph.myGraph, myGraphLayout, this);
    return myWorkingGraph.createLinearBekGraph();
  }

  @Override
  public void enterSubtree(int currentNodeIndex, int currentHead, BitSetFlags visited) {
  }

  @Override
  public void leaveSubtree(int currentNodeIndex, int currentHead, BitSetFlags visited) {
    myWorkingGraph.clear();

    List<Integer> upNodes = LinearGraphUtils.getUpNodes(myWorkingGraph, currentNodeIndex);
    if (upNodes.size() != 1) return;
    int parent = upNodes.get(0);
    if (LinearGraphUtils.getDownNodes(myWorkingGraph, parent).size() != 2) {
      return;
    }

    int firstChildIndex = LinearGraphUtils.getDownNodes(myWorkingGraph, parent).get(0);
    boolean switched = false;
    if (firstChildIndex == currentNodeIndex) {
      if (firstChildIndex > LinearGraphUtils.getDownNodes(myWorkingGraph, parent).get(1)) {
        return;
      }
      switched = true;
      firstChildIndex = LinearGraphUtils.getDownNodes(myWorkingGraph, parent).get(1);
    }
    if (firstChildIndex < currentNodeIndex) return;

    int x = myGraphLayout.getLayoutIndex(firstChildIndex);
    int y = myGraphLayout.getLayoutIndex(currentNodeIndex);
    if (switched && x != y) return;
    int k = 1;

    int headNumber = myHeads.indexOf(currentHead);
    int nextHeadIndex = headNumber == myHeads.size() - 1
                        ? Integer.MAX_VALUE
                        : myGraphLayout
                          .getLayoutIndex(myHeads.get(headNumber + 1)); // TODO dont make it bad, take a bad code and make it better
    int headIndex = myGraphLayout.getLayoutIndex(currentHead);

    PriorityQueue<GraphEdge> queue = new PriorityQueue<GraphEdge>(MAX_BLOCK_SIZE/*todo?*/, new GraphEdgeComparator());
    addDownEdges(myWorkingGraph, currentNodeIndex, queue);

    Set<Integer> definitelyNotTails = ContainerUtil.newHashSet(MAX_BLOCK_SIZE/*todo?*/);
    Set<Integer> tails = ContainerUtil.newHashSet(MAX_BLOCK_SIZE/*todo?*/);
    while (!queue.isEmpty()) {
      GraphEdge nextEdge = queue.poll();
      Integer next = nextEdge.getDownNodeIndex();
      if (next == null) return; // well, what do you do

      Integer upNodeIndex = nextEdge.getUpNodeIndex();

      if (next == firstChildIndex) {
        // found first child
        tails.add(upNodeIndex);
      }
      else if (next < currentNodeIndex + k) {
        // or we were here before
        // so doing nothing?
      }
      else if (next == currentNodeIndex + k) {
        // all is fine, continuing
        k++;
        addDownEdges(myWorkingGraph, next, queue);
        definitelyNotTails.add(upNodeIndex);
      }
      else if (next > currentNodeIndex + k && next < firstChildIndex) {
        int li = myGraphLayout.getLayoutIndex(next);
        if (li > y) {
          return;
        }
        if (li <= x) {
          if (!(li >= headIndex && li < nextHeadIndex)) {
            return;
          }
        }
        k++;
        addDownEdges(myWorkingGraph, next, queue);

        // here we have to decide whether next is a part of the block or not
        if (visited.get(next)) {
          definitelyNotTails.add(upNodeIndex);
        }
      }
      else if (next > firstChildIndex) {
        int li = myGraphLayout.getLayoutIndex(next);
        if (li > y) {
          return;
        }
        if (li < x) {
          if (!(li >= headIndex && li < nextHeadIndex)) {
            return;
          }
        }
        else {
          if (!definitelyNotTails.contains(upNodeIndex)) {
            tails.add(upNodeIndex);
            if (li != y) {
              myWorkingGraph.removeEdge(upNodeIndex, next); // questionable -- we remove edges to the very old commits only for tails
              // done in sake of expanding dotted edges
              // also, should check (I guess?) that the edge is not too long
            }
          }
        }
      }

      if (k >= MAX_BLOCK_SIZE) {
        return;
      }
      if (Math.abs(myTimestampGetter.getTimestamp(currentNodeIndex) - myTimestampGetter.getTimestamp(currentNodeIndex + k - 1)) >
          MAX_DELTA_TIME) {
        // there is a big question what we should really check here
        // maybe we should also ensure that we do not remove edges to very old commits too
        return;
      }
    }

    boolean mergeWithOldCommit = currentNodeIndex + k == firstChildIndex && visited.get(firstChildIndex);
    if (switched && !mergeWithOldCommit) {
      return;
    }

    for (Integer tail : tails) {
      if (!LinearGraphUtils.getDownNodes(myWorkingGraph, tail).contains(firstChildIndex)) {
        myWorkingGraph.addEdge(tail, firstChildIndex);
      }
      else if (mergeWithOldCommit) {
        myWorkingGraph.removeEdge(tail, firstChildIndex);
        myWorkingGraph.addEdge(tail, firstChildIndex);
      }
    }

    if (!tails.isEmpty() || mergeWithOldCommit) {
      myWorkingGraph.removeEdge(parent, firstChildIndex);
    }
    myWorkingGraph.apply();
  }

  private static void addDownEdges(@NotNull LinearGraph graph, int node, @NotNull Collection<GraphEdge> collection) {
    for (GraphEdge edge : graph.getAdjacentEdges(node)) {
      if (LinearGraphUtils.isEdgeToDown(edge, node)) {
        collection.add(edge);
      }
    }
  }

  private static class GraphEdgeComparator implements Comparator<GraphEdge> {
    @Override
    public int compare(@NotNull GraphEdge o1, @NotNull GraphEdge o2) {
      if (o1.getDownNodeIndex() == null) return -1;
      if (o2.getDownNodeIndex() == null) return 1;
      return o1.getDownNodeIndex().compareTo(o2.getDownNodeIndex());
    }
  }

  private static class WorkingGraph extends LinearBekGraph {
    private final List<GraphEdge> myToAdd = new ArrayList<GraphEdge>();
    private final List<GraphEdge> myToRemove = new ArrayList<GraphEdge>();
    private final List<GraphEdge> myDottedToRemove = new ArrayList<GraphEdge>();

    private WorkingGraph(LinearGraph graph) {
      super(graph, createSimpleAdditionalEdges(), createSimpleAdditionalEdges());
    }

    public void addEdge(int from, int to) {
      myToAdd.add(new GraphEdge(from, to, null, GraphEdgeType.DOTTED));
    }

    public void removeEdge(int from, int to) {
      if (myDottedEdges.hasEdge(from, to)) {
        myDottedToRemove.add(new GraphEdge(from, to, null, GraphEdgeType.DOTTED));
      }
      else {
        myToRemove.add(LinearGraphUtils.getEdge(myGraph, from, to));
      }
    }

    public void apply() {
      for (GraphEdge e : myToRemove) {
        myHiddenEdges.createEdge(e);
      }
      for (GraphEdge e : myDottedToRemove) {
        myDottedEdges.removeEdge(e);
        myHiddenEdges.createEdge(e);
      }
      // TODO if we start with adding edges instead of removing we will break merge with old commit test
      // that's really bad
      for (GraphEdge e : myToAdd) {
        myDottedEdges.createEdge(e);
      }

      // todo in this specific place we should remember which edges were hidden
      // 1) we hide some edges that do not go to first child, but below it
      // 2) we hide some dotted edges; we can not restore them
      // we should either change collapsing algorithm (simplify it) or save some stuff here somehow

      clear();
    }

    public void clear() {
      myToAdd.clear();
      myToRemove.clear();
      myDottedToRemove.clear();
    }

    public LinearBekGraph createLinearBekGraph() {
      return new LinearBekGraph(myGraph, myHiddenEdges, myDottedEdges);
    }
  }

  private static GraphAdditionalEdges createSimpleAdditionalEdges() {
    return GraphAdditionalEdges.newInstance(new Function.Self<Integer, Integer>(), new Function.Self<Integer, Integer>());
  }
}
