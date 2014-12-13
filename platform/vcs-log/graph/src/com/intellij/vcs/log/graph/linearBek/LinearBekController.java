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
import com.intellij.util.containers.IntStack;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.api.printer.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.collapsing.GraphAdditionalEdges;
import com.intellij.vcs.log.graph.impl.facade.BaseLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.CascadeLinearGraphController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

    GraphVisitorUtil graphVisitorUtil = new GraphVisitorUtil();
    final IntStack starts = new IntStack();

    graphVisitorUtil.visitGraph(graph, graphLayout, new GraphVisitorUtil.GraphVisitor() {
      @Override
      public void enterSubtree(int currentNode) {
        if (canBeStructureEnd(graph, currentNode)) {
          int start = -1;
          int currentNodeIndex = graphLayout.getLayoutIndex(currentNode);
          while (!starts.empty()) {
            int candidateStart = starts.peek();
            int candidateIndex = graphLayout.getLayoutIndex(candidateStart);

            if (currentNodeIndex <= candidateIndex) {
              starts.pop();
              if (currentNodeIndex == candidateIndex) {
                start = candidateStart;
                break;
              }
            }
            else {
              break;
            }
          }

          if (start != -1) {
            List<Integer> upNodes = getSortedUpNodes(graph, graphLayout, currentNode);
            int firstChildNode = getDownNodes(graph, start).get(0);
            if (graphLayout.getLayoutIndex(upNodes.get(0)) == graphLayout.getLayoutIndex(firstChildNode) &&
                graphLayout.getLayoutIndex(upNodes.get(1)) == graphLayout.getLayoutIndex(getDownNodes(graph, start).get(1))) {
              if (upNodes.get(0) == start) {
                // triangle
                missingEdges.createEdge(getEdge(graph, start, firstChildNode));
              } else {
                missingEdges.createEdge(getEdge(graph, start, firstChildNode));
                missingEdges.createEdge(getEdge(graph, upNodes.get(1), currentNode));
                newEdges.createEdge(new GraphEdge(upNodes.get(1), firstChildNode, null, GraphEdgeType.DOTTED));
              }
            }
          }
        }

        if (canBeStructureStart(graph, currentNode)) {
          starts.push(currentNode);
        }
      }

      @Override
      public void leaveSubtree(int currentNode) {
        if (canBeStructureStart(graph, currentNode)) {
          while (!starts.empty()) {
            int lastStart = starts.peek();
            if (lastStart >= currentNode){
              starts.pop();
              if (lastStart == currentNode) break;
            } else {
              break;
            }
          }
        }
      }
    });

    return new LinearBekGraph(graph, missingEdges, newEdges);
  }

  private static List<Integer> getSortedUpNodes(@NotNull final LinearGraph graph, @NotNull GraphLayout layout, int node) {
    List<Integer> upNodes = getUpNodes(graph, node);
    Collections.sort(upNodes, new LayoutComparator(layout));
    return upNodes;
  }

  private static boolean canBeStructureStart(@NotNull final LinearGraph graph, int node) {
    return getDownNodes(graph, node).size() == 2; // since order of getUpNodes is unclear to me, we only support collapsing 2 branches
  }

  private static boolean canBeStructureEnd(@NotNull final LinearGraph graph, int node) {
    return getUpNodes(graph, node).size() == 2;
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

  private static class LayoutComparator implements Comparator<Integer> {
    @NotNull private final GraphLayout myLayout;

    public LayoutComparator(@NotNull GraphLayout layout) {
      myLayout = layout;
    }

    @Override
    public int compare(Integer o1, Integer o2) {
      return myLayout.getLayoutIndex(o1) - myLayout.getLayoutIndex(o2);
    }
  }
}
