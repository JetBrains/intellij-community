// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

public class StatusBarUI extends ComponentUI {
  private static final Dimension MAX_SIZE = new Dimension(Integer.MAX_VALUE, 23);
  private static final Dimension MIN_SIZE = new Dimension(100, 23);

  private static final Border BACKGROUND_PAINTER = new BackgroundPainter();

  @Override
  public void paint(final Graphics g, final JComponent c) {
    final Rectangle bounds = c.getBounds();
    BACKGROUND_PAINTER.paintBorder(c, g, 0, 0, bounds.width, bounds.height);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return MIN_SIZE; // TODO
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return MAX_SIZE;
  }

  private static final class BackgroundPainter implements Border {
    private static final Color BORDER_TOP_COLOR = Gray._145;
    private static final Color BORDER2_TOP_COLOR = Gray._255;
    private static final Color BORDER_BOTTOM_COLOR = Gray._255;

    private static final Insets INSETS = new Insets(0, 0, 0, 0);

    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      final Graphics2D g2d = (Graphics2D) g.create();

      final Color background = UIUtil.getPanelBackground();

      g2d.setColor(background);
      g2d.fillRect(0, 0, width, height);

      Color topColor = UIUtil.isUnderDarcula() ? BORDER_TOP_COLOR.darker().darker() : BORDER_TOP_COLOR;
      if (SystemInfo.isMac && UIUtil.isUnderIntelliJLaF()) {
        topColor = Gray.xC9;
      }
      g2d.setColor(topColor);
      UIUtil.drawLine(g2d, 0, 0, width, 0);

      if (!UIUtil.isUnderDarcula()) {
        g2d.setColor(BORDER_BOTTOM_COLOR);
        UIUtil.drawLine(g2d, 0, height, width, height);
      }

      g2d.dispose();
    }

    public Insets getBorderInsets(Component c) {
      return (Insets)INSETS.clone();
    }

    public boolean isBorderOpaque() {
      return true;
    }
  }
}
