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
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.facade.GraphChanges;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController.LinearGraphAction;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController.LinearGraphAnswer;
import com.intellij.vcs.log.graph.impl.visible.LinearFragmentGenerator;
import com.intellij.vcs.log.graph.impl.visible.LinearFragmentGenerator.GraphFragment;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getCursor;

class CollapsedActionManager {

  @Nullable
  public static LinearGraphAnswer performAction(
    @NotNull CollapsedLinearGraphController graphController,
    @NotNull LinearGraphAction action
  ) {
    ActionContext context = new ActionContext(graphController.getCollapsedGraph(), graphController.getPermanentGraphInfo(), action);

    for (ActionCase actionCase : FILTER_ACTION_CASES) {
      if (actionCase.supportedActionTypes().contains(context.getActionType())) {
        LinearGraphAnswer graphAnswer = actionCase.performAction(context);
        if (graphAnswer != null)
          return graphAnswer;
      }
    }
    return null;
  }


  private interface ActionCase {
    @Nullable LinearGraphAnswer performAction(@NotNull ActionContext context);
    @NotNull Set<GraphAction.Type> supportedActionTypes();
  }

  private static class ActionContext {
    @NotNull private final CollapsedGraph myCollapsedGraph;
    @NotNull private final LinearGraphAction myGraphAction;
    @NotNull private final FragmentGenerators myDelegatedFragmentGenerators;
    @NotNull private final FragmentGenerators myCompiledFragmentGenerators;

    private ActionContext(@NotNull CollapsedGraph collapsedGraph,
                          @NotNull PermanentGraphInfo permanentGraphInfo,
                          @NotNull LinearGraphAction graphAction
    ) {
      myCollapsedGraph = collapsedGraph;
      myGraphAction = graphAction;
      myDelegatedFragmentGenerators = new FragmentGenerators(collapsedGraph.getDelegatedGraph(), permanentGraphInfo);
      myCompiledFragmentGenerators = new FragmentGenerators(collapsedGraph.getCompiledGraph(), permanentGraphInfo);
    }

    @NotNull
    GraphAction.Type getActionType() {
      return myGraphAction.getType();
    }

    @Nullable
    GraphElement getAffectedGraphElement() {
      return myGraphAction.getAffectedElement() == null ? null : myGraphAction.getAffectedElement().getGraphElement();
    }

    @NotNull
    LinearGraph getDelegatedGraph() {
      return myCollapsedGraph.getDelegatedGraph();
    }

    @NotNull
    LinearGraph getCompiledGraph() {
      return myCollapsedGraph.getCompiledGraph();
    }

    int convertToDelegateNodeIndex(int compiledNodeIndex) {
      return myCollapsedGraph.convertToDelegateNodeIndex(compiledNodeIndex);
    }

    @NotNull
    Set<Integer> convertToDelegateNodeIndex(@NotNull Collection<Integer> compiledNodeIndexes) {
      return ContainerUtil.map2Set(compiledNodeIndexes, new Function<Integer, Integer>() {
        @Override
        public Integer fun(Integer nodeIndex) {
          return convertToDelegateNodeIndex(nodeIndex);
        }
      });
    }

    @NotNull
    GraphEdge convertToDelegateEdge(@NotNull GraphEdge compiledEdge) {
      Integer upNodeIndex = null, downNodeIndex = null;
      if (compiledEdge.getUpNodeIndex() != null) upNodeIndex = convertToDelegateNodeIndex(compiledEdge.getUpNodeIndex());
      if (compiledEdge.getDownNodeIndex() != null) downNodeIndex = convertToDelegateNodeIndex(compiledEdge.getDownNodeIndex());

      return new GraphEdge(upNodeIndex, downNodeIndex, compiledEdge.getTargetId(), compiledEdge.getType());
    }

  }

  private static class FragmentGenerators {
    @NotNull private final FragmentGenerator fragmentGenerator;
    @NotNull private final LinearFragmentGenerator linearFragmentGenerator;

    private FragmentGenerators(@NotNull LinearGraph linearGraph, @NotNull PermanentGraphInfo<?> permanentGraphInfo) {
      fragmentGenerator = new FragmentGenerator(LinearGraphUtils.asLiteLinearGraph(linearGraph), Condition.FALSE);

      Set<Integer> branchNodeIndexes = LinearGraphUtils.convertIdsToNodeIndexes(linearGraph, permanentGraphInfo.getBranchNodeIds());
      linearFragmentGenerator = new LinearFragmentGenerator(LinearGraphUtils.asLiteLinearGraph(linearGraph), branchNodeIndexes);
    }
  }

