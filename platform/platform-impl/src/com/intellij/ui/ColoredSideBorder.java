/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import javax.swing.border.Border;
import java.awt.*;

public class ColoredSideBorder implements Border {
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

  public Insets getBorderInsets(Component component) {
    return new Insets(
      myTopColor != null ? getThickness() : 0,
      myLeftColor != null ? getThickness() : 0,
      myBottomColor != null ? getThickness() : 0,
      myRightColor != null ? getThickness() : 0
    );
  }

  public boolean isBorderOpaque() {
    return true;
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Color oldColor = g.getColor();
    int i;

    for(i = 0; i < getThickness(); i++){
      if (myLeftColor != null){
        g.setColor(myLeftColor);
        UIUtil.drawLine(g, x + i, y + i, x + i, height - i - i - 1);
      }
      if (myTopColor != null){
        g.setColor(myTopColor);
        UIUtil.drawLine(g, x + i, y + i, width - i - i - 1, y + i);
      }
      if (myRightColor != null){
        g.setColor(myRightColor);
        UIUtil.drawLine(g, width - i - i - 1, y + i, width - i - i - 1, height - i - i - 1);
      }
      if (myBottomColor != null){
        g.setColor(myBottomColor);
        UIUtil.drawLine(g, x + i, height - i - i - 1, width - i - i - 1, height - i - i - 1);
      }
    }
    g.setColor(oldColor);
  }

  public int getThickness() {
    return myThickness;
  }
}
