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
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.collapsing.EdgeStorageWrapper;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class LinearBekGraphBuilder {
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
    Map<Integer, Integer> nextHeadLayoutIndexes = ContainerUtilRt.newHashMap(myHeads.size());
    for (int i = 0; i < myHeads.size(); i++) {
      if (i == myHeads.size() - 1) {
        nextHeadLayoutIndexes.put(myHeads.get(i), Integer.MAX_VALUE);
      }
      else {
        nextHeadLayoutIndexes.put(myHeads.get(i), myGraphLayout.getLayoutIndex(myHeads.get(i + 1)));
      }
    }

    for (int i = myWorkingGraph.myGraph.nodesCount() - 1; i >= 0; i--) {
      List<Integer> downNodes = ContainerUtil.sorted(LinearGraphUtils.getDownNodes(myWorkingGraph, i));
      if (downNodes.size() != 2) continue;

      int head = myGraphLayout.getOneOfHeadNodeIndex(i);
      if (collapse(downNodes.get(1), downNodes.get(0), i, myGraphLayout.getLayoutIndex(head), nextHeadLayoutIndexes.get(head))) {
        myWorkingGraph.apply();
      }
      else {
        myWorkingGraph.clear();
      }
    }
    return myWorkingGraph.createLinearBekGraph();
  }

  private boolean collapse(int firstChild, int secondChild, int parent, int headIndex, int nextHeadIndex) {
    int x = myGraphLayout.getLayoutIndex(firstChild);
    int y = myGraphLayout.getLayoutIndex(secondChild);
    int k = 1;

    PriorityQueue<GraphEdge> queue = new PriorityQueue<GraphEdge>(MAX_BLOCK_SIZE, new GraphEdgeComparator());
    queue.addAll(myWorkingGraph.getAdjacentEdges(secondChild, EdgeFilter.NORMAL_DOWN));

    Set<Integer> definitelyNotTails = ContainerUtil.newHashSet();
    Set<Integer> tails = ContainerUtil.newHashSet();
    boolean mergeWithOldCommit = false;

    while (!queue.isEmpty()) {
      GraphEdge nextEdge = queue.poll();
      Integer next = nextEdge.getDownNodeIndex();
      if (next == null) continue; // allow very long edges down

      Integer upNodeIndex = nextEdge.getUpNodeIndex();
      assert upNodeIndex != null; // can not happen

      if (next == firstChild) {
        // found first child
        tails.add(upNodeIndex);
        mergeWithOldCommit = true;
      }
      else if (next == secondChild + k) {
        // all is fine, continuing
        k++;
        queue.addAll(myWorkingGraph.getAdjacentEdges(next, EdgeFilter.NORMAL_DOWN));
        definitelyNotTails.add(upNodeIndex);
      }
      else if (next > secondChild + k && next < firstChild) {
        k = next - secondChild + 1;
        queue.addAll(myWorkingGraph.getAdjacentEdges(next, EdgeFilter.NORMAL_DOWN));
        definitelyNotTails.add(upNodeIndex);
      }
      else if (next > firstChild) {
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
              // another thing is that if li < x next can still be reachable from the firstChild
              // but we do not know that unless we walk down from the firstChild and check that
            }
          }
        }
      }

      if (k >= MAX_BLOCK_SIZE) {
        return false;
      }
      if (Math.abs(myTimestampGetter.getTimestamp(secondChild) - myTimestampGetter.getTimestamp(secondChild + k - 1)) > MAX_DELTA_TIME) {
        // there is a big question what we should really check here
        // maybe we should also ensure that we do not remove edges to very old commits too
        return false;
      }
    }

    if (tails.isEmpty()) {
      return false; // this can happen if we ran into initial import
    }

    for (Integer tail : tails) {
      if (!LinearGraphUtils.getDownNodes(myWorkingGraph, tail).contains(firstChild)) {
        myWorkingGraph.addEdge(tail, firstChild);
      }
      else if (mergeWithOldCommit) {
        myWorkingGraph.replaceEdge(tail, firstChild);
      }
    }
    myWorkingGraph.removeEdge(parent, firstChild);

    return true;
  }

  private static class GraphEdgeComparator implements Comparator<GraphEdge> {
    @Override
    public int compare(@NotNull GraphEdge o1, @NotNull GraphEdge o2) {
      Integer d1 = o1.getDownNodeIndex();
      Integer d2 = o2.getDownNodeIndex();

      if (d1 == null) {
        if (d2 == null) return 0;
        return 1;
      }
      if (d2 == null) return -1;

      return d1.compareTo(d2);
    }
  }

  private static class WorkingGraph extends LinearBekGraph {
    private final List<GraphEdge> myToAdd = new ArrayList<GraphEdge>();
    private final List<GraphEdge> myToReplace = new ArrayList<GraphEdge>();
    private final List<GraphEdge> myNormalToHide = new ArrayList<GraphEdge>();
    private final List<GraphEdge> myDottedToHide = new ArrayList<GraphEdge>();

    private WorkingGraph(LinearGraph graph) {
      super(graph, EdgeStorageWrapper.createSimpleEdgeStorage(), EdgeStorageWrapper.createSimpleEdgeStorage());
    }

    public void addEdge(int up, int down) {
      myToAdd.add(new GraphEdge(up, down, null, GraphEdgeType.DOTTED));
    }

    public void removeEdge(int up, int down) {
      if (myDottedEdges.hasEdge(up, down)) {
        myDottedToHide.add(new GraphEdge(up, down, null, GraphEdgeType.DOTTED));
      }
      else {
        myNormalToHide.add(LinearGraphUtils.getEdge(myGraph, up, down));
      }
    }

    public void replaceEdge(int up, int down) {
      if (!myDottedEdges.hasEdge(up, down)) {
        myToReplace.add(LinearGraphUtils.getEdge(myGraph, up, down));
      }
    }

    public void apply() {
      for (GraphEdge e : myNormalToHide) {
        myHiddenEdges.createEdge(e);
      }
      for (GraphEdge e : myDottedToHide) {
        myDottedEdges.removeEdge(e);
        myHiddenEdges.createEdge(e);
      }
      for (GraphEdge e : myToAdd) {
        myDottedEdges.createEdge(e);
      }
      for (GraphEdge e : myToReplace) {
        myHiddenEdges.createEdge(e);
        myDottedEdges.createEdge(new GraphEdge(e.getUpNodeIndex(), e.getDownNodeIndex(), null, GraphEdgeType.DOTTED));
      }

      clear();
    }

    public void clear() {
      myToAdd.clear();
      myToReplace.clear();
      myNormalToHide.clear();
      myDottedToHide.clear();
    }

    public LinearBekGraph createLinearBekGraph() {
      return new LinearBekGraph(myGraph, myHiddenEdges, myDottedEdges);
    }
  }
}
