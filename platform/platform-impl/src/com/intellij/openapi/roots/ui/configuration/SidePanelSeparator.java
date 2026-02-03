// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingConstants.LEFT;

public class SidePanelSeparator extends SeparatorWithText {
  @Override
  protected void paintComponent(Graphics g) {
    Color separatorColor = JBUI.CurrentTheme.Popup.separatorColor();
    g.setColor(separatorColor);
    if ("--".equals(getCaption())) {
      final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      final int h = getHeight() / 2;
      LinePainter2D.paint((Graphics2D)g, 30, h, getWidth() - 30, h);
      ((Graphics2D)g).setPaint(new GradientPaint(5, h, ColorUtil.toAlpha(separatorColor, 0), 30, h, separatorColor));
      LinePainter2D.paint((Graphics2D)g, 5, h, 30, h);
      ((Graphics2D)g).setPaint(
        new GradientPaint(getWidth() - 5, h, ColorUtil.toAlpha(separatorColor, 0), getWidth() - 30, h, separatorColor));
      LinePainter2D.paint((Graphics2D)g, getWidth() - 5, h, getWidth() - 30, h);
      config.restore();
      return;
    }
    Rectangle viewR = new Rectangle(0, getVgap(), getWidth() - 1, getHeight() - getVgap() - 1);
    Rectangle iconR = new Rectangle();
    Rectangle textR = new Rectangle();
    String s = SwingUtilities
      .layoutCompoundLabel(g.getFontMetrics(), getCaption(), null, CENTER,
                           LEFT,
                           CENTER,
                           LEFT,
                           viewR, iconR, textR, 0);
    GraphicsUtil.setupAAPainting(g);
    g.setColor(UIUtil.getListForeground());
    g.drawString(s, textR.x + 10, textR.y + g.getFontMetrics().getAscent());
  }
}
