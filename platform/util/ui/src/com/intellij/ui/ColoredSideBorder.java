// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ui.paint.LinePainter2D;

import javax.swing.border.Border;
import java.awt.*;

public final class ColoredSideBorder implements Border {
  private final Color myLeftColor;
  private final Color myRightColor;
  private final Color myTopColor;
  private final Color myBottomColor;

  private final int myThickness;

  public ColoredSideBorder(Color topColor, Color leftColor, Color bottomColor, Color rightColor, int thickness) {
    myTopColor = topColor;
    myLeftColor = leftColor;
    myRightColor = rightColor;
    myBottomColor = bottomColor;
    myThickness = thickness;
  }

  @Override
  public Insets getBorderInsets(Component component) {
    return new Insets(
      myTopColor != null ? getThickness() : 0,
      myLeftColor != null ? getThickness() : 0,
      myBottomColor != null ? getThickness() : 0,
      myRightColor != null ? getThickness() : 0
    );
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Color oldColor = g.getColor();
    int i;

    for(i = 0; i < getThickness(); i++){
      if (myLeftColor != null){
        g.setColor(myLeftColor);
        LinePainter2D.paint((Graphics2D)g, x + i, y + i, x + i, height - i - i - 1);
      }
      if (myTopColor != null){
        g.setColor(myTopColor);
        LinePainter2D.paint((Graphics2D)g, x + i, y + i, width - i - i - 1, y + i);
      }
      if (myRightColor != null){
        g.setColor(myRightColor);
        LinePainter2D.paint((Graphics2D)g, width - i - i - 1, y + i, width - i - i - 1, height - i - i - 1);
      }
      if (myBottomColor != null){
        g.setColor(myBottomColor);
        LinePainter2D.paint((Graphics2D)g, x + i, height - i - i - 1, width - i - i - 1, height - i - i - 1);
      }
    }
    g.setColor(oldColor);
  }

  public int getThickness() {
    return myThickness;
  }
}
