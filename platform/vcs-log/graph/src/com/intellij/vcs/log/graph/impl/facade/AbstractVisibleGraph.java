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

import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.graph.actions.ActionController;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.graph.actions.GraphMouseAction;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LinearGraphWithCommitInfo;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.printer.PrintElementGenerator;
import com.intellij.vcs.log.graph.api.printer.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.api.printer.PrintElementsManager;
import com.intellij.vcs.log.graph.impl.print.PrintElementGeneratorImpl;
import com.intellij.vcs.log.graph.impl.visible.CurrentBranches;
import com.intellij.vcs.log.graph.impl.visible.adapters.LinearGraphAsGraphWithHiddenNodes;
import com.intellij.vcs.log.graph.utils.Flags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public abstract class AbstractVisibleGraph<CommitId> implements VisibleGraph<CommitId> {
  @NotNull
  protected static <CommitId> LinearGraphAsGraphWithHiddenNodes createBranchesGraph(@NotNull final PermanentGraphImpl<CommitId> permanentGraph,
                                                                  @Nullable Set<CommitId> heads) {
    if (heads == null) {
      return new LinearGraphAsGraphWithHiddenNodes(permanentGraph.getPermanentLinearGraph());
    } else {
      Set<Integer> headIndexes = permanentGraph.getPermanentCommitsInfo().convertToCommitIndexes(heads);
      Flags visibleNodes = CurrentBranches.getVisibleNodes(permanentGraph.getPermanentLinearGraph(), headIndexes);
      return new LinearGraphAsGraphWithHiddenNodes(permanentGraph.getPermanentLinearGraph(), visibleNodes);
    }
  }

  @NotNull
  protected final GraphAnswerImpl<CommitId> COMMIT_ID_GRAPH_ANSWER = new GraphAnswerImpl<CommitId>(null, null);

  @NotNull
  protected final LinearGraphWithCommitInfo<CommitId> myLinearGraphWithCommitInfo;
  @NotNull
  protected final PrintElementGenerator myPrintElementGenerator;
  @NotNull
  protected final PrintElementsManager myPrintElementsManager;
  @NotNull
  protected final Map<CommitId, GraphCommit<CommitId>> myCommitsWithNotLoadParent;

  protected AbstractVisibleGraph(@NotNull LinearGraphWithCommitInfo<CommitId> linearGraphWithCommitInfo,
                                 @NotNull Map<CommitId, GraphCommit<CommitId>> commitsWithNotLoadParent,
                                 @NotNull PrintElementsManager printElementsManager) {
    myLinearGraphWithCommitInfo = linearGraphWithCommitInfo;
    myCommitsWithNotLoadParent = commitsWithNotLoadParent;
    myPrintElementsManager = printElementsManager;
    myPrintElementGenerator = new PrintElementGeneratorImpl(linearGraphWithCommitInfo, printElementsManager);
  }

  @Override
  public int getVisibleCommitCount() {
    return myLinearGraphWithCommitInfo.nodesCount();
  }

  @NotNull
  @Override
  public RowInfo<CommitId> getRowInfo(final int visibleRow) {
    final Collection<PrintElement> printElements = myPrintElementGenerator.getPrintElements(visibleRow);
    return new RowInfo<CommitId>() {
      @NotNull
      @Override
      public CommitId getCommit() {
        return myLinearGraphWithCommitInfo.getHashIndex(visibleRow);
      }

      @NotNull
      @Override
      public CommitId getOneOfHeads() {
        int oneOfHeadNodeIndex = myLinearGraphWithCommitInfo.getGraphLayout().getOneOfHeadNodeIndex(visibleRow);
        return myLinearGraphWithCommitInfo.getHashIndex(oneOfHeadNodeIndex);
      }

      @NotNull
      @Override
      public Collection<PrintElement> getPrintElements() {
        return printElements;
      }
    };
  }

  @NotNull
  @Override
  public ActionController<CommitId> getActionController() {
    return new ActionControllerImpl();
  }

  abstract protected void setLinearBranchesExpansion(boolean collapse);

  @NotNull
  abstract protected GraphAnswer<CommitId> clickByElement(@NotNull GraphElement graphElement);

  @NotNull
  protected GraphAnswer<CommitId> clickByElement(@Nullable PrintElementWithGraphElement printElement) {
    if (printElement == null) {
      return COMMIT_ID_GRAPH_ANSWER;
    }

    if (printElement instanceof SimplePrintElement) {
      SimplePrintElement simplePrintElement = (SimplePrintElement)printElement;

      int upNodeIndex, downNodeIndex;
      @NotNull GraphElement graphElement = printElement.getGraphElement();
      switch (simplePrintElement.getType()) {
        case NODE:
          assert graphElement instanceof GraphNode;
          return clickByElement(graphElement);
        case UP_ARROW:
          assert graphElement instanceof GraphEdge;
          upNodeIndex = ((GraphEdge)graphElement).getUpNodeIndex();
          return new GraphAnswerImpl<CommitId>(myLinearGraphWithCommitInfo.getHashIndex(upNodeIndex), null);
        case DOWN_ARROW:
          assert graphElement instanceof GraphEdge;
          downNodeIndex = ((GraphEdge)graphElement).getDownNodeIndex();
          if (downNodeIndex != LinearGraph.NOT_LOAD_COMMIT)
            return new GraphAnswerImpl<CommitId>(myLinearGraphWithCommitInfo.getHashIndex(downNodeIndex), null);
          else {
            upNodeIndex = ((GraphEdge)graphElement).getUpNodeIndex();
            int edgeIndex = myLinearGraphWithCommitInfo.getDownNodes(upNodeIndex).indexOf(LinearGraph.NOT_LOAD_COMMIT);

            GraphCommit<CommitId> commitIdGraphCommit =
              myCommitsWithNotLoadParent.get(myLinearGraphWithCommitInfo.getHashIndex(upNodeIndex));
            CommitId jumpTo = commitIdGraphCommit.getParents().get(edgeIndex);
            return new GraphAnswerImpl<CommitId>(jumpTo, null);
          }
        default:
          throw new IllegalStateException("Unsupported SimplePrintElement type: " + simplePrintElement.getType());
      }
    }

    if (printElement instanceof EdgePrintElement) {
      GraphElement graphElement = printElement.getGraphElement();
      assert graphElement instanceof GraphEdge;
      return clickByElement(graphElement);
    }

    return COMMIT_ID_GRAPH_ANSWER;
  }

  protected GraphAnswer<CommitId> createJumpAnswer(int nodeIndex) {
    return new GraphAnswerImpl<CommitId>(myLinearGraphWithCommitInfo.getHashIndex(nodeIndex), null);
  }

  protected static class GraphAnswerImpl<CommitId> implements GraphAnswer<CommitId> {
    @Nullable
    private final CommitId myCommitId;

    @Nullable
    private final Cursor myCursor;

    public GraphAnswerImpl(@Nullable CommitId commitId, @Nullable Cursor cursor) {
      myCommitId = commitId;
      myCursor = cursor;
    }

    @Nullable
    @Override
    public Cursor getCursorToSet() {
      return myCursor;
    }

    @Nullable
    @Override
    public CommitId getCommitToJump() {
      return myCommitId;
    }
  }

  protected class ActionControllerImpl implements ActionController<CommitId> {
    @NotNull
    @Override
    public GraphAnswer<CommitId> performMouseAction(@NotNull GraphMouseAction graphMouseAction) {
      myPrintElementsManager.performOverElement(null);

      PrintElementWithGraphElement printElement = getPrintElementWithGraphElement(graphMouseAction);
      switch (graphMouseAction.getType()) {
        case OVER: {
          Cursor cursor = myPrintElementsManager.performOverElement(printElement);
          return new GraphAnswerImpl<CommitId>(null, cursor);
        }
        case CLICK:
          return AbstractVisibleGraph.this.clickByElement(printElement);

        default: {
          throw new IllegalStateException("Not supported GraphMouseAction type: " + graphMouseAction.getType());
        }
      }
    }

    @Nullable
    private PrintElementWithGraphElement getPrintElementWithGraphElement(@NotNull GraphMouseAction graphMouseAction) {
      PrintElement affectedElement = graphMouseAction.getAffectedElement();
      if (affectedElement == null)
        return null;

      return myPrintElementGenerator.toPrintElementWithGraphElement(affectedElement);
    }

    @Override
    public boolean areLongEdgesHidden() {
      return myPrintElementGenerator.areLongEdgesHidden();
    }

    @Override
    public void setLongEdgesHidden(boolean longEdgesHidden) {
      myPrintElementGenerator.setLongEdgesHidden(longEdgesHidden);
    }

    @Override
    public void setLinearBranchesExpansion(boolean collapse) {
      AbstractVisibleGraph.this.setLinearBranchesExpansion(collapse);
    }
  }
}
