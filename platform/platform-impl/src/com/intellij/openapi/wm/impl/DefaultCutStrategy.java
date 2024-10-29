// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.ui.paint.PaintUtil.getStringWidth;

@ApiStatus.Internal
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
    return UIUtil.computeTextComponentMinimumSize(metrics.stringWidth(text), text, metrics, MIN_TEXT_LENGTH - 1);
  }
}
