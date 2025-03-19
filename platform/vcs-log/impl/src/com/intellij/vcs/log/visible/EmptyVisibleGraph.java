// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible;

import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.RowInfo;
import com.intellij.vcs.log.graph.RowType;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.actions.ActionController;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class EmptyVisibleGraph implements VisibleGraph<Integer> {
  private static final VisibleGraph<Integer> INSTANCE = new EmptyVisibleGraph();

  public static @NotNull VisibleGraph<Integer> getInstance() {
    return INSTANCE;
  }

  @Override
  public int getVisibleCommitCount() {
    return 0;
  }

  @Override
  public @NotNull RowInfo<Integer> getRowInfo(int visibleRow) {
    return EmptyRowInfo.INSTANCE;
  }

  @Override
  public @Nullable Integer getVisibleRowIndex(@NotNull Integer integer) {
    return null;
  }

  @Override
  public @NotNull ActionController<Integer> getActionController() {
    return DumbActionController.INSTANCE;
  }

  @Override
  public int getRecommendedWidth() {
    return 0;
  }

  private static class DumbActionController implements ActionController<Integer> {

    private static final ActionController<Integer> INSTANCE = new DumbActionController();

    @Override
    public @NotNull GraphAnswer<Integer> performAction(@NotNull GraphAction graphAction) {
      return EmptyGraphAnswer.INSTANCE;
    }

    @Override
    public boolean areLongEdgesHidden() {
      return false;
    }

    @Override
    public void setLongEdgesHidden(boolean longEdgesHidden) {
    }

    private static final class EmptyGraphAnswer implements GraphAnswer<Integer> {
      private static final EmptyGraphAnswer INSTANCE = new EmptyGraphAnswer();

      @Override
      public @Nullable Cursor getCursorToSet() {
        return null;
      }

      @Override
      public @Nullable Integer getCommitToJump() {
        return null;
      }

      @Override
      public @Nullable Runnable getGraphUpdater() {
        return null;
      }

      @Override
      public boolean doJump() {
        return false;
      }

      @Override
      public boolean isRepaintRequired() {
        return false;
      }
    }
  }

  private static class EmptyRowInfo implements RowInfo<Integer> {

    private static final RowInfo<Integer> INSTANCE = new EmptyRowInfo();

    @Override
    public @NotNull Integer getCommit() {
      return 0;
    }

    @Override
    public @NotNull Integer getOneOfHeads() {
      return 0;
    }

    @Override
    public @NotNull Collection<PrintElement> getPrintElements() {
      return Collections.emptyList();
    }

    @Override
    public @NotNull RowType getRowType() {
      return RowType.NORMAL;
    }

    @Override
    public @NotNull List<Integer> getAdjacentRows(boolean parent) {
      return Collections.emptyList();
    }
  }
}
