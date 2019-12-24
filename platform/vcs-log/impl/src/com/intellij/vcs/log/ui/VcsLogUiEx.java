// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.navigation.History;
import com.intellij.util.PairFunction;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.table.GraphTableModel;
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

  @Nullable
  History getNavigationHistory();

  @Nullable
  String getHelpId();

  void jumpToRow(int row, boolean silently);

  @NotNull
  ListenableFuture<Boolean> jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root);

  @NotNull
  ListenableFuture<Boolean> jumpToHash(@NotNull String commitHash);

  <T> void jumpTo(@NotNull T commitId,
                  @NotNull PairFunction<GraphTableModel, T, Integer> rowGetter,
                  @NotNull SettableFuture<? super Boolean> future,
                  boolean silently);
}
