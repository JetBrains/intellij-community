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
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.collapsing.EdgeStorageWrapper;
import com.intellij.vcs.log.graph.utils.IntIntMultiMap;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class LinearBekGraphBuilder {
  private static final int MAX_BLOCK_SIZE = 200;
  public static final int MAGIC_SET_SIZE = 30;
  @NotNull private final WorkingGraph myWorkingGraph;
  @NotNull private final GraphLayout myGraphLayout;
  @NotNull private final List<Integer> myHeads;

  public LinearBekGraphBuilder(@NotNull LinearGraph graph, @NotNull GraphLayout graphLayout) {
    myWorkingGraph = new WorkingGraph(graph);
    myGraphLayout = graphLayout;
    myHeads = graphLayout.getHeadNodeIndex();
  }

  public LinearBekGraphBuilder(@NotNull LinearGraph graph, @NotNull GraphLayout graphLayout, @NotNull LinearBekGraph bekGraph) {
    myWorkingGraph = new WorkingGraph(graph, bekGraph.myHiddenEdges, bekGraph.myDottedEdges);
    myGraphLayout = graphLayout;
    myHeads = graphLayout.getHeadNodeIndex();
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
      MergeFragment fragment =
        getFragment(downNodes.get(1), downNodes.get(0), i, myGraphLayout.getLayoutIndex(head), nextHeadLayoutIndexes.get(head));
      if (fragment != null) {
        fragment.collapse(myWorkingGraph);
      }
    }
    return myWorkingGraph.createLinearBekGraph();
  }

  @Nullable
  public boolean collapseFragment(int mergeCommit) {
    MergeFragment fragment = getFragment(mergeCommit);
    if (fragment != null) {
      fragment.collapse(myWorkingGraph);
      return true;
    }
    else {
      myWorkingGraph.clear();
      return false;
    }
  }

  @Nullable
  public MergeFragment getFragment(int mergeCommit) {
    List<Integer> downNodes = ContainerUtil.sorted(LinearGraphUtils.getDownNodes(myWorkingGraph, mergeCommit));
    if (downNodes.size() != 2) return null;

    int head = myGraphLayout.getOneOfHeadNodeIndex(mergeCommit);
    int i = myHeads.indexOf(head);
    int nextHeadLi = Integer.MAX_VALUE;
    if (i != myHeads.size() - 1) {
      nextHeadLi = myGraphLayout.getLayoutIndex(myHeads.get(i + 1));
    }
    return getFragment(downNodes.get(1), downNodes.get(0), mergeCommit, myGraphLayout.getLayoutIndex(head), nextHeadLi);
  }

  @Nullable
  private MergeFragment getFragment(int firstChild, int secondChild, int parent, int headLi, int nextHeadLi) {
    MergeFragment fragment = new MergeFragment(parent, firstChild, secondChild);

    int x = myGraphLayout.getLayoutIndex(firstChild);
    int y = myGraphLayout.getLayoutIndex(secondChild);
    int k = 1;
    int blockSize = 1;

    PriorityQueue<GraphEdge> queue = new PriorityQueue<GraphEdge>(MAX_BLOCK_SIZE, new GraphEdgeComparator());
    queue.addAll(myWorkingGraph.getAdjacentEdges(secondChild, EdgeFilter.NORMAL_DOWN));

    @Nullable Set<Integer> magicSet = null;

    while (!queue.isEmpty()) {
      GraphEdge nextEdge = queue.poll();
      Integer next = nextEdge.getDownNodeIndex();
      if (next == null) continue; // allow very long edges down

      Integer upNodeIndex = nextEdge.getUpNodeIndex();
      assert upNodeIndex != null; // can not happen

      if (next == firstChild) {
        // found first child
        fragment.addTail(upNodeIndex);
        fragment.setMergeWithOldCommit(true);
      }
      else if (next == secondChild + k) {
        // all is fine, continuing
        k++;
        blockSize++;
        queue.addAll(myWorkingGraph.getAdjacentEdges(next, EdgeFilter.NORMAL_DOWN));
        fragment.addBody(upNodeIndex);
      }
      else if (next > secondChild + k && next < firstChild) {
        k = next - secondChild + 1;
        blockSize++;
        queue.addAll(myWorkingGraph.getAdjacentEdges(next, EdgeFilter.NORMAL_DOWN));
        fragment.addBody(upNodeIndex);
      }
      else if (next > firstChild) {
        int li = myGraphLayout.getLayoutIndex(next);
        if (x > y && !fragment.isMergeWithOldCommit()) {
          if (next > firstChild + MAGIC_SET_SIZE) return null;
          if (magicSet == null) {
            magicSet = calculateMagicSet(firstChild);
          }

          if (magicSet.contains(next)) {
            fragment.addTailEdge(upNodeIndex, next);
          }
          else {
            return null;
          }
        }
        else {
          if ((li > x && li < y) || (li == x)) {
            fragment.addTailEdge(upNodeIndex, next);
          }
          else {
            if (li >= y) {
              return null;
            }
            else {
              if (next > firstChild + MAGIC_SET_SIZE) {
                fragment.addTail(upNodeIndex);
              }
              else {
                if (magicSet == null) {
                  magicSet = calculateMagicSet(firstChild);
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
  private Set<Integer> calculateMagicSet(int firstChild) {
    Set<Integer> magicSet;
    magicSet = ContainerUtil.newHashSet(MAGIC_SET_SIZE);

    PriorityQueue<Integer> magicQueue = new PriorityQueue<Integer>(MAGIC_SET_SIZE);
    magicQueue
      .addAll(ContainerUtil.map(myWorkingGraph.getAdjacentEdges(firstChild, EdgeFilter.NORMAL_DOWN), new Function<GraphEdge, Integer>() {
        @Override
        public Integer fun(GraphEdge graphEdge) {
          return graphEdge.getDownNodeIndex();
        }
      }));
    while (!magicQueue.isEmpty()) {
      Integer i = magicQueue.poll();
      if (i > firstChild + MAGIC_SET_SIZE) break;
      magicSet.add(i);
      magicQueue.addAll(ContainerUtil.map(myWorkingGraph.getAdjacentEdges(i, EdgeFilter.NORMAL_DOWN), new Function<GraphEdge, Integer>() {
        @Override
        public Integer fun(GraphEdge graphEdge) {
          return graphEdge.getDownNodeIndex();
        }
      }));
    }
    return magicSet;
  }

  // todo well, name
  public static class MergeFragment {
    private final int myParent;
    private final int myFirstChild;
    private final int mySecondChild;

    private boolean myMergeWithOldCommit = false;
    @NotNull private final IntIntMultiMap myTailEdges = new IntIntMultiMap();
    @NotNull private final TIntHashSet myBlockBody = new TIntHashSet();
    @NotNull private final TIntHashSet myTails = new TIntHashSet();

    private MergeFragment(int parent, int firstChild, int secondChild) {
      myParent = parent;
      myFirstChild = firstChild;
      mySecondChild = secondChild;
    }

    public boolean isMergeWithOldCommit() {
      return myMergeWithOldCommit;
    }

    public void setMergeWithOldCommit(boolean mergeWithOldCommit) {
      myMergeWithOldCommit = mergeWithOldCommit;
    }

    public boolean addTail(int tail) {
      if (!myBlockBody.contains(tail)) {
        myTails.add(tail);
        return true;
      }
      return false;
    }

    public boolean addTailEdge(int upNodeIndex, int downNodeIndex) {
      if (!myBlockBody.contains(upNodeIndex)) {
        myTails.add(upNodeIndex);
        myTailEdges.putValue(upNodeIndex, downNodeIndex);
        return true;
      }
      return false;
    }

    public void addBody(int body) {
      myBlockBody.add(body);
    }

    public TIntHashSet getTails() {
      return myTails;
    }

    public Set<Integer> getAllNodes() {
      Set<Integer> nodes = ContainerUtil.newHashSet();
      nodes.add(myParent);
      nodes.add(myFirstChild);
      nodes.add(mySecondChild);
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

    public void collapse(WorkingGraph workingGraph) {
      for (int upNodeIndex : myTailEdges.keys()) {
        for (int downNodeIndex : myTailEdges.get(upNodeIndex)) {
          workingGraph.removeEdge(upNodeIndex, downNodeIndex);
        }
      }

      TIntIterator it = myTails.iterator();
      while (it.hasNext()) {
        int tail = it.next();
        if (!LinearGraphUtils.getDownNodes(workingGraph, tail).contains(myFirstChild)) {
          workingGraph.addEdge(tail, myFirstChild);
        }
        else if (myMergeWithOldCommit) {
          workingGraph.replaceEdge(tail, myFirstChild);
        }
      }
      workingGraph.removeEdge(myParent, myFirstChild);

      workingGraph.apply();
    }
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
    @NotNull private final List<GraphEdge> myToAdd = new ArrayList<GraphEdge>();
    @NotNull private final List<GraphEdge> myToReplace = new ArrayList<GraphEdge>();
    @NotNull private final List<GraphEdge> myNormalToHide = new ArrayList<GraphEdge>();
    @NotNull private final List<GraphEdge> myDottedToHide = new ArrayList<GraphEdge>();

    private WorkingGraph(@NotNull LinearGraph graph) {
      super(graph, EdgeStorageWrapper.createSimpleEdgeStorage(), EdgeStorageWrapper.createSimpleEdgeStorage());
    }

    private WorkingGraph(@NotNull LinearGraph graph, @NotNull EdgeStorageWrapper hiddenEdges, @NotNull EdgeStorageWrapper dottedEdges) {
      super(graph, hiddenEdges, dottedEdges);
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
