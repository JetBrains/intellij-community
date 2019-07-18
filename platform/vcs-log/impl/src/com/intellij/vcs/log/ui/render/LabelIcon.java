// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;

public class LabelIcon implements Icon {
  public static final float SIZE = 6.25f;
  private final int mySize;
  @NotNull private final List<? extends Color> myColors;
  @NotNull private final Color myBgColor;
  @NotNull private BufferedImage myImage;

  public LabelIcon(@NotNull JComponent component, int size, @NotNull Color bgColor, @NotNull List<? extends Color> colors) {
    mySize = size;
    myBgColor = bgColor;
    myColors = colors;
    myImage = createImage(component, null);
  }

  private BufferedImage createImage(Component c, Graphics2D g) {
    BufferedImage image = c != null ?
                          UIUtil.createImage(c.getGraphicsConfiguration(), getIconWidth(), getIconHeight(), BufferedImage.TYPE_INT_ARGB) :
                          UIUtil.createImage(g, getIconWidth(), getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    paintIcon(image.createGraphics());
    return image;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    if (ImageUtil.getImageScale(myImage) != JBUIScale.sysScale((Graphics2D)g)) {
      myImage = createImage(null, (Graphics2D)g);
    }
    UIUtil.drawImage(g, myImage, x, y, null);
  }

  private void paintIcon(@NotNull Graphics2D g2) {
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g2);

    float scale = mySize / SIZE;

    for (int i = myColors.size() - 1; i >= 0; i--) {
      if (i != myColors.size() - 1) {
        g2.setColor(myBgColor);
        paintTag(g2, scale, scale * 2 * i + 1, 0);
      }
      g2.setColor(myColors.get(i));
      paintTag(g2, scale, scale * 2 * i, 0);
    }

    config.restore();
  }

  public void paintTag(Graphics2D g2, float scale, float x, float y) {
    Path2D.Float path = new Path2D.Float();
    x += scale * 0.25;
    y += scale;
    path.moveTo(x, y);
    path.lineTo(x + 2 * scale, y);
    path.lineTo(x + 5 * scale, y + 3 * scale);
    path.lineTo(x + 3 * scale, y + 5 * scale);
    path.lineTo(x, y + 2 * scale);
    path.lineTo(x, y);
    path.closePath();
    Ellipse2D hole = new Ellipse2D.Float(x + 1 * scale, y + 1 * scale, scale, scale);
    Area area = new Area(path);
    area.subtract(new Area(hole));
    g2.fill(area);
  }

  @Override
  public int getIconWidth() {
    return getWidth(myColors.size());
  }

  protected int getWidth(int labelsCount) {
    return getWidth(mySize, labelsCount);
  }

  public static int getWidth(int height, int labelsCount) {
    float scale = height / SIZE;
    return Math.round((SIZE + 2 * (labelsCount - 1)) * scale);
  }

  @Override
  public int getIconHeight() {
    return mySize;
  }
}
