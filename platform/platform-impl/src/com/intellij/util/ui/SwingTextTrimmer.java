// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingConstants;
import java.awt.FontMetrics;

public class SwingTextTrimmer {
  public static final @NotNull SwingTextTrimmer ELLIPSIS_AT_LEFT = new SwingTextTrimmer(new StandardTrimmerStrategy(StringUtil.ELLIPSIS, SwingConstants.LEFT));
  public static final @NotNull SwingTextTrimmer ELLIPSIS_AT_RIGHT = new SwingTextTrimmer(new StandardTrimmerStrategy(StringUtil.ELLIPSIS, SwingConstants.RIGHT));
  public static final @NotNull SwingTextTrimmer ELLIPSIS_IN_CENTER = new SwingTextTrimmer(new StandardTrimmerStrategy(StringUtil.ELLIPSIS, SwingConstants.CENTER));
  public static final @NotNull SwingTextTrimmer THREE_DOTS_AT_LEFT = new SwingTextTrimmer(new StandardTrimmerStrategy(StringUtil.THREE_DOTS, SwingConstants.LEFT));
  public static final @NotNull SwingTextTrimmer THREE_DOTS_AT_RIGHT = new SwingTextTrimmer(new StandardTrimmerStrategy(StringUtil.THREE_DOTS, SwingConstants.RIGHT));
  public static final @NotNull SwingTextTrimmer THREE_DOTS_IN_CENTER = new SwingTextTrimmer(new StandardTrimmerStrategy(StringUtil.THREE_DOTS, SwingConstants.CENTER));

  public static final @NotNull Key<SwingTextTrimmer> KEY = Key.create(SwingTextTrimmer.class.getSimpleName());

  public static @NotNull SwingTextTrimmer createCenterTrimmer(float ratio) {
    return new SwingTextTrimmer(new StandardTrimmerStrategy(StringUtil.ELLIPSIS, SwingConstants.CENTER, ratio));
  }

  public static @NotNull SwingTextTrimmer createCustomTrimmer(@NotNull SwingTextTrimmerStrategy strategy) {
    return new SwingTextTrimmer(strategy);
  }

  private final @NotNull SwingTextTrimmerStrategy strategy;
  private boolean trimmed;

  SwingTextTrimmer(@NotNull SwingTextTrimmerStrategy strategy) {
    this.strategy = strategy;
  }

  public @NotNull String trim(@Nullable String text, @NotNull FontMetrics metrics, int availableWidth) {
    if (text == null || availableWidth <= 0) return "";
    trimmed = !isFit(text, metrics, availableWidth);
    if (!trimmed) return text;
    return strategy.trim(text, metrics, availableWidth);
  }

  public boolean isTrimmed() {
    return trimmed;
  }

  @ApiStatus.Internal
  public void setTrimmed(boolean trimmed) {
    this.trimmed = trimmed;
  }

  static boolean isFit(@NotNull String text, @NotNull FontMetrics metrics, int width) {
    return text.isEmpty() || metrics.stringWidth(text) <= width;
  }
}
