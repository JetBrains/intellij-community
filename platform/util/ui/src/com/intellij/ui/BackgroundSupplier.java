// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import java.awt.Color;

public interface BackgroundSupplier {
  /**
   * This method allows to specify a background for a specified element (list item or tree node).
   * Note, that this method is called from the Event Dispatch Thread during painting.
   *
   * @param row a visible index of an element that allows to define old fashioned striped background
   * @return a preferred background color or {@code null} if an element should use default background
   */
  @Nullable
  Color getElementBackground(int row);
}
