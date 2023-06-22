// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public interface ScalableIcon extends Icon {
  /**
   * @return the scale of the icon relative to its origin
   */
  float getScale();

  /**
   * Creates and returns a scaled instance of the icon.
   * The icon is scaled relative to its origin, that is {@code scale(1f)} will
   * return the icon in its original scale.
   * <p>
   * To scale this instance by {@code n} times: {@code scale(n * getScale())}.
   * <p>
   * Note that the methods {@link #getIconWidth()} and {@link #getIconHeight()}
   * should return the scaled size of the icon.
   *
   * @param scaleFactor scale
   * @return scaled icon instance
   */
  @NotNull Icon scale(float scaleFactor);
}
