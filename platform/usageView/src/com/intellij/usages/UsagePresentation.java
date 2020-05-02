// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.util.NlsContexts.Tooltip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface UsagePresentation {
  TextChunk @NotNull [] getText();

  /**
   * If the implementation caches or lazy-loades the text chunks internally, this method gives it a chance to avoid
   * re-calculating it synchronously on EDT and return the possibly obsolete data.
   *
   * The component using this presentation might call {@link UsagePresentation#updateCachedText()} in a background
   * thread and then use {@link UsagePresentation#getCachedText()} to draw the text.
   */
  default TextChunk @Nullable [] getCachedText() {
    return getText();
  }

  default void updateCachedText() {}

  @NotNull
  String getPlainText();

  Icon getIcon();

  @Tooltip
  String getTooltipText();
}
