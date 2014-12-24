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

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class LinearBekGraphBuilder implements GraphVisitorAlgorithm.GraphVisitor {
  private final static int MAX_BLOCK_SIZE = 200; // specially tailored for 17740ba5899bc13de622808e0e0f4fbf6285e8b5
  // this high delta is kinda useless (its about like ten cases in idea when a block does not match this), but I really want to collapse 17740ba5899bc13de622808e0e0f4fbf6285e8b5
  private static final long MAX_DELTA_TIME = 60 * 60 * 24 * 365 * 1000l;
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

    List<Integer> upNodes = myWorkingGraph.getUpNodes(currentNodeIndex);
    if (upNodes.size() != 1) return;
    int parent = upNodes.get(0);
    if (myWorkingGraph.getDownNodes(parent).size() != 2) {
      return;
    }

    int firstChildIndex = myWorkingGraph.getDownNodes(parent).get(0);
    boolean switched = false;
    if (firstChildIndex == currentNodeIndex) {
      if (firstChildIndex > myWorkingGraph.getDownNodes(parent).get(1)) {
        return;
      }
      switched = true;
      firstChildIndex = myWorkingGraph.getDownNodes(parent).get(1);
    }

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

      if (next == firstChildIndex || next < currentNodeIndex + k) {
        // found first child
        // or we were here before
      }
      else if (next == currentNodeIndex + k) {
        // all is fine, continuing
        k++;
        addDownEdges(myWorkingGraph, next, queue);
        definitelyNotTails.add(nextEdge.getUpNodeIndex());
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
          definitelyNotTails.add(nextEdge.getUpNodeIndex());
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
          if (!definitelyNotTails.contains(nextEdge.getUpNodeIndex())) {
            tails.add(nextEdge.getUpNodeIndex());
          }
          myWorkingGraph.removeEdge(nextEdge.getUpNodeIndex(), nextEdge.getDownNodeIndex());
        }
      }

      if (k >= MAX_BLOCK_SIZE) {
        return;
      }
      if (Math.abs(myTimestampGetter.getTimestamp(currentNodeIndex) - myTimestampGetter.getTimestamp(currentNodeIndex + k)) >
          MAX_DELTA_TIME) {
        return;
      }
    }

    boolean mergeWithOldCommit = currentNodeIndex + k == firstChildIndex && visited.get(firstChildIndex);
    if (switched && !mergeWithOldCommit) {
      return;
    }

    for (Integer tail : tails) {
      if (!myWorkingGraph.getDownNodes(tail).contains(firstChildIndex)) {
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
      super(graph, LinearBekController.createSimpleAdditionalEdges(), LinearBekController.createSimpleAdditionalEdges());
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
      for (GraphEdge e : myToAdd) {
        myDottedEdges.createEdge(e);
      }
      for (GraphEdge e : myToRemove) {
        myHiddenEdges.createEdge(e);
      }
      for (GraphEdge e : myDottedToRemove) {
        myDottedEdges.removeEdge(e);
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
}
