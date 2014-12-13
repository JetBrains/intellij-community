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
import com.intellij.vcs.log.graph.impl.facade.BaseLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.CascadeLinearGraphController;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class LinearBekController extends CascadeLinearGraphController {
  @NotNull private final LinearGraph myCompiledGraph;

  public LinearBekController(@NotNull PermanentGraphInfo bekSortedGraph) {
    super(new BaseLinearGraphController(bekSortedGraph), bekSortedGraph);
    myCompiledGraph = compileGraph(getDelegateLinearGraphController().getCompiledGraph(), bekSortedGraph.getPermanentGraphLayout());
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

        int k = 1;
        PriorityQueue<Integer> queue = new PriorityQueue<Integer>();
        queue.addAll(workingGraph.getDownNodes(currentNodeIndex));
        while (!queue.isEmpty()) {
          Integer next = queue.poll();
          if (next > currentNodeIndex + k || !visited.get(next)) {
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

          for (int upNode : workingGraph.getUpNodes(i)) {
            if (upNode >= currentNodeIndex + k || upNode < currentNodeIndex - 1) {
              return;
            }
          }

          boolean isTail = true;
          for (int downNode : workingGraph.getDownNodes(i)) {
            if (!visited.get(downNode)) {
              int li = graphLayout.getLayoutIndex(downNode);
              if (li < x || li >= y) {
                return;
              }
              workingGraph.removeEdge(i, downNode);
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

  private static class WorkingGraph {
    private final GraphAdditionalEdges myHiddenEdges = createSimpleAdditionalEdges();
    private final GraphAdditionalEdges myDottedEdges = createSimpleAdditionalEdges();
    private final LinearGraph myGraph;

    private final List<GraphEdge> myToAdd = new ArrayList<GraphEdge>();
    private final List<GraphEdge> myToRemove = new ArrayList<GraphEdge>();
    private final List<GraphEdge> myDottedToRemove = new ArrayList<GraphEdge>();

    private WorkingGraph(LinearGraph graph) {
      myGraph = graph;
    }

    public void addEdge(int from, int to) {
      myToAdd.add(new GraphEdge(from, to, null, GraphEdgeType.DOTTED));
    }

    public void removeEdge(int from, int to) {
      if (myDottedEdges.hasEdge(from, to)) {
        myDottedToRemove.add(new GraphEdge(from, to, null, GraphEdgeType.DOTTED));
      } else {
        myToRemove.add(LinearGraphUtils.getEdge(myGraph, from, to));
      }
    }

    public List<Integer> getUpNodes(int index) {
      List<GraphEdge> edges = getGraphEdges(index);

      List<Integer> result = new ArrayList<Integer>();
      for (GraphEdge e : edges) {
        if (e.getUpNodeIndex() != index) {
          result.add(e.getUpNodeIndex());
        }
      }
      return result;
    }

    public List<Integer> getDownNodes(int index) {
      List<GraphEdge> edges = getGraphEdges(index);

      List<Integer> result = new ArrayList<Integer>();
      for (GraphEdge e : edges) {
        if (e.getDownNodeIndex() != index) {
          result.add(e.getDownNodeIndex());
        }
      }
      return result;
    }

    private List<GraphEdge> getGraphEdges(int index) {
      List<GraphEdge> edges = myGraph.getAdjacentEdges(index);
      myHiddenEdges.removeAdditionalEdges(edges, index);
      myDottedEdges.appendAdditionalEdges(edges, index);
      return edges;
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
}
