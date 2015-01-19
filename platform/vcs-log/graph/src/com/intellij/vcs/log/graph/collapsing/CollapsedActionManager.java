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
package com.intellij.vcs.log.graph.collapsing;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.printer.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.impl.facade.GraphChanges;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController.LinearGraphAction;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController.LinearGraphAnswer;
import com.intellij.vcs.log.graph.impl.visible.LinearFragmentGenerator;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class CollapsedActionManager {

  private CollapsedActionManager() {}

  private interface ActionCase {
    @Nullable LinearGraphAnswer performAction(
      @NotNull CollapsedLinearGraphController graphController,
      @NotNull LinearGraphAction action
    );
  }

  @Nullable
  public static LinearGraphAnswer performAction(
    @NotNull CollapsedLinearGraphController graphController,
    @NotNull LinearGraphAction action
  ) {
    for (ActionCase actionCase : FILTER_ACTION_CASES) {
      LinearGraphAnswer graphAnswer = actionCase.performAction(graphController, action);
      if (graphAnswer != null)
        return graphAnswer;
    }
    return null;
  }

  @Nullable
  private static GraphEdge getDottedEdge(@Nullable PrintElementWithGraphElement element, @NotNull LinearGraph graph) {
    if (element == null) return null;
    GraphElement graphElement = element.getGraphElement();

    if (graphElement instanceof GraphEdge && ((GraphEdge)graphElement).getType() == GraphEdgeType.DOTTED) return (GraphEdge)graphElement;
    if (graphElement instanceof GraphNode) {
      GraphNode node = (GraphNode)graphElement;
      for (GraphEdge edge : graph.getAdjacentEdges(node.getNodeIndex())) {
        if (edge.getType() == GraphEdgeType.DOTTED) {
          return edge;
        }
      }
    }

    return null;
  }

  private static LinearGraphAnswer clearHover(@NotNull CollapsedLinearGraphController graphController) {
    graphController.setSelectedNodes(Collections.<Integer>emptySet());
    return LinearGraphUtils.createCursorAnswer(false); // todo check performance
  }

  private final static ActionCase HOVER_CASE = new ActionCase() {
    @Nullable
    @Override
    public LinearGraphAnswer performAction(@NotNull CollapsedLinearGraphController graphController, @NotNull LinearGraphAction action) {
      if (action.getType() != GraphAction.Type.MOUSE_OVER) return null;

      GraphEdge dottedEdge = getDottedEdge(action.getAffectedElement(), graphController.getCompiledGraph());
      if (dottedEdge != null) {
        graphController.setSelectedElements(ContainerUtil.<GraphElement>set(dottedEdge));
        return LinearGraphUtils.createCursorAnswer(true);
      }

      if (action.getAffectedElement() == null) return clearHover(graphController);

      GraphElement element = action.getAffectedElement().getGraphElement();
      LinearFragmentGenerator.GraphFragment fragment = graphController.getLinearFragmentGenerator().getPartLongFragment(element);
      if (fragment != null) {
        Set<Integer> middleNodes = graphController.getFragmentGenerator().getMiddleNodes(fragment.upNodeIndex, fragment.downNodeIndex);
        graphController.setSelectedNodes(middleNodes);
        return LinearGraphUtils.createCursorAnswer(true);
      }

      return clearHover(graphController);
    }
  };

  private final static GraphChanges<Integer> SOME_CHANGES = new GraphChanges<Integer>() {
    @NotNull
    @Override
    public Collection<Node<Integer>> getChangedNodes() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public Collection<Edge<Integer>> getChangedEdges() {
      return Collections.emptyList();
    }
  };

  private final static ActionCase LINEAR_COLLAPSE_CASE = new ActionCase() {
    @Nullable
    @Override
    public LinearGraphAnswer performAction(@NotNull final CollapsedLinearGraphController graphController, @NotNull LinearGraphAction action) {
      if (action.getType() != GraphAction.Type.MOUSE_CLICK || action.getAffectedElement() == null) return null;
      GraphElement graphElement = action.getAffectedElement().getGraphElement();

      LinearFragmentGenerator.GraphFragment longFragment = graphController.getLinearFragmentGenerator().getLongFragment(graphElement);
      if (longFragment != null) {
        Set<Integer> nodesForHide =
          graphController.getFragmentGenerator().getMiddleNodes(longFragment.upNodeIndex, longFragment.downNodeIndex);
        nodesForHide.remove(longFragment.upNodeIndex);
        nodesForHide.remove(longFragment.downNodeIndex);

        CollapsedGraph collapsedGraph = graphController.getCollapsedGraph();
        final LinearGraph compiledGraph = collapsedGraph.getCompiledGraph();
        int upNodeId = compiledGraph.getNodeId(longFragment.upNodeIndex);
        int downNodeId = compiledGraph.getNodeId(longFragment.downNodeIndex);

        final List<Integer> nodeIdList = ContainerUtil.map(nodesForHide, new Function<Integer, Integer>() {
          @Override
          public Integer fun(Integer integer) {
            return compiledGraph.getNodeId(integer);
          }
        });
        for (Integer nodeId : nodeIdList) {
          collapsedGraph.setNodeVisibility(nodeId, false);
        }
        collapsedGraph.getGraphAdditionalEdges().createEdge(upNodeId, downNodeId, GraphEdgeType.DOTTED);


        collapsedGraph.updateNodeMapping(collapsedGraph.getDelegateGraph().getNodeIndexById(upNodeId),
                                         collapsedGraph.getDelegateGraph().getNodeIndexById(downNodeId));
        return new LinearGraphAnswer(SOME_CHANGES, null, null); // todo fix
      }

      return null;
    }
  };

  private final static ActionCase EXPAND_ALL = new ActionCase() {
    @Nullable
    @Override
    public LinearGraphAnswer performAction(@NotNull CollapsedLinearGraphController graphController, @NotNull LinearGraphAction action) {
      if (action.getType() != GraphAction.Type.BUTTON_EXPAND) return null;
      CollapsedGraph collapsedGraph = graphController.getCollapsedGraph();
      LinearGraph delegateGraph = collapsedGraph.getDelegateGraph();

      for (int nodeIndex = 0; nodeIndex < delegateGraph.nodesCount(); nodeIndex++) {
        int nodeId = delegateGraph.getNodeId(nodeIndex);
        collapsedGraph.setNodeVisibility(nodeId, true);
      }
      collapsedGraph.getGraphAdditionalEdges().removeAll();
      collapsedGraph.updateNodeMapping(0, delegateGraph.nodesCount() - 1);

      return new LinearGraphAnswer(SOME_CHANGES, null, null); // todo fix
    }
  };

  private final static ActionCase COLLAPSE_ALL = new ActionCase() { // todo
    @Nullable
    @Override
    public LinearGraphAnswer performAction(@NotNull CollapsedLinearGraphController graphController, @NotNull LinearGraphAction action) {
      if (action.getType() != GraphAction.Type.BUTTON_COLLAPSE) return null;

      CollapsedGraph collapsedGraph = graphController.getCollapsedGraph();
      LinearGraph delegateGraph = collapsedGraph.getDelegateGraph();
      LinearFragmentGenerator generator = new LinearFragmentGenerator(delegateGraph, Condition.FALSE);
      FragmentGenerator fragmentGenerator = new FragmentGenerator(LinearGraphUtils.asLiteLinearGraph(delegateGraph), Condition.FALSE);
      for (int nodeIndex = 0; nodeIndex < delegateGraph.nodesCount(); nodeIndex++) {
        int nodeId1 = delegateGraph.getNodeId(nodeIndex);
        if (!collapsedGraph.getNodeVisibility(nodeId1)) continue;

        LinearFragmentGenerator.GraphFragment fragment = generator.getLongDownFragment(nodeIndex);
        if (fragment != null) {
          Set<Integer> middleNodes = fragmentGenerator.getMiddleNodes(fragment.upNodeIndex, fragment.downNodeIndex);
          middleNodes.remove(fragment.upNodeIndex);
          middleNodes.remove(fragment.downNodeIndex);
          int upNodeId = delegateGraph.getNodeId(fragment.upNodeIndex);
          int downNodeId = delegateGraph.getNodeId(fragment.downNodeIndex);
          collapsedGraph.getGraphAdditionalEdges().createEdge(upNodeId, downNodeId, GraphEdgeType.DOTTED);

          for (Integer nodeIndexForHide : middleNodes) {
            int nodeId = delegateGraph.getNodeId(nodeIndexForHide);
            collapsedGraph.setNodeVisibility(nodeId, false);
          }
        }
      }
      collapsedGraph.updateNodeMapping(0, delegateGraph.nodesCount() - 1);

      return new LinearGraphAnswer(SOME_CHANGES, null, null);
    }
  };

  private final static ActionCase LINEAR_EXPAND_CASE = new ActionCase() {
    @Nullable
    @Override
    public LinearGraphAnswer performAction(@NotNull final CollapsedLinearGraphController graphController,
                                           @NotNull LinearGraphAction action) {
      if (action.getType() != GraphAction.Type.MOUSE_CLICK) return null;

      Pair<Integer, Integer>
        dottedEdge = LinearGraphUtils.asNormalEdge(getDottedEdge(action.getAffectedElement(), graphController.getCompiledGraph()));
      if (dottedEdge != null) {
        final CollapsedGraph collapsedGraph = graphController.getCollapsedGraph();
        LinearGraph delegateGraph = collapsedGraph.getDelegateGraph();
        FragmentGenerator fragmentGenerator =
          new FragmentGenerator(LinearGraphUtils.asLiteLinearGraph(delegateGraph),
                                Condition.FALSE);
        int upNodeId = graphController.getCompiledGraph().getNodeId(dottedEdge.first);
        int downNodeId = graphController.getCompiledGraph().getNodeId(dottedEdge.second);
        Integer upNodeIndex = delegateGraph.getNodeIndexById(upNodeId);
        Integer downNodeIndex = delegateGraph.getNodeIndexById(downNodeId);
        Set<Integer> middleNodes =
          fragmentGenerator.getMiddleNodes(upNodeIndex, downNodeIndex);

        List<Integer> nodeIdList = ContainerUtil.map(middleNodes, new Function<Integer, Integer>() {
          @Override
          public Integer fun(Integer integer) {
            return collapsedGraph.getDelegateGraph().getNodeId(integer);
          }
        });

        collapsedGraph.getGraphAdditionalEdges().removeEdge(upNodeId, downNodeId, GraphEdgeType.DOTTED);
        for (Integer nodId : nodeIdList) {
          collapsedGraph.setNodeVisibility(nodId, true);
        }

        collapsedGraph.updateNodeMapping(upNodeIndex, downNodeIndex);
        return new LinearGraphAnswer(SOME_CHANGES, null, null); // todo
      }

      return null;
    }
  };

  private final static List<ActionCase> FILTER_ACTION_CASES =
    ContainerUtil.list(COLLAPSE_ALL, EXPAND_ALL, HOVER_CASE, LINEAR_COLLAPSE_CASE, LINEAR_EXPAND_CASE);

}
