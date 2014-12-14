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
  @NotNull private final LinearGraph myCompiledGraph;

  public LinearBekController(@NotNull BekBaseLinearGraphController controller, @NotNull PermanentGraphInfo permanentGraphInfo) {
    super(controller, permanentGraphInfo);
    myCompiledGraph =
      compileGraph(getDelegateLinearGraphController().getCompiledGraph(),
                   new BekGraphLayout(permanentGraphInfo.getPermanentGraphLayout(), controller.getBekIntMap()));
  }

  static LinearGraph compileGraph(@NotNull final LinearGraph graph, @NotNull final GraphLayout graphLayout) {
    final WorkingGraph workingGraph = new WorkingGraph(graph);

    GraphVisitorAlgorithm graphVisitorAlgorithm = new GraphVisitorAlgorithm(true);

    graphVisitorAlgorithm.visitGraph(graph, graphLayout, new GraphVisitorAlgorithm.GraphVisitor() {
      @Override
      public void enterSubtree(int currentNodeIndex, BitSetFlags visited) {
      }

      @Override
      public void leaveSubtree(int currentNodeIndex, BitSetFlags visited) {
        List<Integer> upNodes = workingGraph.getUpNodes(currentNodeIndex);
        if (upNodes.size() != 1) return;
        int parent = upNodes.get(0);
        if (workingGraph.getDownNodes(parent).size() != 2) {
          return;
        }
        int firstChildIndex = workingGraph.getDownNodes(parent).get(0);
        if (firstChildIndex == currentNodeIndex) return;

        int x = graphLayout.getLayoutIndex(firstChildIndex);
        int y = graphLayout.getLayoutIndex(currentNodeIndex);

        final Map<Integer, Integer> magicMap = new HashMap<Integer, Integer>();
        new GraphVisitorAlgorithm(false).visitSubgraph(workingGraph, new GraphVisitorAlgorithm.SimpleVisitor() {
          @Override
          public void visitNode(int nodeIndex) {
            int layoutIndex = graphLayout.getLayoutIndex(nodeIndex);
            if (!magicMap.containsKey(layoutIndex) || magicMap.get(layoutIndex) > nodeIndex) {
              magicMap.put(layoutIndex, nodeIndex);
            }
          }
        }, firstChildIndex, 100);

        int k = 1;
        PriorityQueue<Integer> queue = new PriorityQueue<Integer>();
        queue.addAll(workingGraph.getDownNodes(currentNodeIndex));
        while (!queue.isEmpty()) {
          Integer next = queue.poll();
          if (next == firstChildIndex) {
            break;
          }
          else if (next > currentNodeIndex + k || !visited.get(next)) {
            break;
          }
          else if (next < currentNodeIndex + k) {
            continue;
          }
          k++;
          queue.addAll(workingGraph.getDownNodes(next));
        }

        workingGraph.clear();
        for (int i = currentNodeIndex; i < currentNodeIndex + k; i++) {
          boolean isTail = true;
          for (int downNode : workingGraph.getDownNodes(i)) {
            if (!visited.get(downNode)) {
              int li = graphLayout.getLayoutIndex(downNode);
              if (li >= y) {
                return;
              }
              if (li < x) {
                // magic map will save us here
                if (!magicMap.containsKey(li) || magicMap.get(li) > downNode) {
                  return;
                }
              }
              workingGraph.removeEdge(i, downNode);
            }
            else if (downNode > currentNodeIndex + k) {
              return; // settling case 2450 as "not collapsing at all"
            }
            else {
              isTail = false;
            }
          }

          if (isTail) {
            workingGraph.addEdge(i, firstChildIndex);
          }
        }

        workingGraph.removeEdge(parent, firstChildIndex);

        workingGraph.apply();
      }
    });

    return workingGraph.createLinearBekGraph();
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
}
