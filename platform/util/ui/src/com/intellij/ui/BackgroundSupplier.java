// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ui.render.RenderingUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface BackgroundSupplier {
  /**
   * This method allows to specify a background for a specified element (list item or tree node).
   * Note, that this method is called from the Event Dispatch Thread during painting.
   *
   * @param row a visible index of an element that allows to define old fashioned striped background
   * @return a preferred background color or {@code null} if an element should use default background
   */
  @Nullable
  default Color getElementBackground(int row) { return null; }

  /**
   * This method allows specifying a background for a selected element (list item or tree node).
   *
   * @param row a visible index of an element that allows to define old-fashioned striped background
   * @return color for a selected element or {@code null} if an element should use default background (@see {@link RenderingUtil#getBackground})
   */
  @Nullable
  default Color getSelectedElementBackground(int row) { return null; }
}
