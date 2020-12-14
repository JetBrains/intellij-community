// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class PillBorder extends LineBorder {

  public PillBorder(@NotNull Color color, int thickness) {
    super(color, thickness);
  }

  @Override
  public Insets getBorderInsets(Component c, Insets insets) {
    int scl = JBUIScale.scale(thickness);
    insets.set(scl, scl, scl, scl);
    return insets;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    Graphics2D g2d = (Graphics2D)g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    float myArcSize = DarculaUIUtil.BUTTON_ARC.getFloat();
    g.setColor(lineColor);
    RoundRectangle2D.Float area = new RoundRectangle2D.Float(x, y, width, height, myArcSize, myArcSize);
    g2d.fill(area);

    config.restore();
  }
}
