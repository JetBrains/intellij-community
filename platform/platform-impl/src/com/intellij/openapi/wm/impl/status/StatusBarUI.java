// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ui.JBColor;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

public class StatusBarUI extends ComponentUI {
  private static final Color BORDER_TOP_COLOR = JBColor.namedColor("StatusBar.borderColor", new JBColor(0x919191, 0x919191));

  @Override
  public void paint(final Graphics g, final JComponent c) {
    Graphics2D g2d = (Graphics2D) g.create();
    Rectangle r = new Rectangle(c.getSize());
    try {
      g2d.setColor(getBackground());
      g2d.fill(r);

      g2d.setColor(BORDER_TOP_COLOR);
      RectanglePainter2D.FILL.paint(g2d, r.x, r.y, r.width, 1);
    }
    finally {
      g2d.dispose();
    }
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return JBUI.size(100, 23);
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return JBUI.size(Integer.MAX_VALUE, 23);
  }

  public @NotNull Color getBackground() {
    return UIUtil.getPanelBackground();
  }
}
