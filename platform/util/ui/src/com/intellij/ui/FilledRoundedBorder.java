// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class FilledRoundedBorder extends LineBorder {
  private final int myArcSize;
  private final float bw;

  public FilledRoundedBorder(@NotNull Color color, int arcSize, int thickness, float bw) {
    super(color, thickness);
    myArcSize = arcSize;
    this.bw = bw;
  }

  public FilledRoundedBorder(@NotNull Color color, int arcSize, int thickness) {
    this(color, arcSize, thickness, 0);
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    Graphics2D g2d = (Graphics2D)g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    g.setColor(lineColor);
    RoundRectangle2D.Float area = new RoundRectangle2D.Float(x + bw, y+bw, width- (bw * 2), height- (bw * 2), myArcSize, myArcSize);
    g2d.fill(area);

    config.restore();
  }
}
