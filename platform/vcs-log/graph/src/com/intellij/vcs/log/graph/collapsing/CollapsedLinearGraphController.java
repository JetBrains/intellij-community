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

import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.api.printer.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.impl.facade.CascadeLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.GraphChanges;
import com.intellij.vcs.log.graph.utils.UnsignedBitSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CollapsedLinearGraphController extends CascadeLinearGraphController {
  @NotNull
  private CollapsedGraph myCollapsedGraph;

  protected CollapsedLinearGraphController(@NotNull CascadeLinearGraphController delegateLinearGraphController,
                                           @NotNull PermanentGraphInfo permanentGraphInfo) {
    super(delegateLinearGraphController, permanentGraphInfo);
    myCollapsedGraph = CollapsedGraph.newInstance(getDelegateLinearGraphController().getCompiledGraph(), null);
  }

  @NotNull
  @Override
  protected LinearGraphAnswer performDelegateUpdate(@NotNull LinearGraphAnswer delegateAnswer) {
    if (delegateAnswer.getGraphChanges() != null) {
      myCollapsedGraph = CollapsedGraph.updateInstance(myCollapsedGraph, getDelegateLinearGraphController().getCompiledGraph());
      for (GraphChanges.Node<Integer> changedNode : delegateAnswer.getGraphChanges().getChangedNodes()) {
        if (!changedNode.removed()) {
          int nodeId = changedNode.getNodeId();
          myCollapsedGraph.setNodeVisibility(nodeId, true);
        }
      }
    }
    return delegateAnswer;
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

  @Override
  protected boolean elementIsSelected(@NotNull PrintElementWithGraphElement printElement) {
    return false;
  }

  @NotNull
  protected CollapsedGraph getCollapsedGraph() {
    return myCollapsedGraph;
  }

}
