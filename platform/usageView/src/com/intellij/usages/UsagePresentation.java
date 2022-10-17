// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.util.NlsContexts.Tooltip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public interface UsagePresentation {

  @Nullable Icon getIcon();

  @NotNull TextChunk @NotNull [] getText();

  @NotNull String getPlainText();

  @Tooltip @Nullable String getTooltipText();

  default @Nullable Color getBackgroundColor() {
    return null;
  }

  /**
   * If the implementation caches or lazy-loads the text chunks internally, this method gives it a chance to avoid
   * re-calculating it synchronously on EDT and return the possibly obsolete data.
   * <p>
   * The component using this presentation might call {@link UsagePresentation#updateCachedPresentation()} in a background
   * thread and then use {@code getCachedPresentation()} to draw the text.
   */
  default @Nullable UsageNodePresentation getCachedPresentation() {
    return new UsageNodePresentation(getIcon(), getText(), getBackgroundColor());
  }

  default void updateCachedPresentation() { }
}
