/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.util.ui.UIUtil;

import javax.swing.border.LineBorder;
import java.awt.*;

public class SideBorder extends LineBorder {

  public static final int LEFT = 0x01;
  public static final int TOP = 0x02;
  public static final int RIGHT = 0x04;
  public static final int BOTTOM = 0x08;
  public static final int ALL = LEFT | TOP | RIGHT | BOTTOM;

  private final int mySideMask;
  private final boolean myDotted;

  public SideBorder(Color color, int mask) {
    this(color, mask, false);
  }

  public SideBorder(Color color, int mask, boolean dotted) {
    super(color, 1);
    mySideMask = mask;
    myDotted = dotted;
  }

  public Insets getBorderInsets(Component component) {
    return new Insets(
      (mySideMask & TOP) != 0 ? getThickness() : 0,
      (mySideMask & LEFT) != 0 ? getThickness() : 0,
      (mySideMask & BOTTOM) != 0 ? getThickness() : 0,
      (mySideMask & RIGHT) != 0 ? getThickness() : 0
    );
  }

  public Insets getBorderInsets(Component component, Insets insets) {
    insets.top = (mySideMask & TOP) != 0 ? getThickness() : 0;
    insets.left = (mySideMask & LEFT) != 0 ? getThickness() : 0;
    insets.bottom = (mySideMask & BOTTOM) != 0 ? getThickness() : 0;
    insets.right = (mySideMask & RIGHT) != 0 ? getThickness() : 0;
    return insets;
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Color oldColor = g.getColor();
    int i;

    g.setColor(getLineColor());
    for(i = 0; i < getThickness(); i++){
      if ((mySideMask & LEFT) != 0){
        drawLine(c, g, x + i, y + i, x + i, height - i - i - 1);
      }
      if ((mySideMask & TOP) != 0){
        drawLine(c, g, x + i, y + i, width - i - i - 1, y + i);
      }
      if ((mySideMask & RIGHT) != 0){
        drawLine(c, g, width - i - i - 1, y + i, width - i - i - 1, height - i - i - 1);
      }
      if ((mySideMask & BOTTOM) != 0){
        drawLine(c, g, x + i, height - i - i - 1, width - i - i - 1, height - i - i - 1);
      }
    }
    g.setColor(oldColor);
  }

  private void drawLine(Component c, Graphics g, int x1, int y1, int x2, int y2) {
    if (myDotted) {
      UIUtil.drawDottedLine((Graphics2D)g, x1, y1, x2, y2, null, getLineColor());
    }
    else {
      UIUtil.drawLine(g, x1, y1, x2, y2);
    }
  }

  public void setLineColor(Color color) {
    lineColor = color;
  }
}
