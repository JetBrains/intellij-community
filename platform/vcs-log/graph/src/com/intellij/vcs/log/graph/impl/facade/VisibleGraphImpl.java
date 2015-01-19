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

import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.RowInfo;
import com.intellij.vcs.log.graph.RowType;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.actions.ActionController;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.api.printer.PrintElementGenerator;
import com.intellij.vcs.log.graph.api.printer.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.impl.print.PrintElementGeneratorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;

public class VisibleGraphImpl<CommitId> implements VisibleGraph<CommitId> {
  @NotNull
  private final LinearGraphController myGraphController;
  @NotNull
  private final PermanentGraphInfo<CommitId> myPermanentGraph;

  @NotNull
  private PrintElementGenerator myPrintElementGenerator;
  private boolean myShowLongEdges = false;

  public VisibleGraphImpl(@NotNull LinearGraphController graphController,
                          @NotNull PermanentGraphInfo<CommitId> permanentGraph) {
    myGraphController = graphController;
    myPermanentGraph = permanentGraph;
    myPrintElementGenerator = new PrintElementGeneratorImpl(graphController.getCompiledGraph(),
                                                            graphController.getPrintElementManager(),
                                                            myShowLongEdges);
  }

  @Override
  public int getVisibleCommitCount() {
    return myGraphController.getCompiledGraph().nodesCount();
  }

  @NotNull
  @Override
  public RowInfo<CommitId> getRowInfo(final int visibleRow) {
    final int nodeId = myGraphController.getCompiledGraph().getNodeId(visibleRow);
    assert nodeId >= 0; // todo remake for all id
    return new RowInfo<CommitId>() {
      @NotNull
      @Override
      public CommitId getCommit() {
        return myPermanentGraph.getPermanentCommitsInfo().getCommitId(nodeId);
      }

      @NotNull
      @Override
      public CommitId getOneOfHeads() {
        int headNodeId = myPermanentGraph.getPermanentGraphLayout().getOneOfHeadNodeIndex(nodeId);
        return myPermanentGraph.getPermanentCommitsInfo().getCommitId(headNodeId);
      }

      @NotNull
      @Override
      public Collection<? extends PrintElement> getPrintElements() {
        return myPrintElementGenerator.getPrintElements(visibleRow);
      }

      @NotNull
      @Override
      public RowType getRowType() {
        throw new UnsupportedOperationException(); // todo fix
      }
    };
  }

  @Override
  @Nullable
  public Integer getVisibleRowIndex(@NotNull CommitId commitId) {
    int nodeId = myPermanentGraph.getPermanentCommitsInfo().getNodeId(commitId);
    return myGraphController.getCompiledGraph().getNodeIndexById(nodeId);
  }

  @NotNull
  @Override
  public ActionController<CommitId> getActionController() {
    return new ActionControllerImpl();
  }

  private void graphWasChanged() {
    myPrintElementGenerator = new PrintElementGeneratorImpl(myGraphController.getCompiledGraph(),
                                                            myGraphController.getPrintElementManager(),
                                                            myShowLongEdges);
  }

  private class ActionControllerImpl implements ActionController<CommitId> {

    @NotNull
    @Override
    public GraphAnswer<CommitId> performAction(@NotNull GraphAction graphAction) {
      LinearGraphController.LinearGraphAnswer answer = myGraphController.performLinearGraphAction(convert(graphAction));
      if (answer.getGraphChanges() != null)
        graphWasChanged();
      return convert(answer);
    }

    @Override
    public boolean areLongEdgesHidden() {
      return !myShowLongEdges;
    }

    @Override
    public void setLongEdgesHidden(boolean longEdgesHidden) {
      myShowLongEdges = !longEdgesHidden;
      graphWasChanged();
    }

    @Override
    public void setLinearBranchesExpansion(boolean collapse) {
      LinearGraphController.LinearGraphAnswer answer;
      if (collapse)
        answer = myGraphController.performLinearGraphAction(LinearGraphActionImpl.COLLAPSE);
      else
        answer = myGraphController.performLinearGraphAction(LinearGraphActionImpl.EXPAND);
      if (answer.getGraphChanges() != null)
        graphWasChanged();
    }

    private LinearGraphController.LinearGraphAction convert(@NotNull GraphAction graphAction) {
      PrintElementWithGraphElement printElement = null;
      if (graphAction.getAffectedElement() != null)
        printElement = myPrintElementGenerator.toPrintElementWithGraphElement(graphAction.getAffectedElement());
      return new LinearGraphActionImpl(printElement, graphAction.getType());
    }

    private GraphAnswer<CommitId> convert(@NotNull LinearGraphController.LinearGraphAnswer answer) {
      CommitId commitToJump = null;
      Integer nodeId = answer.getCommitToJump();
      if (nodeId != null)
        commitToJump = myPermanentGraph.getPermanentCommitsInfo().getCommitId(nodeId);
      return new GraphAnswerImpl<CommitId>(answer.getCursorToSet(), commitToJump);
    }
  }

  private static class GraphAnswerImpl<CommitId> implements GraphAnswer<CommitId> {
    @Nullable
    private final Cursor myCursor;
    @Nullable
    private final CommitId myCommitToJump;

    private GraphAnswerImpl(@Nullable Cursor cursor, @Nullable CommitId commitToJump) {
      myCursor = cursor;
      myCommitToJump = commitToJump;
    }

    @Nullable
    @Override
    public Cursor getCursorToSet() {
      return myCursor;
    }

    @Nullable
    @Override
    public CommitId getCommitToJump() {
      return myCommitToJump;
    }
  }

  private static class LinearGraphActionImpl implements LinearGraphController.LinearGraphAction {
    private final static LinearGraphController.LinearGraphAction COLLAPSE = new LinearGraphActionImpl(null, Type.BUTTON_COLLAPSE);
    private final static LinearGraphController.LinearGraphAction EXPAND = new LinearGraphActionImpl(null, Type.BUTTON_EXPAND);

    @Nullable
    private final PrintElementWithGraphElement myAffectedElement;
    @NotNull
    private final Type myType;

    private LinearGraphActionImpl(@Nullable PrintElementWithGraphElement affectedElement, @NotNull Type type) {
      myAffectedElement = affectedElement;
      myType = type;
    }

    @Nullable
    @Override
    public PrintElementWithGraphElement getAffectedElement() {
      return myAffectedElement;
    }

    @NotNull
    @Override
    public Type getType() {
      return myType;
    }
  }
}
