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
package com.intellij.vcs.log.graph.collapsing;

import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.facade.CascadeController;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.ReachableNodes;
import com.intellij.vcs.log.graph.utils.UnsignedBitSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class BranchFilterController extends CascadeController {
  @NotNull private CollapsedGraph myCollapsedGraph;
  @NotNull private final UnsignedBitSet myVisibility;

  public BranchFilterController(@NotNull LinearGraphController delegateLinearGraphController,
                                @NotNull PermanentGraphInfo<?> permanentGraphInfo,
                                @Nullable Set<Integer> idsOfVisibleBranches) {
    super(delegateLinearGraphController, permanentGraphInfo);
    myVisibility = ReachableNodes.getReachableNodes(myPermanentGraphInfo.getLinearGraph(), idsOfVisibleBranches);
    myCollapsedGraph = update();
  }

  @NotNull
  private CollapsedGraph update() {
    return CollapsedGraph.newInstance(getDelegateController().getCompiledGraph(), myVisibility);
  }

  @NotNull
  @Override
  protected LinearGraphAnswer delegateGraphChanged(@NotNull LinearGraphAnswer delegateAnswer) {
    if (delegateAnswer.getGraphChanges() != null) myCollapsedGraph = update();
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
    return myCollapsedGraph.getCompiledGraph();
  }

  @Nullable
  @Override
  protected GraphElement convertToDelegate(@NotNull GraphElement graphElement) {
    return CollapsedController.convertToDelegate(graphElement, myCollapsedGraph);
  }
}
