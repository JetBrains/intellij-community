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

package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.openapi.util.Condition;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.graph.api.LinearGraphWithCommitInfo;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.printer.PrintElementsManager;
import com.intellij.vcs.log.graph.impl.print.AbstractPrintElementsManager;
import com.intellij.vcs.log.graph.impl.visible.CollapsedGraphWithHiddenNodes;
import com.intellij.vcs.log.graph.impl.visible.FragmentGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class CollapsedVisibleGraph<CommitId> extends AbstractVisibleGraph<CommitId> {

  @NotNull
  public static <CommitId> CollapsedVisibleGraph<CommitId> newInstance(@NotNull PermanentGraphImpl<CommitId> permanentGraph,
                                                                       @Nullable Set<CommitId> heads) {
    return null;
  }

  @NotNull
  private final CollapsedGraphWithHiddenNodes myGraphWithHiddenNodes;

  @NotNull
  private final FragmentGenerator myFragmentGenerator;


  private CollapsedVisibleGraph(@NotNull LinearGraphWithCommitInfo<CommitId> linearGraphWithCommitInfo,
                                @NotNull CollapsedGraphWithHiddenNodes graphWithHiddenNodes,
                                @NotNull Map<CommitId, GraphCommit<CommitId>> commitsWithNotLoadParent,
                                @NotNull PrintElementsManager printElementsManager,
                                @NotNull Condition<Integer> thisNodeCantBeInMiddle) {
    super(linearGraphWithCommitInfo, commitsWithNotLoadParent, printElementsManager);
    myGraphWithHiddenNodes = graphWithHiddenNodes;
    myFragmentGenerator = new FragmentGenerator(myGraphWithHiddenNodes, thisNodeCantBeInMiddle);
  }

  @Override
  protected void setLinearBranchesExpansion(boolean collapse) {
    // todo
  }

  @NotNull
  protected GraphAnswer<CommitId> clickByElement(@NotNull GraphElement graphElement) {
    return null;
  }
}
