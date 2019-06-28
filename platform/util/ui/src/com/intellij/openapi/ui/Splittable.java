// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

public interface Splittable {
  float getMinProportion(boolean first);

  void setProportion(float proportion);

  /**
   * @return {@code true} if splitter has vertical orientation, {@code false} otherwise
   */
  boolean getOrientation();

  /**
   * @param verticalSplit {@code true} means that splitter will have vertical split
   */
  void setOrientation(boolean verticalSplit);

  @NotNull
  Component asComponent();

  void setDragging(boolean dragging);
}
