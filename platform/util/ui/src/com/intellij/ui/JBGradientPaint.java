// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public final class JBGradientPaint extends GradientPaint {
  public enum GradientDirection {
    TOP_BOTTOM,
    LEFT_RIGHT,
    TOP_LEFT_BOTTOM_RIGHT,
    BOTTOM_LEFT_TOP_RIGHT
  }

  public JBGradientPaint(JComponent c, Color color1, Color color2) {
    this(c, GradientDirection.TOP_BOTTOM, color1, color2);
  }

  public JBGradientPaint(JComponent c, GradientDirection direction, Color color1, Color color2) {
    super(getX1(c, direction), getY1(c, direction), color1, getX2(c, direction), getY2(c, direction), color2);
  }

  @SuppressWarnings("UnusedParameters")
  private static float getX1(JComponent c, GradientDirection d) {
    return 0;
  }

  private static float getY1(JComponent c, GradientDirection d) {
    return d == GradientDirection.BOTTOM_LEFT_TOP_RIGHT ? c.getHeight() : 0;
  }

  private static float getX2(JComponent c, GradientDirection d) {
    return d == GradientDirection.TOP_BOTTOM ? 0 : c.getWidth();
  }

  private static float getY2(JComponent c, GradientDirection d) {
    return d == GradientDirection.BOTTOM_LEFT_TOP_RIGHT ||
           d == GradientDirection.LEFT_RIGHT ? 0 : c.getHeight();
  }
}
