// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.highlighters;

import com.intellij.vcs.log.VcsLogHighlighter;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.data.VcsLogData;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * A factory for creating {@link VcsLogHighlighter} instances to customize commit presentation (such as color and text style)
 * in the Vcs Log table.
 *
 * @see VcsLogHighlighter
 * @see VcsLogHighlighter.VcsCommitStyle
 */
public interface VcsLogHighlighterFactory {
  /**
   * Creates a new {@link VcsLogHighlighter} instance for the specified {@link VcsLogData} and {@link VcsLogUi}.
   *
   * @param logData a {@link VcsLogData} instance
   * @param logUi   a {@link VcsLogUi} instance
   * @return a new highlighter instance
   * @see VcsLogHighlighter
   */
  @NotNull
  VcsLogHighlighter createHighlighter(@NotNull VcsLogData logData, @NotNull VcsLogUi logUi);

  /**
   * A unique id of this factory.
   *
   * @return id string
   */
  @NotNull
  String getId();

  /**
   * A name for this highlighter. This name is used for showing a toggle action to enable or disable the highlighter
   * from the Vcs Log presentation settings.
   *
   * @return a name string
   * @see VcsLogHighlighterFactory#showMenuItem()
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  String getTitle();

  /**
   * Return true to show a toggle action to enable or disable this highlighter in the Vcs Log presentation settings.
   *
   * @return true to display a toggle action for the highlighter, false otherwise
   * @see VcsLogHighlighterFactory#getTitle()
   */
  boolean showMenuItem();
}
