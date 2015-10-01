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

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.facade.CascadeController;
import com.intellij.vcs.log.graph.impl.facade.GraphChanges;
import com.intellij.vcs.log.graph.impl.facade.ReachableNodes;
import com.intellij.vcs.log.graph.utils.UnsignedBitSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class CollapsedController extends CascadeController {
  @NotNull private CollapsedGraph myCollapsedGraph;

  public CollapsedController(@NotNull CascadeController delegateLinearGraphController,
                             @NotNull final PermanentGraphInfo<?> permanentGraphInfo,
                             @Nullable Set<Integer> idsOfVisibleBranches) {
    super(delegateLinearGraphController, permanentGraphInfo);
    UnsignedBitSet initVisibility =
      ReachableNodes.getReachableNodes(permanentGraphInfo.getPermanentLinearGraph(), idsOfVisibleBranches);
    myCollapsedGraph = CollapsedGraph.newInstance(getDelegateController().getCompiledGraph(), initVisibility);
  }

  @NotNull
  @Override
  protected LinearGraphAnswer delegateGraphChanged(@NotNull LinearGraphAnswer delegateAnswer) {
    if (delegateAnswer.getGraphChanges() != null) {
      LinearGraph delegateGraph = getDelegateController().getCompiledGraph();
      myCollapsedGraph = CollapsedGraph.updateInstance(myCollapsedGraph, delegateGraph);

      // some new edges and node appeared, so we expand them
      applyDelegateChanges(delegateGraph, delegateAnswer.getGraphChanges());
    }
    return delegateAnswer; // if somebody outside actually uses changes we return here they are screwed
  }

  private void applyDelegateChanges(LinearGraph graph, GraphChanges<Integer> changes) {
    Set<Integer> nodesToShow = ContainerUtil.newHashSet();

    for (GraphChanges.Edge<Integer> e : changes.getChangedEdges()) {
      if (!e.removed()) {
        Integer upId = e.upNodeId();
        if (upId != null) {
          Integer upIndex = graph.getNodeIndex(upId);
          if (upIndex != null) {
            nodesToShow.add(upIndex);
          }
        }
        Integer downId = e.downNodeId();
        if (downId != null) {
          Integer downIndex = graph.getNodeIndex(downId);
          if (downIndex != null) {
            nodesToShow.add(downIndex);
          }
        }
      }
    }

    for (GraphChanges.Node<Integer> e : changes.getChangedNodes()) {
      if (!e.removed()) {
        Integer nodeIndex = graph.getNodeIndex(e.getNodeId());
        if (nodeIndex != null) {
          nodesToShow.add(nodeIndex);
        }
      }
    }

    CollapsedActionManager.expandNodes(myCollapsedGraph, nodesToShow);
  }

  @Nullable
  @Override
  protected LinearGraphAnswer performAction(@NotNull LinearGraphAction action) {
    return CollapsedActionManager.performAction(this, action);
  }

  @NotNull
  @Override
  public LinearGraph getCompiledGraph() {
    return myCollapsedGraph.getCompiledGraph();
  }

  @NotNull
  protected CollapsedGraph getCollapsedGraph() {
    return myCollapsedGraph;
  }

  @Nullable
  @Override
  protected GraphElement convertToDelegate(@NotNull GraphElement graphElement) {
    return convertToDelegate(graphElement, myCollapsedGraph);
  }

  @Nullable
  public static GraphElement convertToDelegate(@NotNull GraphElement graphElement, CollapsedGraph collapsedGraph) {
    if (graphElement instanceof GraphEdge) {
      Integer upIndex = ((GraphEdge)graphElement).getUpNodeIndex();
      Integer downIndex = ((GraphEdge)graphElement).getDownNodeIndex();
      if (upIndex != null && downIndex != null && collapsedGraph.isMyCollapsedEdge(upIndex, downIndex)) return null;

      Integer convertedUpIndex = upIndex == null ? null : collapsedGraph.convertToDelegateNodeIndex(upIndex);
      Integer convertedDownIndex = downIndex == null ? null : collapsedGraph.convertToDelegateNodeIndex(downIndex);

      return new GraphEdge(convertedUpIndex, convertedDownIndex, ((GraphEdge)graphElement).getTargetId(),
                           ((GraphEdge)graphElement).getType());
    }
    else if (graphElement instanceof GraphNode) {
      return new GraphNode(collapsedGraph.convertToDelegateNodeIndex((((GraphNode)graphElement).getNodeIndex())),
                           ((GraphNode)graphElement).getType());
    }
    return null;
  }
}
