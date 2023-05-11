/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  @NotNull
  default TooltipRenderer calcTooltipRenderer(@NlsContexts.Tooltip String text, @Nullable TooltipAction action, int width) {
    return calcTooltipRenderer(text, width);
  }
}