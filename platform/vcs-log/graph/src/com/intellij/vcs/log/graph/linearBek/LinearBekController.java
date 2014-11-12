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
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.*;

public class LinearBekController extends CascadeLinearGraphController {
  @NotNull private final LinearGraph myCompiledGraph;

  public LinearBekController(@NotNull PermanentGraphInfo bekSortedGraph) {
    super(new BaseLinearGraphController(bekSortedGraph), bekSortedGraph);
    myCompiledGraph = compileGraph(getDelegateLinearGraphController().getCompiledGraph(), bekSortedGraph.getPermanentGraphLayout());
  }

  static LinearGraph compileGraph(@NotNull final LinearGraph graph, @NotNull final GraphLayout graphLayout) {
    final GraphAdditionalEdges missingEdges = createSimpleAdditionalEdges();
    final GraphAdditionalEdges newEdges = createSimpleAdditionalEdges();

    GraphVisitorAlgorithm graphVisitorAlgorithm = new GraphVisitorAlgorithm(true);

    graphVisitorAlgorithm.visitGraph(graph, graphLayout, new GraphVisitorAlgorithm.GraphVisitor() {
      @Override
      public void enterSubtree(int currentNodeIndex, BitSetFlags visited) {
      }

      @Override
      public void leaveSubtree(int currentNodeIndex, BitSetFlags visited) {
        List<Integer> upNodes = getUpNodes(graph, currentNodeIndex);
        if (upNodes.size() != 1) return;
        int parent = upNodes.get(0);
        if (getDownNodes(graph, parent).size() != 2) {
          return;
        }
        int firstChildIndex = getDownNodes(graph, parent).get(0);
        if (firstChildIndex == currentNodeIndex) return;

        int x = graphLayout.getLayoutIndex(firstChildIndex);
        int y = graphLayout.getLayoutIndex(currentNodeIndex);

        int k = 1;
        PriorityQueue<Integer> queue = new PriorityQueue<Integer>();
        queue.addAll(getDownNodes(graph, currentNodeIndex));
        while (!queue.isEmpty()) {
          Integer next = queue.poll();
          if (next > currentNodeIndex + k || !visited.get(next)) {
            break;
          } else if (next < currentNodeIndex + k) {
            continue;
          }
          k++;
          queue.addAll(getDownNodes(graph, next));
        }

        List<GraphEdge> edgesToRemove = new ArrayList<GraphEdge>();
        List<GraphEdge> dottedEdgesToRemove = new ArrayList<GraphEdge>();
        List<Integer> tails = new ArrayList<Integer>();

        for (int i = currentNodeIndex; i < currentNodeIndex + k; i++) {
          boolean isTail = true;

          for (int upNode : getUpNodes(graph, i)) {
            if (upNode >= currentNodeIndex + k || upNode < currentNodeIndex - 1) {
              return;
            }
          }

          for (int downNode : getDownNodes(graph, i)) {
            if (!visited.get(downNode)) {
              int li = graphLayout.getLayoutIndex(downNode);
              if (li < x || li >= y) {
                return;
              }
              edgesToRemove.add(getEdge(graph, i, downNode));
            }
            else {
              isTail = false;
            }
          }
          if (isTail) {
            tails.add(i);
          }

          ArrayList<GraphEdge> dottedEdges = new ArrayList<GraphEdge>();
          newEdges.appendAdditionalEdges(dottedEdges, i);
          for (GraphEdge dottedEdge : dottedEdges) {
            if (dottedEdge.getUpNodeIndex() == i) {
              int li = graphLayout.getLayoutIndex(dottedEdge.getDownNodeIndex());
              if (li >= x && li < y) {
                dottedEdgesToRemove.add(dottedEdge);
              }
            }
          }
        }

        // replace edges!
        missingEdges.createEdge(getEdge(graph, parent, firstChildIndex));
        for (Integer t : tails) {
          newEdges.createEdge(new GraphEdge(t, firstChildIndex, null, GraphEdgeType.DOTTED));
        }

        for (GraphEdge edge : edgesToRemove) {
          missingEdges.createEdge(edge);
        }
        for (GraphEdge edge : dottedEdgesToRemove) {
          newEdges.removeEdge(edge);
        }
      }
    });

    return new LinearBekGraph(graph, missingEdges, newEdges);
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
