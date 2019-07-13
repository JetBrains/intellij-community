// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import java.awt.*;

/**
 * Draws a 'wavy' line of 1-pixel amplitude. Instances are cached for performance reasons.
 * <p/>
 * This class is not thread-safe, it's supposed to be used in EDT only.
 *
 * @see WavePainter2D
 */
public abstract class WavePainter {
  protected WavePainter() {}

  /**
   * Paints a wave in given coordinate range. {@code y} defines the lower boundary of painted wave.
   */
  public abstract void paint(Graphics2D g, int xStart, int xEnd, int y);

  public static WavePainter forColor(Color color) {
    return WavePainter2D.forColor(color);
  }
}
