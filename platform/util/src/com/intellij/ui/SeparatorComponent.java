/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.util.ui.JBUI;

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

  @Override
  protected void paintComponent(Graphics g) {
    if (!isVisible()) return;

    if (myColor == null) return;

    g.setColor(myColor);
    if (myOrientation != SeparatorOrientation.VERTICAL) {
      g.drawLine(myHGap, myVGap, getWidth() - myHGap - 1, myVGap);
      if (myShadow != null) {
        g.setColor(myShadow);
        g.drawLine(myHGap + 1, myVGap + 1, getWidth() - myHGap, myVGap + 1);
      }
    } else {
      g.drawLine(myHGap, myVGap, myHGap, getHeight() - myVGap - 1);
      if (myShadow != null) {
        g.setColor(myShadow);
        g.drawLine(myHGap + 1, myVGap + 1, myHGap + 1, getHeight() - myVGap);
      }
    }

  }

  @Override
  public Dimension getPreferredSize() {
    if (myOrientation != SeparatorOrientation.VERTICAL) {
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