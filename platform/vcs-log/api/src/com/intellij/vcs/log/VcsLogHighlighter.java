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
package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Allows to add some highlighting to the Vcs Log table entries.
 */
public interface VcsLogHighlighter {

  /**
   * Return the style which should be used for the log table commit entry, or VcsCommitStyle.DEFAULT if this highlighter does not specify any style for this commit.
   * @param commitDetails details of selected commit.
   * @param isSelected  if true, the row currently has selection on it.
   */
  @NotNull
  VcsCommitStyle getStyle(@NotNull VcsShortCommitDetails commitDetails, boolean isSelected);

  /**
   * This method is called when new data arrives to the ui.
   * @param dataPack new visible pack.
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
