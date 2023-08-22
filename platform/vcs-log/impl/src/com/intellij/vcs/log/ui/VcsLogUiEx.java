// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.ui.navigation.History;
import com.intellij.util.PairFunction;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Experimental
public interface VcsLogUiEx extends VcsLogUi, Disposable {
  @NotNull
  VisiblePackRefresher getRefresher();

  @NotNull
  VcsLog getVcsLog();

  @Override
  @NotNull
  VisiblePack getDataPack();

  @NotNull
  VcsLogGraphTable getTable();

  @NotNull
  JComponent getMainComponent();

  @NotNull
  VcsLogUiProperties getProperties();

  @NotNull
  VcsLogColorManager getColorManager();

  @NotNull
  VcsLogData getLogData();

  @Nullable
  History getNavigationHistory();

  @Nullable
  String getHelpId();

  @ApiStatus.Internal
  <T> void jumpTo(@NotNull T commitId,
                  @NotNull PairFunction<? super VisiblePack, ? super T, Integer> rowGetter,
                  @NotNull SettableFuture<JumpResult> future,
                  boolean silently,
                  boolean focus);

  @ApiStatus.Internal int COMMIT_NOT_FOUND = -1;
  @ApiStatus.Internal int COMMIT_DOES_NOT_MATCH = -2;

  enum JumpResult {
    SUCCESS, COMMIT_NOT_FOUND, COMMIT_DOES_NOT_MATCH;

    static @NotNull JumpResult fromInt(int result) {
      if (result == VcsLogUiEx.COMMIT_NOT_FOUND) return COMMIT_NOT_FOUND;
      if (result == VcsLogUiEx.COMMIT_DOES_NOT_MATCH) return COMMIT_DOES_NOT_MATCH;
      if (result >= 0) return SUCCESS;
      return COMMIT_NOT_FOUND;
    }
  }
}
