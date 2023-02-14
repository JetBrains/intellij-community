// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Allows to add some highlighting to the Vcs Log table entries.
 */
public interface VcsLogHighlighter {

  /**
   * Return the style which should be used for the log table commit entry, or {@link VcsCommitStyle#DEFAULT} if this highlighter does not specify any style for this commit.
   *
   * @param commitId      id of selected commit.
   * @param commitDetails details of selected commit.
   * @param column        column index in the table model
   * @param isSelected    if true, the row currently has selection on it.
   */
  @NotNull
  VcsCommitStyle getStyle(int commitId, @NotNull VcsShortCommitDetails commitDetails, int column, boolean isSelected);

  /**
   * This method is called when new data arrives to the ui.
   *
   * @param dataPack        new visible pack.
   * @param refreshHappened true if permanent graph has changed.
   */
  void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened);

  /**
   * Describes how to display commit entry in the log table (for example, text or background color).
   */
  interface VcsCommitStyle {
    /**
     * Default commit entry style.
     */
    VcsCommitStyle DEFAULT = VcsCommitStyleFactory.createStyle(null, null, null);

    /**
     * Text color for commit entry or null if unspecified.
     */
    @Nullable
    Color getForeground();

    /**
     * Background color for commit entry or null if unspecified.
     */
    @Nullable
    Color getBackground();

    /**
     * Text style for commit entry or null if unspecified.
     */
    @Nullable
    TextStyle getTextStyle();
  }

  /**
   * Text style.
   */
  enum TextStyle {
    NORMAL,
    BOLD,
    ITALIC
  }
}
