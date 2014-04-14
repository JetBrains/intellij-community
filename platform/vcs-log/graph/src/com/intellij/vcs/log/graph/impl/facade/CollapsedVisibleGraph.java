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

import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.printer.PrintElementsManager;
import com.intellij.vcs.log.graph.impl.print.AbstractPrintElementsManager;
import com.intellij.vcs.log.graph.impl.print.PrintElementsManagerImpl;
import com.intellij.vcs.log.graph.impl.visible.CollapsedGraphWithHiddenNodes;
import com.intellij.vcs.log.graph.impl.visible.FragmentGenerator;
import com.intellij.vcs.log.graph.impl.visible.adapters.GraphWithHiddenNodesAsGraphWithCommitInfo;
import com.intellij.vcs.log.graph.impl.visible.adapters.LinearGraphAsGraphWithHiddenNodes;
import com.intellij.vcs.log.graph.utils.IntToIntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class CollapsedVisibleGraph<CommitId> extends AbstractVisibleGraph<CommitId> {

  @NotNull
  public static <CommitId> CollapsedVisibleGraph<CommitId> newInstance(@NotNull PermanentGraphImpl<CommitId> permanentGraph,
                                                                       @Nullable Set<CommitId> heads) {
    LinearGraphAsGraphWithHiddenNodes branchesGraph = createBranchesGraph(permanentGraph, heads);
    CollapsedGraphWithHiddenNodes collapsedGraph = new CollapsedGraphWithHiddenNodes(branchesGraph);
    GraphWithHiddenNodesAsGraphWithCommitInfo<CommitId> graphWithCommitInfo =
      new GraphWithHiddenNodesAsGraphWithCommitInfo<CommitId>(collapsedGraph, permanentGraph.getPermanentGraphLayout(),
                                                              permanentGraph.getPermanentCommitsInfo());

    FragmentGenerator fragmentGeneratorForPrinterGraph = new FragmentGenerator(graphWithCommitInfo, permanentGraph.getNotCollapsedNodes());

    PrintElementsManagerImpl printElementsManager = new PrintElementsManagerImpl<CommitId>(graphWithCommitInfo,
                                                                                           fragmentGeneratorForPrinterGraph,
                                                                                           permanentGraph.getGraphColorManager());
    return new CollapsedVisibleGraph<CommitId>(graphWithCommitInfo, collapsedGraph, permanentGraph.getCommitsWithNotLoadParent(),
                                               printElementsManager, fragmentGeneratorForPrinterGraph);
  }

  @NotNull
  private final CollapsedGraphWithHiddenNodes myCollapsedGraph;

  @NotNull
  private final FragmentGenerator myFragmentGeneratorForPrinterGraph;

  @NotNull
  private final IntToIntMap myIntToIntMap;


  private CollapsedVisibleGraph(@NotNull GraphWithHiddenNodesAsGraphWithCommitInfo<CommitId> graphWithCommitInfo,
                                @NotNull CollapsedGraphWithHiddenNodes collapsedGraph,
                                @NotNull Map<CommitId, GraphCommit<CommitId>> commitsWithNotLoadParent,
                                @NotNull PrintElementsManager printElementsManager,
                                @NotNull FragmentGenerator fragmentGeneratorForPrinterGraph) {
    super(graphWithCommitInfo, commitsWithNotLoadParent, printElementsManager);
    myCollapsedGraph = collapsedGraph;
    myFragmentGeneratorForPrinterGraph = fragmentGeneratorForPrinterGraph;
    myIntToIntMap = graphWithCommitInfo.getIntToIntMap();
  }

  @Override
  protected void setLinearBranchesExpansion(boolean collapse) {
    // todo
  }

  @NotNull
  protected GraphAnswer<CommitId> clickByElement(@NotNull GraphElement graphElement) {
    GraphEdge graphEdge = AbstractPrintElementsManager.containedCollapsedEdge(graphElement, myLinearGraphWithCommitInfo);
    if (graphEdge != null) {
      int upShortIndex = myIntToIntMap.getShortIndex(graphEdge.getUpNodeIndex());
      int downShortIndex = myIntToIntMap.getShortIndex(graphEdge.getDownNodeIndex());
      myCollapsedGraph.expand(upShortIndex, downShortIndex);
      return createJumpAnswer(graphEdge.getUpNodeIndex());
    }

    FragmentGenerator.GraphFragment relativeFragment = myFragmentGeneratorForPrinterGraph.getRelativeFragment(graphElement);
    if (relativeFragment != null) {
      int upShortIndex = myIntToIntMap.getShortIndex(relativeFragment.upNodeIndex);
      int downShortIndex = myIntToIntMap.getShortIndex(relativeFragment.downNodeIndex);
      myCollapsedGraph.collapse(upShortIndex, downShortIndex);
      return createJumpAnswer(relativeFragment.upNodeIndex);
    }

    return COMMIT_ID_GRAPH_ANSWER;
  }
}
