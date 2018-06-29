/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.collapsing.CollapsedGraph;
import com.intellij.vcs.log.graph.collapsing.DottedFilterEdgesGenerator;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.UnsignedBitSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class FilteredController extends CascadeController {
  @NotNull private final CollapsedGraph myCollapsedGraph;

  protected FilteredController(@NotNull LinearGraphController delegateLinearGraphController,
                               @NotNull PermanentGraphInfo permanentGraphInfo,
                               @NotNull Set<Integer> matchedIds) {
    this(delegateLinearGraphController, permanentGraphInfo, matchedIds, null);
  }

  protected FilteredController(@NotNull LinearGraphController delegateLinearGraphController,
                               @NotNull PermanentGraphInfo permanentGraphInfo,
                               @NotNull Set<Integer> matchedIds,
                               @Nullable Set<Integer> visibleHeadsIds) {
    super(delegateLinearGraphController, permanentGraphInfo);

    UnsignedBitSet initVisibility = new UnsignedBitSet();
    if (visibleHeadsIds != null) {
      ReachableNodes getter = new ReachableNodes(LinearGraphUtils.asLiteLinearGraph(myPermanentGraphInfo.getLinearGraph()));
      getter.walk(visibleHeadsIds, node -> {
        if (matchedIds.contains(node)) initVisibility.set(node, true);
      });
    }
    else {
      for (Integer matchedId: matchedIds) initVisibility.set(matchedId, true);
    }

    myCollapsedGraph = CollapsedGraph.newInstance(delegateLinearGraphController.getCompiledGraph(), initVisibility);
    DottedFilterEdgesGenerator.update(myCollapsedGraph, 0, myCollapsedGraph.getDelegatedGraph().nodesCount() - 1);
  }

  @NotNull
  @Override
  public LinearGraphAnswer performLinearGraphAction(@NotNull LinearGraphAction action) {
    // filter prohibits any actions on delegate graph for now
    LinearGraphAnswer answer = performAction(action);
    if (answer != null) return answer;
    return LinearGraphUtils.DEFAULT_GRAPH_ANSWER;
  }

  @Nullable
  @Override
  protected GraphElement convertToDelegate(@NotNull GraphElement graphElement) {
    // filter prohibits any actions on delegate graph for now
    return null;
  }

  @NotNull
  @Override
  protected LinearGraphAnswer delegateGraphChanged(@NotNull LinearGraphAnswer delegateAnswer) {
    if (delegateAnswer == LinearGraphUtils.DEFAULT_GRAPH_ANSWER) return delegateAnswer;
    throw new UnsupportedOperationException(); // todo fix later
  }

  @Nullable
  @Override
  protected LinearGraphAnswer performAction(@NotNull LinearGraphAction action) {
    return null;
  }

  @NotNull
  @Override
  public LinearGraph getCompiledGraph() {
    return myCollapsedGraph.getCompiledGraph();
  }

  @NotNull
  public CollapsedGraph getCollapsedGraph() {
    return myCollapsedGraph;
  }
}
