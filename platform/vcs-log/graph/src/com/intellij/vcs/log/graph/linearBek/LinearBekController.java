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
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.facade.BekBaseLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.CascadeLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.GraphChanges;
import com.intellij.vcs.log.graph.impl.facade.bek.BekIntMap;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class LinearBekController extends CascadeLinearGraphController {
  @NotNull private final LinearBekGraph myCompiledGraph;
  private final BekIntMap myBekIntMap;
  private final BekGraphLayout myBekGraphLayout;

  public LinearBekController(@NotNull BekBaseLinearGraphController controller, @NotNull PermanentGraphInfo permanentGraphInfo) {
    super(controller, permanentGraphInfo);
    myBekIntMap = controller.getBekIntMap();
    myBekGraphLayout = new BekGraphLayout(permanentGraphInfo.getPermanentGraphLayout(), myBekIntMap);
    myCompiledGraph = compileGraph(getDelegateLinearGraphController().getCompiledGraph(), myBekGraphLayout);
  }

  static LinearBekGraph compileGraph(@NotNull LinearGraph graph, @NotNull GraphLayout graphLayout) {
    long start = System.currentTimeMillis();

    LinearBekGraph linearBekGraph = new LinearBekGraph(graph);
    new LinearBekGraphBuilder(linearBekGraph, graphLayout).collapseAll();

    System.err.println((System.currentTimeMillis() - start) / 1000.0 + " sec");

    return linearBekGraph;
  }

  @NotNull
  @Override
  protected LinearGraphAnswer delegateGraphChanged(@NotNull LinearGraphAnswer delegateAnswer) {
    return delegateAnswer;
  }

  @Nullable
  @Override
  protected LinearGraphAnswer performAction(@NotNull LinearGraphAction action) {
    if (action.getAffectedElement() != null) {
      if (action.getType() == GraphAction.Type.MOUSE_CLICK) {
        GraphElement graphElement = action.getAffectedElement().getGraphElement();
        if (graphElement instanceof GraphEdge) {
          GraphEdge edge = (GraphEdge)graphElement;
          if (edge.getType() == GraphEdgeType.DOTTED) {
            return new LinearGraphAnswer(replacedEdges(Collections.singleton(edge), myCompiledGraph.expandEdge(edge),
                                                       getDelegateLinearGraphController().getCompiledGraph()), null, null, null);
          }
        }
        else if (graphElement instanceof GraphNode) {
          if (new LinearBekGraphBuilder(myCompiledGraph, myBekGraphLayout).collapseFragment(((GraphNode)graphElement).getNodeIndex())) {
            return new LinearGraphAnswer(GraphChanges.SOME_CHANGES, null, null, null);
          }
        }
      }
      else if (action.getType() == GraphAction.Type.MOUSE_OVER) {
        GraphElement graphElement = action.getAffectedElement().getGraphElement();
        if (graphElement instanceof GraphEdge) {
          GraphEdge edge = (GraphEdge)graphElement;
          if (edge.getType() == GraphEdgeType.DOTTED) {
            return LinearGraphUtils
              .createSelectedAnswer(myCompiledGraph, ContainerUtil.set(edge.getUpNodeIndex(), edge.getDownNodeIndex()));
          }
        }
        else if (graphElement instanceof GraphNode) {
          LinearBekGraphBuilder.MergeFragment fragment =
            new LinearBekGraphBuilder(myCompiledGraph, myBekGraphLayout).getFragment(((GraphNode)graphElement).getNodeIndex());
          if (fragment != null) {
            return LinearGraphUtils.createSelectedAnswer(myCompiledGraph, fragment.getAllNodes());
          }
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public LinearGraph getCompiledGraph() {
    return myCompiledGraph;
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

  private static GraphChanges<Integer> replacedEdges(Collection<GraphEdge> removedEdges,
                                                     Collection<GraphEdge> addedEdges,
                                                     LinearGraph delegateGraph) {
    final Set<GraphChanges.Edge<Integer>> edgeChanges = ContainerUtil.newHashSet();

    for (GraphEdge edge : removedEdges) {
      edgeChanges.add(
        new GraphChanges.EdgeImpl<Integer>(delegateGraph.getNodeId(edge.getUpNodeIndex()), delegateGraph.getNodeId(edge.getDownNodeIndex()),
                                           true));
    }

    for (GraphEdge edge : addedEdges) {
      edgeChanges.add(
        new GraphChanges.EdgeImpl<Integer>(delegateGraph.getNodeId(edge.getUpNodeIndex()), delegateGraph.getNodeId(edge.getDownNodeIndex()),
                                           false));
    }

    return new GraphChanges.GraphChangesImpl<Integer>(Collections.<GraphChanges.Node<Integer>>emptySet(), edgeChanges);
  }
}
