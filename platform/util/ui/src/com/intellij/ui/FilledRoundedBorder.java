// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

public class FilledRoundedBorder extends LineBorder {
  private final int myArcSize;

  public FilledRoundedBorder(@NotNull Color color, int arcSize, int thickness) {
    super(color, thickness);
    myArcSize = arcSize;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    Graphics2D g2d = (Graphics2D)g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    g.setColor(lineColor);
    RoundRectangle2D.Float area = new RoundRectangle2D.Float(x, y, width, height, myArcSize, myArcSize);
    g2d.fill(area);

    config.restore();
  }
}
