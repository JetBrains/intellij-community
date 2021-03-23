// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingConstants;
import java.awt.FontMetrics;

public enum SwingTextTrimmer {
  ELLIPSIS_AT_LEFT(StringUtil.ELLIPSIS, SwingConstants.LEFT),
  ELLIPSIS_AT_RIGHT(StringUtil.ELLIPSIS, SwingConstants.RIGHT),
  ELLIPSIS_IN_CENTER(StringUtil.ELLIPSIS, SwingConstants.CENTER),
  THREE_DOTS_AT_LEFT(StringUtil.THREE_DOTS, SwingConstants.LEFT),
  THREE_DOTS_AT_RIGHT(StringUtil.THREE_DOTS, SwingConstants.RIGHT),
  THREE_DOTS_IN_CENTER(StringUtil.THREE_DOTS, SwingConstants.CENTER);

  public static final Key<SwingTextTrimmer> KEY = Key.create(SwingTextTrimmer.class.getSimpleName());
  private final String ellipsis;
  private final int alignment;

  SwingTextTrimmer(@NotNull String ellipsis, int alignment) {
    this.ellipsis = ellipsis;
    this.alignment = alignment;
  }

  public @NotNull String trim(@Nullable String text, @NotNull FontMetrics metrics, int availableWidth) {
    if (text == null || availableWidth <= 0) return "";
    if (isFit(text, metrics, availableWidth)) return text;
    int ellipsisWidth = metrics.stringWidth(ellipsis);
    if (availableWidth <= ellipsisWidth) return ellipsis;
    int width = availableWidth - ellipsisWidth;
    if (alignment == SwingConstants.LEFT) return ellipsis + trimLeft(text, metrics, width);
    if (alignment == SwingConstants.RIGHT) return trimRight(text, metrics, width) + ellipsis;
    String postfix = trimRight(text, metrics, width / 2) + ellipsis;
    return postfix + trimLeft(text, metrics, availableWidth - metrics.stringWidth(postfix));
  }

  private static boolean isFit(@NotNull String text, @NotNull FontMetrics metrics, int width) {
    return text.isEmpty() || metrics.stringWidth(text) <= width;
  }

  private static @NotNull String trimLeft(@NotNull String text, @NotNull FontMetrics metrics, int width) {
    int min = 0, max = text.length();
    while (true) {
      int pos = max - (max - min) / 2;
      String str = text.substring(pos);
      if (pos == max) return str;
      if (isFit(str, metrics, width)) {
        max = pos;
      }
      else {
        min = pos;
      }
    }
  }

  private static @NotNull String trimRight(@NotNull String text, @NotNull FontMetrics metrics, int width) {
    int min = 0, max = text.length();
    while (true) {
      int pos = min + (max - min) / 2;
      String str = text.substring(0, pos);
      if (pos == min) return str;
      if (isFit(str, metrics, width)) {
        min = pos;
      }
      else {
        max = pos;
      }
    }
  }
}
