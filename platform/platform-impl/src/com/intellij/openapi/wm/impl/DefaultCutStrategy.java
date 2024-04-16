// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.ui.paint.PaintUtil.getStringWidth;

public final class DefaultCutStrategy implements TextCutStrategy {

  private static final int MIN_TEXT_LENGTH = 5;

  @NotNull
  @Override
  public String calcShownText(@NotNull String text, @NotNull FontMetrics metrics, int maxWidth, @NotNull Graphics g) {
    int width = getStringWidth(text, g, metrics);
    if (width <= maxWidth) return text;

    while (width > maxWidth && text.length() > MIN_TEXT_LENGTH) {
      text = text.substring(0, text.length() - 1);
      width = getStringWidth(text + "...", g, metrics);
    }
    return text + "...";
  }

  @Override
  public int calcMinTextWidth(@NotNull String text, @NotNull FontMetrics metrics) {
    if (text.length() < MIN_TEXT_LENGTH) return metrics.stringWidth(text);
    text = text.substring(0, MIN_TEXT_LENGTH - 1);
    return metrics.stringWidth(text + "...");
  }
}