  private static LinearGraphAnswer createSelectedAnswer(@NotNull LinearGraph linearGraph, Collection<Integer> selectedNodeIndexes) {
    Set<Integer> selectedId = ContainerUtil.newHashSet();
    for (Integer nodeIndex : selectedNodeIndexes) {
      if (nodeIndex == null) continue;
      selectedId.add(linearGraph.getNodeId(nodeIndex));
    }
    return new LinearGraphAnswer(null, getCursor(true), null, selectedId);
  }

  private final static ActionCase CLEAR_HOVER = new ActionCase() {
    @Nullable
    @Override
    public LinearGraphAnswer performAction(@NotNull ActionContext context) {
      return new LinearGraphAnswer(null, getCursor(false), null, Collections.<Integer>emptySet());
    }

    @NotNull
    @Override
    public Set<GraphAction.Type> supportedActionTypes() {
      return ContainerUtil.set(GraphAction.Type.MOUSE_OVER);
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
    public LinearGraphAnswer performAction(@NotNull final ActionContext context) {
      if (context.getAffectedGraphElement() == null) return null;
      LinearFragmentGenerator compiledLinearFragmentGenerator = context.myCompiledFragmentGenerators.linearFragmentGenerator;
      FragmentGenerator compiledFragmentGenerator = context.myCompiledFragmentGenerators.fragmentGenerator;

      if (context.getActionType() == GraphAction.Type.MOUSE_OVER) {
        GraphFragment fragment = compiledLinearFragmentGenerator.getPartLongFragment(context.getAffectedGraphElement());
        if (fragment == null) return null;
        Set<Integer> middleCompiledNodes = compiledFragmentGenerator.getMiddleNodes(fragment.upNodeIndex, fragment.downNodeIndex, false);
        return createSelectedAnswer(context.getCompiledGraph(), middleCompiledNodes);
      }

      GraphFragment fragment = compiledLinearFragmentGenerator.getLongFragment(context.getAffectedGraphElement());
      if (fragment == null) return null;

      Set<Integer> middleCompiledNodes = compiledFragmentGenerator.getMiddleNodes(fragment.upNodeIndex, fragment.downNodeIndex, true);
      Set<GraphEdge> dottedCompiledEdges = ContainerUtil.newHashSet();
      for (Integer middleNodeIndex: middleCompiledNodes) {
        dottedCompiledEdges.addAll(ContainerUtil.filter(context.getCompiledGraph().getAdjacentEdges(middleNodeIndex, EdgeFilter.NORMAL_ALL),
                                                        new Condition<GraphEdge>() {
                                                          @Override
                                                          public boolean value(GraphEdge edge) {
                                                            return edge.getType() == GraphEdgeType.DOTTED;
                                                          }
                                                        }));
      }

      int upNodeIndex = context.convertToDelegateNodeIndex(fragment.upNodeIndex);
      int downNodeIndex = context.convertToDelegateNodeIndex(fragment.downNodeIndex);
      Set<Integer> middleNodes = context.convertToDelegateNodeIndex(middleCompiledNodes);
      Set<GraphEdge> dottedEdges = ContainerUtil.map2Set(dottedCompiledEdges, new Function<GraphEdge, GraphEdge>() {
        @Override
        public GraphEdge fun(GraphEdge edge) {
          return context.convertToDelegateEdge(edge);
        }
      });

      CollapsedGraph.Modification modification = context.myCollapsedGraph.startModification();
      for (GraphEdge dottedEdge : dottedEdges) modification.removeEdge(dottedEdge);
      for (Integer middleNode : middleNodes) modification.hideNode(middleNode);
      modification.createEdge(new GraphEdge(upNodeIndex, downNodeIndex, null, GraphEdgeType.DOTTED));

      modification.apply();
      return new LinearGraphAnswer(SOME_CHANGES, null, null, null); // todo fix
    }

    @NotNull
    @Override
    public Set<GraphAction.Type> supportedActionTypes() {
      return ContainerUtil.set(GraphAction.Type.MOUSE_CLICK, GraphAction.Type.MOUSE_OVER);
    }
  };

  private final static ActionCase EXPAND_ALL = new ActionCase() {
    @Nullable
    @Override
    public LinearGraphAnswer performAction(@NotNull ActionContext context) {
      CollapsedGraph collapsedGraph = context.myCollapsedGraph;
      LinearGraph delegateGraph = collapsedGraph.getDelegatedGraph();

      for (int nodeIndex = 0; nodeIndex < delegateGraph.nodesCount(); nodeIndex++) {
        int nodeId = delegateGraph.getNodeId(nodeIndex);
        collapsedGraph.setNodeVisibility(nodeId, true);
      }
      collapsedGraph.getEdgeStorage().removeAll();
      collapsedGraph.updateNodeMapping(0, delegateGraph.nodesCount() - 1);

      return new LinearGraphAnswer(SOME_CHANGES, null, null, null); // todo fix
    }

    @NotNull
    @Override
    public Set<GraphAction.Type> supportedActionTypes() {
      return Collections.singleton(GraphAction.Type.BUTTON_EXPAND);
    }
  };

  private final static ActionCase COLLAPSE_ALL = new ActionCase() { // todo
    @Nullable
    @Override
    public LinearGraphAnswer performAction(@NotNull ActionContext context) {
      CollapsedGraph collapsedGraph = context.myCollapsedGraph;
      LinearGraph delegateGraph = collapsedGraph.getDelegatedGraph();
      LinearFragmentGenerator generator = new LinearFragmentGenerator(LinearGraphUtils.asLiteLinearGraph(delegateGraph), Collections.<Integer>emptySet());
      FragmentGenerator fragmentGenerator = new FragmentGenerator(LinearGraphUtils.asLiteLinearGraph(delegateGraph), Condition.FALSE);
      for (int nodeIndex = 0; nodeIndex < delegateGraph.nodesCount(); nodeIndex++) {
        if (!collapsedGraph.isNodeVisible(nodeIndex)) continue;

        GraphFragment fragment = generator.getLongDownFragment(nodeIndex);
        if (fragment != null) {
          Set<Integer> middleNodes = fragmentGenerator.getMiddleNodes(fragment.upNodeIndex, fragment.downNodeIndex, true);
          int upNodeId = delegateGraph.getNodeId(fragment.upNodeIndex);
          int downNodeId = delegateGraph.getNodeId(fragment.downNodeIndex);
          collapsedGraph.getEdgeStorage().createEdge(upNodeId, downNodeId, GraphEdgeType.DOTTED);

          for (Integer nodeIndexForHide : middleNodes) {
            int nodeId = delegateGraph.getNodeId(nodeIndexForHide);
            collapsedGraph.setNodeVisibility(nodeId, false);
          }
        }
      }
      collapsedGraph.updateNodeMapping(0, delegateGraph.nodesCount() - 1);

      return new LinearGraphAnswer(SOME_CHANGES, null, null, null);
    }

    @NotNull
    @Override
    public Set<GraphAction.Type> supportedActionTypes() {
      return Collections.singleton(GraphAction.Type.BUTTON_COLLAPSE);
    }
  };

  private final static ActionCase LINEAR_EXPAND_CASE = new ActionCase() {
    @Nullable
    @Override
    public LinearGraphAnswer performAction(@NotNull ActionContext context) {
      GraphEdge dottedEdge = getDottedEdge(context.getAffectedGraphElement(), context.getCompiledGraph());

      if (dottedEdge != null) {
        int upNodeIndex = context.convertToDelegateNodeIndex(assertInt(dottedEdge.getUpNodeIndex()));
        int downNodeIndex = context.convertToDelegateNodeIndex(assertInt(dottedEdge.getDownNodeIndex()));

        if (context.getActionType() == GraphAction.Type.MOUSE_OVER) {
          return createSelectedAnswer(context.getDelegatedGraph(), ContainerUtil.set(upNodeIndex, downNodeIndex));
        }

        Set<Integer> middleNodes = context.myDelegatedFragmentGenerators.fragmentGenerator.getMiddleNodes(upNodeIndex, downNodeIndex, true);

        CollapsedGraph.Modification modification = context.myCollapsedGraph.startModification();
        for (Integer middleNode : middleNodes) {
          modification.showNode(middleNode);
        }
        modification.removeEdge(new GraphEdge(upNodeIndex, downNodeIndex, null, GraphEdgeType.DOTTED));

        modification.apply();
        return new LinearGraphAnswer(SOME_CHANGES, null, null, null); // todo
      }

      return null;
    }

    @NotNull
    @Override
    public Set<GraphAction.Type> supportedActionTypes() {
      return ContainerUtil.set(GraphAction.Type.MOUSE_CLICK, GraphAction.Type.MOUSE_OVER);
    }
  };

  private final static List<ActionCase> FILTER_ACTION_CASES =
    ContainerUtil.list(COLLAPSE_ALL, EXPAND_ALL, LINEAR_EXPAND_CASE, LINEAR_COLLAPSE_CASE, CLEAR_HOVER);

  private CollapsedActionManager() {}

  private static int assertInt(@Nullable Integer value) {
    assert value != null;
    return value;
  }

  @Nullable
  private static GraphEdge getDottedEdge(@Nullable GraphElement graphElement, @NotNull LinearGraph graph) {
    if (graphElement == null) return null;

    if (graphElement instanceof GraphEdge && ((GraphEdge)graphElement).getType() == GraphEdgeType.DOTTED) return (GraphEdge)graphElement;
    if (graphElement instanceof GraphNode) {
      GraphNode node = (GraphNode)graphElement;
      for (GraphEdge edge : graph.getAdjacentEdges(node.getNodeIndex(), EdgeFilter.NORMAL_ALL)) {
        if (edge.getType() == GraphEdgeType.DOTTED) return edge;
      }
    }

    return null;
  }
}
