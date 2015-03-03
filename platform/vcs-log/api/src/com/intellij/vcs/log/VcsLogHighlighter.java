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
   * Return the color which should be used for the log table entries foreground, or null if default color should be used.
   *
   * @param commitIndex index of commit (can be transferred to the Hash and vice versa).
   * @param isSelected  if true, the row currently has selection on it.
   */
  @NotNull
  VcsCommitStyle getStyle(int commitIndex, boolean isSelected);


  class VcsCommitStyle {
    public static final VcsCommitStyle DEFAULT = new VcsCommitStyle(null, null);
    @Nullable private final Color myForeground;
    @Nullable private final Color myBackground;

    public VcsCommitStyle(@Nullable Color foreground, @Nullable Color background) {
      myForeground = foreground;
      myBackground = background;
    }

    @Nullable
    public Color getForeground() {
      return myForeground;
    }

    @Nullable
    public Color getBackground() {
      return myBackground;
    }

    @NotNull
    public static VcsCommitStyle foreground(@Nullable Color foreground) {
      return new VcsCommitStyle(foreground, null);
    }

    @NotNull
    public static VcsCommitStyle background(@Nullable Color background) {
      return new VcsCommitStyle(null, background);
    }

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

      return new VcsCommitStyle(foreground, background);
    }
  }
}
