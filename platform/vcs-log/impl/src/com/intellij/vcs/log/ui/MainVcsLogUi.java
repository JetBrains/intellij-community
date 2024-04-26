// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Experimental
public interface MainVcsLogUi extends VcsLogUiEx {
  @Override
  @NotNull
  VcsLogGraphTable getTable();

  @NotNull
  @Override
  VcsLogFilterUiEx getFilterUi();

  @NotNull
  JComponent getToolbar();

  @NotNull
  ChangesBrowserBase getChangesBrowser();

  @NotNull
  @Override
  MainVcsLogUiProperties getProperties();

  @ApiStatus.Internal
  void selectFilePath(@NotNull FilePath filePath, boolean requestFocus);
}
