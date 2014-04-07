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

import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.SimplePrintElement;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LinearGraphWithCommitInfo;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.printer.PrintElementsManager;
import com.intellij.vcs.log.graph.impl.permanent.PermanentCommitsInfo;
import com.intellij.vcs.log.graph.impl.visible.CollapsedGraphWithHiddenNodes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class CollapsedVisibleGraph<CommitId> extends AbstractVisibleGraph<CommitId> {

  @NotNull
  public static <CommitId> CollapsedVisibleGraph<CommitId> newInstance(@NotNull LinearGraph permanentGraph,
                                                                       @NotNull PermanentCommitsInfo<CommitId> permanentCommitsInfo,
                                                                       @Nullable Set<CommitId> heads) {
    return null;
  }


  @NotNull
  private final GraphAnswerImpl<CommitId> myCommitIdGraphAnswer = new GraphAnswerImpl<CommitId>(null, null);

  @NotNull
  private final CollapsedGraphWithHiddenNodes myGraphWithHiddenNodes;


  private CollapsedVisibleGraph(@NotNull LinearGraphWithCommitInfo<CommitId> linearGraphWithCommitInfo,
                               @NotNull CollapsedGraphWithHiddenNodes graphWithHiddenNodes,
                               @NotNull PrintElementsManager printElementsManager) {
    super(linearGraphWithCommitInfo, printElementsManager);
    myGraphWithHiddenNodes = graphWithHiddenNodes;
  }

  @Override
  protected void setLinearBranchesExpansion(boolean collapse) {
    // todo
  }

  @NotNull
  @Override
  protected GraphAnswer<CommitId> clickByElement(@Nullable PrintElement printElement) {
    if (printElement == null) {
      return myCommitIdGraphAnswer;
    }

    if (printElement instanceof SimplePrintElement) {
      SimplePrintElement simplePrintElement = (SimplePrintElement)printElement;

      @NotNull GraphElement graphElement = myPrintElementGenerator.getRelatedGraphElement(printElement);
      switch (simplePrintElement.getType()) {
        case NODE:
          assert graphElement instanceof GraphNode;
          int nodeIndex = ((GraphNode)graphElement).getNodeIndex(); // todo
          return null;
        case UP_ARROW:
          assert graphElement instanceof GraphEdge;
          int upNodeIndex = ((GraphEdge)graphElement).getUpNodeIndex();
          return new GraphAnswerImpl<CommitId>(myLinearGraphWithCommitInfo.getHashIndex(upNodeIndex), null);
        case DOWN_ARROW:
          assert graphElement instanceof GraphEdge;
          int downNodeIndex = ((GraphEdge)graphElement).getDownNodeIndex();
          return new GraphAnswerImpl<CommitId>(myLinearGraphWithCommitInfo.getHashIndex(downNodeIndex), null);
        default:
          throw new IllegalStateException("Unsupported SimplePrintElement type: " + simplePrintElement.getType());
      }
    }

    if (printElement instanceof EdgePrintElement) {
      GraphElement graphElement = myPrintElementGenerator.getRelatedGraphElement(printElement);
      assert graphElement instanceof GraphEdge; // todo
      return null;
    }

    return myCommitIdGraphAnswer;
  }
}
