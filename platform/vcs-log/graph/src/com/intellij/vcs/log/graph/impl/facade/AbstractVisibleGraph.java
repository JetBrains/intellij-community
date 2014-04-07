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
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.actions.ActionController;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.graph.actions.GraphMouseAction;
import com.intellij.vcs.log.graph.api.LinearGraphWithCommitInfo;
import com.intellij.vcs.log.graph.api.printer.PrintElementGenerator;
import com.intellij.vcs.log.graph.api.printer.PrintElementsManager;
import com.intellij.vcs.log.graph.impl.print.PrintElementGeneratorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;

public abstract class AbstractVisibleGraph<CommitId> implements VisibleGraph<CommitId> {
  @NotNull
  protected final LinearGraphWithCommitInfo<CommitId> myLinearGraphWithCommitInfo;

  @NotNull
  protected final PrintElementGenerator myPrintElementGenerator;

  @NotNull
  protected final PrintElementsManager myPrintElementsManager;

  protected AbstractVisibleGraph(@NotNull LinearGraphWithCommitInfo<CommitId> linearGraphWithCommitInfo,
                                 @NotNull PrintElementsManager printElementsManager) {
    myLinearGraphWithCommitInfo = linearGraphWithCommitInfo;
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
  abstract protected GraphAnswer<CommitId> clickByElement(@Nullable PrintElement printElement);

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

      switch (graphMouseAction.getType()) {
        case OVER: {
          Cursor cursor = myPrintElementsManager.performOverElement(graphMouseAction.getAffectedElement());
          return new GraphAnswerImpl<CommitId>(null, cursor);
        }
        case CLICK:
          return AbstractVisibleGraph.this.clickByElement(graphMouseAction.getAffectedElement());

        default: {
          throw new IllegalStateException("Not supported GraphMouseAction type: " + graphMouseAction.getType());
        }
      }
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
