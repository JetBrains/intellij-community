/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

public class LabelIcon implements Icon {
  private final int mySize;
  @NotNull private final Color[] myColors;
  @NotNull private final Color myBgColor;
  @NotNull private final BufferedImage myImage;

  public LabelIcon(int size, @NotNull Color bgColor, @NotNull Color... colors) {
    mySize = size;
    myBgColor = bgColor;
    myColors = colors;

    myImage = UIUtil.createImage(getIconWidth(), getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    paintIcon(myImage.createGraphics());
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    UIUtil.drawImage(g, myImage, x, y, null);
  }

  private void paintIcon(@NotNull Graphics2D g2) {
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g2);

    float scale = mySize / 8.0f;

    for (int i = myColors.length - 1; i >= 0; i--) {
      if (i != myColors.length - 1) {
        g2.setColor(myBgColor);
        paintTag(g2, scale, scale * 2 * i + 1, 0);
      }
      g2.setColor(myColors[i]);
      paintTag(g2, scale, scale * 2 * i, 0);
    }

    config.restore();
  }

  public void paintTag(Graphics2D g2, float scale, float x, float y) {
    Path2D.Float path = new Path2D.Float();
    path.moveTo(x + 1 * scale, y + 2 * scale);
    path.lineTo(x + 3 * scale, y + 2 * scale);
    path.lineTo(x + 6 * scale, y + 5 * scale);
    path.lineTo(x + 4 * scale, y + 7 * scale);
    path.lineTo(x + 1 * scale, y + 4 * scale);
    path.lineTo(x + 1 * scale, y + 2 * scale);
    path.closePath();
    Ellipse2D hole = new Ellipse2D.Float(x + 2 * scale, y + 3 * scale, scale, scale);
    Area area = new Area(path);
    area.subtract(new Area(hole));
    g2.fill(area);
  }

  @Override
  public int getIconWidth() {
    return getWidth(myColors.length);
  }

  protected int getWidth(int labelsCount) {
    return getWidth(mySize, labelsCount);
  }

  public static int getWidth(int height, int labelsCount) {
    float scale = height / 8.0f;
    return Math.round((7 + 2 * (labelsCount - 1)) * scale);
  }

  @Override
  public int getIconHeight() {
    return mySize;
  }
}
