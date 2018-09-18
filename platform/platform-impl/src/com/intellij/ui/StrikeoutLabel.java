// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
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