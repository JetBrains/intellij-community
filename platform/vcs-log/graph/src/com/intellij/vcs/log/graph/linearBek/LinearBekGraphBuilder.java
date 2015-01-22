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

import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.collapsing.EdgeStorage;
import com.intellij.vcs.log.graph.collapsing.EdgeStorageAdapter;
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
    Integer parent = ContainerUtil
      .find(ContainerUtil.reverse(ContainerUtil.sorted(LinearGraphUtils.getUpNodes(myWorkingGraph, currentNodeIndex))),
            new Condition<Integer>() {
              @Override
              public boolean value(Integer it) {
                return LinearGraphUtils.getDownNodes(myWorkingGraph, it).size() == 2;
              }
            });
    if (parent == null) return;
    List<Integer> downNodes = LinearGraphUtils.getDownNodes(myWorkingGraph, parent);

    int headNumber = myHeads.indexOf(currentHead);
    int nextHeadIndex = headNumber == myHeads.size() - 1
                        ? Integer.MAX_VALUE
                        : myGraphLayout
                          .getLayoutIndex(myHeads.get(headNumber + 1)); // TODO dont make it bad, take a bad code and make it better
    int headIndex = myGraphLayout.getLayoutIndex(currentHead);

    int firstChildIndex = downNodes.get(0);
    if (firstChildIndex == currentNodeIndex) {
      int secondChildIndex = downNodes.get(1);
      if (secondChildIndex > firstChildIndex) {
        // geometrically first child is higher than the second, so switching them
        if (collapse(secondChildIndex, firstChildIndex, parent, headIndex, nextHeadIndex, visited)) {
          myWorkingGraph.apply();
        }
        else {
          myWorkingGraph.clear();
        }
      }
    }
    else {
      if (collapse(firstChildIndex, currentNodeIndex, parent, headIndex, nextHeadIndex, visited)) {
        myWorkingGraph.apply();
      }
      else {
        myWorkingGraph.clear();
      }
    }
  }

  private boolean collapse(int firstChildIndex, int currentNodeIndex, int parent, int headIndex, int nextHeadIndex, BitSetFlags visited) {
    int x = myGraphLayout.getLayoutIndex(firstChildIndex);
    int y = myGraphLayout.getLayoutIndex(currentNodeIndex);
    int k = 1;

    PriorityQueue<GraphEdge> queue = new PriorityQueue<GraphEdge>(MAX_BLOCK_SIZE/*todo?*/, new GraphEdgeComparator());
    queue.addAll(myWorkingGraph.getAdjacentEdges(currentNodeIndex, EdgeFilter.NORMAL_DOWN));

    Set<Integer> definitelyNotTails = ContainerUtil.newHashSet(MAX_BLOCK_SIZE/*todo?*/);
    Set<Integer> tails = ContainerUtil.newHashSet(MAX_BLOCK_SIZE/*todo?*/);
    boolean mergeWithOldCommit = false;

    while (!queue.isEmpty()) {
      GraphEdge nextEdge = queue.poll();
      Integer next = nextEdge.getDownNodeIndex();
      if (next == null) return false; // well, what do you do

      Integer upNodeIndex = nextEdge.getUpNodeIndex();

      if (next == firstChildIndex) {
        // found first child
        tails.add(upNodeIndex);
        mergeWithOldCommit = true;
      }
      else if (next < currentNodeIndex + k) {
        // or we were here before
        // so doing nothing?
      }
      else if (next == currentNodeIndex + k) {
        // all is fine, continuing
        k++;
        queue.addAll(myWorkingGraph.getAdjacentEdges(next, EdgeFilter.NORMAL_DOWN));
        definitelyNotTails.add(upNodeIndex);
      }
      else if (next > currentNodeIndex + k && next < firstChildIndex) {
        int li = myGraphLayout.getLayoutIndex(next);
        if (li > y) {
          return false;
        }
        if (li <= x) {
          if (!(li >= headIndex && li < nextHeadIndex)) {
            return false;
          }
        }
        k++;
        queue.addAll(myWorkingGraph.getAdjacentEdges(next, EdgeFilter.NORMAL_DOWN));

        // here we have to decide whether next is a part of the block or not
        if (visited.get(next)) { // TODO should get rid of visited here (same problem as with merge with old commit detection)?
          definitelyNotTails.add(upNodeIndex);
        }
      }
      else if (next > firstChildIndex) {
        int li = myGraphLayout.getLayoutIndex(next);
        if (li > y) {
          return false;
        }
        if (li < x && !(li >= headIndex && li < nextHeadIndex)) {
          return false;
        }
        else {
          if (!definitelyNotTails.contains(upNodeIndex)) {
            tails.add(upNodeIndex);
            if (li != y && (li >= x)) {
              myWorkingGraph.removeEdge(upNodeIndex, next); // questionable -- we remove edges to the very old commits only for tails
              // done in sake of expanding dotted edges
              // also, should check (I guess?) that the edge is not too long
            }
          }
        }
      }

      if (k >= MAX_BLOCK_SIZE) {
        return false;
      }
      if (Math.abs(myTimestampGetter.getTimestamp(currentNodeIndex) - myTimestampGetter.getTimestamp(currentNodeIndex + k - 1)) >
          MAX_DELTA_TIME) {
        // there is a big question what we should really check here
        // maybe we should also ensure that we do not remove edges to very old commits too
        return false;
      }
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

    return true;
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
      super(graph, createSimpleEdgeStorage(), createSimpleEdgeStorage());
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

  private static EdgeStorageAdapter createSimpleEdgeStorage() {
    return new EdgeStorageAdapter(new EdgeStorage(), new Function.Self<Integer, Integer>(), new Function.Self<Integer, Integer>());
  }
}
