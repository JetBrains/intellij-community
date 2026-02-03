// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.editor.ex;

import com.intellij.codeInsight.hint.TooltipRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface ErrorStripTooltipRendererProvider {
  /**
   * @return composite tooltip for all highlighters passed, or null if no highlighter has a tooltip to show.
   * This method must be called in a background thread to avoid freezes during expensive calculations.
   */
  @Nullable
  @RequiresBackgroundThread
  TooltipRenderer calcTooltipRenderer(@NotNull Collection<? extends RangeHighlighter> highlighters);

  @NotNull
  TooltipRenderer calcTooltipRenderer(@NlsContexts.Tooltip @NotNull String text);

  @NotNull
  TooltipRenderer calcTooltipRenderer(@NlsContexts.Tooltip @NotNull String text, int width);

  default @NotNull TooltipRenderer calcTooltipRenderer(@NlsContexts.Tooltip String text, @Nullable TooltipAction action, int width) {
    return calcTooltipRenderer(text, width);
  }
}