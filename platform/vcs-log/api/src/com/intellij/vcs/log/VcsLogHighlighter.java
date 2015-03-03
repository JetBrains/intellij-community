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
import java.util.Collection;

/**
 * Allows to add some highlighting to the Vcs Log table entries.
 */
public interface VcsLogHighlighter {

  /**
   * Return the style which should be used for the log table commit entry, or VcsCommitStyle.DEFAULT if this highlighter does not specify any style for this commit.
   *
   * @param commitIndex index of commit (can be transferred to the Hash and vice versa).
   * @param isSelected  if true, the row currently has selection on it.
   */
  @NotNull
  VcsCommitStyle getStyle(int commitIndex, boolean isSelected);

  /**
   * Describes how to display commit entry in the log table (for example, text or background color).
   */
  interface VcsCommitStyle {
    /**
     * Default commit entry style.
     */
    VcsCommitStyle DEFAULT = new VcsCommitStyleImpl(null, null);

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

  }

  /**
   * Default implementation of VcsCommitStyle.
   */
  class VcsCommitStyleImpl implements VcsCommitStyle {
    @Nullable private final Color myForeground;
    @Nullable private final Color myBackground;

    /**
     * Creates VcsCommitStyleImpl with specified text color and background color.
     *
     * @param foreground text color or null if unspecified.
     * @param background background color or null if unspecified.
     */
    public VcsCommitStyleImpl(@Nullable Color foreground, @Nullable Color background) {
      myForeground = foreground;
      myBackground = background;
    }

    @Nullable
    @Override
    public Color getForeground() {
      return myForeground;
    }

    @Nullable
    @Override
    public Color getBackground() {
      return myBackground;
    }

    /**
     * Creates VcsCommitStyleImpl with specified text color and no background color.
     *
     * @param foreground text color or null if unspecified.
     */
    @NotNull
    public static VcsCommitStyleImpl foreground(@Nullable Color foreground) {
      return new VcsCommitStyleImpl(foreground, null);
    }

    /**
     * Creates VcsCommitStyleImpl with specified background color and no text color.
     *
     * @param background background color or null if unspecified.
     */
    @NotNull
    public static VcsCommitStyleImpl background(@Nullable Color background) {
      return new VcsCommitStyleImpl(null, background);
    }

    /**
     * Combines a list of styles into one. For example, if first style in the list specifies text color but does not provide
     * background color and second style in the list does have a background color then this method will return a style with text color from the first
     * and background color from the second. However if the first style in the list has all the attributes then the result will be equal to the first style
     * and the rest of the list will be ignored.
     *
     * @param styles list of styles to combine into one.
     * @return a combination of styles from the list.
     */
    @NotNull
    public static VcsCommitStyle combine(@NotNull Collection<VcsCommitStyle> styles) {
      Color foreground = null;
      Color background = null;

      for (VcsCommitStyle style : styles) {
        if (foreground == null && style.getForeground() != null) {
          foreground = style.getForeground();
        }
        if (background == null && style.getBackground() != null) {
          background = style.getBackground();
        }
        if (background != null && foreground != null) break;
      }

      return new VcsCommitStyleImpl(foreground, background);
    }
  }
}
