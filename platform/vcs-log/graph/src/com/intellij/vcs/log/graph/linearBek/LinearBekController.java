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
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.api.printer.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.collapsing.GraphAdditionalEdges;
import com.intellij.vcs.log.graph.impl.facade.BekBaseLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.CascadeLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.bek.BekIntMap;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class LinearBekController extends CascadeLinearGraphController {
  private final static int MAX_BLOCK_SIZE = 200; // specially tailored for 17740ba5899bc13de622808e0e0f4fbf6285e8b5
  @NotNull private final LinearGraph myCompiledGraph;

  public LinearBekController(@NotNull BekBaseLinearGraphController controller, @NotNull PermanentGraphInfo permanentGraphInfo) {
    super(controller, permanentGraphInfo);
    long start = System.currentTimeMillis();
    myCompiledGraph = compileGraph(getDelegateLinearGraphController().getCompiledGraph(),
                                   new BekGraphLayout(permanentGraphInfo.getPermanentGraphLayout(), controller.getBekIntMap()));
    long end = System.currentTimeMillis();
    System.err.println(((double)end - start) / 1000);
  }

  static LinearGraph compileGraph(@NotNull final LinearGraph graph, @NotNull final GraphLayout graphLayout) {
    final WorkingGraph workingGraph = new WorkingGraph(graph);
    final List<Integer> heads = graphLayout.getHeadNodeIndex();

    new GraphVisitorAlgorithm(true).visitGraph(graph, graphLayout, new MyGraphVisitor(workingGraph, graphLayout, heads));

    return workingGraph.createLinearBekGraph();
  }

  public static void addDownEdges(@NotNull LinearGraph graph, int node, @NotNull Collection<GraphEdge> collection) {
    for (GraphEdge edge : graph.getAdjacentEdges(node)) {
      if (LinearGraphUtils.isEdgeToDown(edge, node)) {
        collection.add(edge);
      }
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

  @Override
  protected boolean elementIsSelected(@NotNull PrintElementWithGraphElement printElement) {
    return false;
  }

  @NotNull
  @Override
  protected LinearGraphAnswer performDelegateUpdate(@NotNull LinearGraphAnswer delegateAnswer) {
    return delegateAnswer;
  }

  @Nullable
  @Override
  protected LinearGraphAnswer performAction(@NotNull LinearGraphAction action) {
    return null;
  }

  @NotNull
  @Override
  public LinearGraph getCompiledGraph() {
    return myCompiledGraph;
  }


  public static GraphAdditionalEdges createSimpleAdditionalEdges() {
    return GraphAdditionalEdges.newInstance(new Function.Self<Integer, Integer>(), new Function.Self<Integer, Integer>());
  }

  private static class BekGraphLayout implements GraphLayout {
    private final GraphLayout myGraphLayout;
    private final BekIntMap myBekIntMap;

    public BekGraphLayout(GraphLayout graphLayout, BekIntMap bekIntMap) {
      myGraphLayout = graphLayout;
      myBekIntMap = bekIntMap;
    }

    @Override
    public int getLayoutIndex(int nodeIndex) {
      return myGraphLayout.getLayoutIndex(myBekIntMap.getUsualIndex(nodeIndex));
    }

    @Override
    public int getOneOfHeadNodeIndex(int nodeIndex) {
      int usualIndex = myGraphLayout.getOneOfHeadNodeIndex(myBekIntMap.getUsualIndex(nodeIndex));
      return myBekIntMap.getBekIndex(usualIndex);
    }

    @NotNull
    @Override
    public List<Integer> getHeadNodeIndex() {
      List<Integer> bekIndexes = new ArrayList<Integer>();
      for (int head : myGraphLayout.getHeadNodeIndex()) {
        bekIndexes.add(myBekIntMap.getBekIndex(head));
      }
      return bekIndexes;
    }
  }

  private static class MyGraphVisitor implements GraphVisitorAlgorithm.GraphVisitor {
    private final WorkingGraph myWorkingGraph;
    private final GraphLayout myGraphLayout;
    private final List<Integer> myHeads;

    public MyGraphVisitor(WorkingGraph workingGraph, GraphLayout graphLayout, List<Integer> heads) {
      myWorkingGraph = workingGraph;
      myGraphLayout = graphLayout;
      myHeads = heads;
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

      PriorityQueue<GraphEdge> queue = new PriorityQueue<GraphEdge>(MAX_BLOCK_SIZE/*todo?*/, new Comparator<GraphEdge>() {
        @Override
        public int compare(@NotNull GraphEdge o1, @NotNull GraphEdge o2) {
          if (o1.getDownNodeIndex() == null) return -1;
          if (o2.getDownNodeIndex() == null) return 1;
          return o1.getDownNodeIndex().compareTo(o2.getDownNodeIndex());
        }
      });
      addDownEdges(myWorkingGraph, currentNodeIndex, queue);

      Set<Integer> definitelyNotTails = ContainerUtil.newHashSet(MAX_BLOCK_SIZE/*todo?*/);
      Set<Integer> tails = ContainerUtil.newHashSet(MAX_BLOCK_SIZE/*todo?*/);
      while (!queue.isEmpty()) {
        GraphEdge nextEdge = queue.poll();
        Integer next = nextEdge.getDownNodeIndex();
        if (next == null) return; // well, what do you do

        if (next == firstChildIndex) {
          // found first child
        }
        else if (next <= currentNodeIndex + k) {
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
  }
}
