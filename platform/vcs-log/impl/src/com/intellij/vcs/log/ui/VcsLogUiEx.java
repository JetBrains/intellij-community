// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsLogNavigationUtil;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.table.VcsLogCommitList;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.BiFunction;

/**
 * Use {@link VcsLogUiBase} as a base implementation class
 */
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
  VcsLogCommitList getTable();

  @NotNull
  JComponent getMainComponent();

  @NotNull
  VcsLogUiProperties getProperties();

  @NotNull
  VcsLogData getLogData();

  /**
   * @param commitId Prefer using {@link Hash} or {@link com.intellij.vcs.log.CommitId} when jumping to a specific commit.
   *                 This value may be displayed to the user.
   * @see VcsLogNavigationUtil for public usages
   */
  @ApiStatus.Internal
  <T> void jumpTo(@NotNull T commitId,
                  @NotNull BiFunction<? super VisiblePack, ? super T, Integer> rowGetter,
                  @NotNull SettableFuture<JumpResult> future,
                  boolean silently,
                  boolean focus);

  @ApiStatus.Internal
  <T> JumpResult jumpToSync(@NotNull T commitId,
                            @NotNull BiFunction<? super VisiblePack, ? super T, Integer> rowGetter,
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
