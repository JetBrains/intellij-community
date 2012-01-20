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
import org.intellij.lang.annotations.JdkConstants;

import javax.swing.*;
import java.awt.*;

public class StrikeoutLabel extends JLabel{
  private boolean myStrikeout = false;

  public StrikeoutLabel(String text, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(text, horizontalAlignment);
  }

  public void setStrikeout(boolean strikeout) {
    myStrikeout = strikeout;
  }

  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (myStrikeout){
      Dimension size = getSize();
      Dimension prefSize = getPreferredSize();
      int width = Math.min(size.width, prefSize.width);
      int iconWidth = 0;
      Icon icon = getIcon();
      if (icon != null){
        iconWidth = icon.getIconWidth();
        iconWidth += getIconTextGap();
      }
      g.setColor(getForeground());
      UIUtil.drawLine(g, iconWidth, size.height / 2, width, size.height / 2);
    }
  }
}