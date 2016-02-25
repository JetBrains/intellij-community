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
package com.intellij.vcs.log.data;

import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.RowInfo;
import com.intellij.vcs.log.graph.RowType;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.actions.ActionController;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.graph.actions.GraphAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;

class EmptyVisibleGraph implements VisibleGraph<Integer> {

  private static final VisibleGraph<Integer> INSTANCE = new EmptyVisibleGraph();

  @NotNull
  public static VisibleGraph<Integer> getInstance() {
    return INSTANCE;
  }

  @Override
  public int getVisibleCommitCount() {
    return 0;
  }

  @NotNull
  @Override
  public RowInfo<Integer> getRowInfo(int visibleRow) {
    return EmptyRowInfo.INSTANCE;
  }

  @Override
  @Nullable
  public Integer getVisibleRowIndex(@NotNull Integer integer) {
    return null;
  }

  @NotNull
  @Override
  public ActionController<Integer> getActionController() {
    return DumbActionController.INSTANCE;
  }

  private static class DumbActionController implements ActionController<Integer> {

    private static ActionController<Integer> INSTANCE = new DumbActionController();

    @NotNull
    @Override
    public GraphAnswer<Integer> performAction(@NotNull GraphAction graphAction) {
      return EmptyGraphAnswer.INSTANCE;
    }

    @Override
    public boolean areLongEdgesHidden() {
      return false;
    }

    @Override
    public void setLongEdgesHidden(boolean longEdgesHidden) {
    }

    private static class EmptyGraphAnswer implements GraphAnswer<Integer> {
      private static EmptyGraphAnswer INSTANCE = new EmptyGraphAnswer();

      @Nullable
      @Override
      public Cursor getCursorToSet() {
        return null;
      }

      @Nullable
      @Override
      public Integer getCommitToJump() {
        return null;
      }

      @Nullable
      @Override
      public Runnable getGraphUpdater() {
        return null;
      }

      @Override
      public boolean doJump() {
        return false;
      }
    }
  }

  private static class EmptyRowInfo implements RowInfo<Integer> {

    private static final RowInfo<Integer> INSTANCE = new EmptyRowInfo();

    @NotNull
    @Override
    public Integer getCommit() {
      return 0;
    }

    @NotNull
    @Override
    public Integer getOneOfHeads() {
      return 0;
    }

    @NotNull
    @Override
    public Collection<PrintElement> getPrintElements() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public RowType getRowType() {
      return RowType.NORMAL;
    }
  }
}
