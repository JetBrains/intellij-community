// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.SwingConstants;
import java.awt.FontMetrics;

import static com.intellij.util.ui.SwingTextTrimmer.isFit;

final class StandardTrimmerStrategy implements SwingTextTrimmerStrategy {
  private final String ellipsis;
  private final int alignment;
  private final float ratio;

  StandardTrimmerStrategy(@NotNull String ellipsis, int alignment) {
    this(ellipsis, alignment, 0.5f);
  }

  StandardTrimmerStrategy(@NotNull String ellipsis, int alignment, float ratio) {
    this.ellipsis = ellipsis;
    this.alignment = alignment;
    this.ratio = ratio;
  }

  @Override
  public @NotNull String trim(@NotNull String text, @NotNull FontMetrics metrics, int availableWidth) {
    int ellipsisWidth = metrics.stringWidth(ellipsis);
    if (availableWidth <= ellipsisWidth) return ellipsis;
    int width = availableWidth - ellipsisWidth;
    if (alignment == SwingConstants.LEFT) return ellipsis + trimLeft(text, metrics, width);
    if (alignment == SwingConstants.RIGHT) return trimRight(text, metrics, width) + ellipsis;
    String postfix = trimRight(text, metrics, (int)(width * ratio)) + ellipsis;
    return postfix + trimLeft(text, metrics, availableWidth - metrics.stringWidth(postfix));
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
