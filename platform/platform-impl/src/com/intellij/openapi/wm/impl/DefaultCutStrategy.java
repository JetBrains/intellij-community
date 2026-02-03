// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public final class DefaultCutStrategy implements TextCutStrategy {

  private static final int MIN_TEXT_LENGTH = 4;

  @Override
  public @NotNull String calcShownText(@NotNull String text, @NotNull FontMetrics metrics, int maxWidth, @NotNull JComponent c) {
    int width = UIUtil.computeStringWidth(c, metrics, text);
    if (width <= maxWidth) return text;

    while (width > maxWidth && text.length() > MIN_TEXT_LENGTH) {
      text = text.substring(0, text.length() - 1);
      width = UIUtil.computeStringWidth(c, metrics, text + "...");
    }
    return text + "...";
  }

  @Override
  public int calcMinTextWidth(@NotNull String text, @NotNull FontMetrics metrics, @NotNull JComponent c) {
    int textWidth = UIUtil.computeStringWidth(c, metrics, text);
    return UIUtil.computeTextComponentMinimumSize(textWidth, text, metrics, MIN_TEXT_LENGTH);
  }
}
