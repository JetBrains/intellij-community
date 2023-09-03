// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class DefaultCutStrategy implements TextCutStrategy {

  private static final int MIN_TEXT_LENGTH = 5;

  @NotNull
  @Override
  public String calcShownText(@NotNull String text, @NotNull FontMetrics metrics, int maxWidth) {
    int width = metrics.stringWidth(text);
    if (width <= maxWidth) return text;

    while (width > maxWidth && text.length() > MIN_TEXT_LENGTH) {
      text = text.substring(0, text.length() - 1);
      width = metrics.stringWidth(text + "...");
    }
    return text + "...";
  }
}
