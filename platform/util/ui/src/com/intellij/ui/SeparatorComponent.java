// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.*;

public class SeparatorComponent extends JComponent {
  private int myVGap = 3;
  private Color myColor = Color.lightGray;
  private Color myShadow = Gray._240;
  private int myHGap = 1;
  private SeparatorOrientation myOrientation = SeparatorOrientation.HORIZONTAL;

  public SeparatorComponent() {

  }

  public SeparatorComponent(int aVerticalGap) {
    myVGap = aVerticalGap;
    setBorder(JBUI.Borders.empty(myVGap, 0));
  }

  public SeparatorComponent(int aVerticalGap, int aHorizontalGap) {
    myVGap = aVerticalGap;
    myHGap = aHorizontalGap;
    setBorder(JBUI.Borders.empty(myVGap, 0));
  }

  public SeparatorComponent(int aVerticalGap, Color aColor, Color aShadowColor) {
    this(aVerticalGap, 1, aColor, aShadowColor);
  }

  public SeparatorComponent(int aVerticalGap, int horizontalGap, Color aColor, Color aShadowColor) {
    myVGap = aVerticalGap;
    myHGap = horizontalGap;
    myColor = aColor;
    myShadow = aShadowColor;
    setBorder(JBUI.Borders.empty(myVGap, 0));
  }

  public SeparatorComponent(Color color, SeparatorOrientation orientation) {
    myColor = color;
    myOrientation = orientation;
    myShadow = null;
    myHGap = 0;
    myVGap = 0;
  }

  public void setVGap(int VGap) {
    myVGap = VGap;
  }

  public void setHGap(int HGap) {
    myHGap = HGap;
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (!isVisible()) return;

    if (myColor == null) return;

    g.setColor(myColor);
    if (myOrientation == SeparatorOrientation.HORIZONTAL) {
      int length = getWidth() - myHGap * 2;
      if (myShadow != null) length--;
      paintHorizontalLine(g, myHGap, myVGap, length);
      if (myShadow != null) {
        g.setColor(myShadow);
        paintHorizontalLine(g, myHGap + 1, myVGap + 1, length);
      }
    }
    else {
      int length = getHeight() - myVGap * 2;
      if (myShadow != null) length--;
      paintVerticalLine(g, myHGap, myVGap, length);
      if (myShadow != null) {
        g.setColor(myShadow);
        paintVerticalLine(g, myHGap + 1, myVGap + 1, length);
      }
    }
  }

  @ApiStatus.Internal
  public SeparatorOrientation getOrientation() {
    return myOrientation;
  }

  private static void paintHorizontalLine(Graphics g, int x, int y, int length) {
    LinePainter2D.paint((Graphics2D)g, x, y, x + length, y, LinePainter2D.StrokeType.CENTERED, 1.);
  }

  private static void paintVerticalLine(Graphics g, int x, int y, int length) {
    LinePainter2D.paint((Graphics2D)g, x, y, x, y + length, LinePainter2D.StrokeType.CENTERED, 1.);
  }

  @Override
  public Dimension getPreferredSize() {
    if (myOrientation == SeparatorOrientation.HORIZONTAL) {
      return new Dimension(0, myVGap * 2 + 1);
    }
    else {
      return new Dimension(myHGap * 2 + 1, 1 + ((myShadow != null) ? 1 : 0));
    }
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    Dimension size = getPreferredSize();
    if (myOrientation != SeparatorOrientation.VERTICAL) {
      size.width = Integer.MAX_VALUE;
    }
    else {
      size.height = Integer.MAX_VALUE;
    }
    return size;
  }

}