/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * Factory for creating instances of default implementation VcsCommitStyle.
 */
public class VcsCommitStyleFactory {
  /**
   * Creates VcsCommitStyle with specified text color and background color.
   *
   * @param foreground text color or null if unspecified.
   * @param background background color or null if unspecified.
   * @return new instance of VcsCommitStyle with specified text and background color.
   */
  @NotNull
  public static VcsLogHighlighter.VcsCommitStyle createStyle(@Nullable Color foreground,
                                                             @Nullable Color background,
                                                             @Nullable VcsLogHighlighter.TextStyle textStyle) {
    return new VcsCommitStyleImpl(foreground, background, textStyle);
  }

  /**
   * Creates VcsCommitStyleImpl with specified text color and no background color.
   *
   * @param foreground text color or null if unspecified.
   */
  @NotNull
  public static VcsLogHighlighter.VcsCommitStyle foreground(@Nullable Color foreground) {
    return createStyle(foreground, null, null);
  }

  /**
   * Creates VcsCommitStyleImpl with specified background color and no text color.
   *
   * @param background background color or null if unspecified.
   */
  @NotNull
  public static VcsLogHighlighter.VcsCommitStyle background(@Nullable Color background) {
    return createStyle(null, background, null);
  }

  /**
   * Creates VcsCommitStyleImpl with bold text.
   */
  @NotNull
  public static VcsLogHighlighter.VcsCommitStyle bold() {
    return createStyle(null, null, VcsLogHighlighter.TextStyle.BOLD);
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
  public static VcsLogHighlighter.VcsCommitStyle combine(@NotNull Collection<VcsLogHighlighter.VcsCommitStyle> styles) {
    Color foreground = null;
    Color background = null;
    VcsLogHighlighter.TextStyle textStyle = null;

    for (VcsLogHighlighter.VcsCommitStyle style : styles) {
      if (foreground == null) {
        foreground = style.getForeground();
      }
      if (background == null) {
        background = style.getBackground();
      }
      if (textStyle == null) {
        textStyle = style.getTextStyle();
      }
      if (background != null && foreground != null && textStyle != null) break;
    }

    return createStyle(foreground, background, textStyle);
  }

  /**
   * Default implementation of VcsCommitStyle.
   */
  private static class VcsCommitStyleImpl implements VcsLogHighlighter.VcsCommitStyle {
    @Nullable private final Color myForeground;
    @Nullable private final Color myBackground;
    @Nullable private final VcsLogHighlighter.TextStyle myTextStyle;

    /**
     * Creates VcsCommitStyleImpl with specified text and background color, and text style.
     *
     * @param foreground text color or null if unspecified.
     * @param background background color or null if unspecified.
     * @param textStyle  text style or null if unspecified
     */
    public VcsCommitStyleImpl(@Nullable Color foreground, @Nullable Color background, @Nullable VcsLogHighlighter.TextStyle textStyle) {
      myForeground = foreground;
      myBackground = background;
      myTextStyle = textStyle;
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

    @Override
    @Nullable
    public VcsLogHighlighter.TextStyle getTextStyle() {
      return myTextStyle;
    }
  }
}
