// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

public final class VerticalSeparatorComponent extends JComponent {
  @Override
  public Dimension getPreferredSize() {
    int gap = JBUIScale.scale(2);
    int center = JBUIScale.scale(3);
    int width = gap * 2 + center;
    int height = JBUIScale.scale(24);

    return new JBDimension(width, height, true);
  }

  @Override
  protected void paintComponent(final Graphics g) {
    if (getParent() == null) return;

    int gap = JBUIScale.scale(2);
    int center = JBUIScale.scale(3);
    g.setColor(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground());
    int y2 = getHeight() - gap * 2;
    LinePainter2D.paint((Graphics2D)g, center, gap, center, y2);
  }
}
