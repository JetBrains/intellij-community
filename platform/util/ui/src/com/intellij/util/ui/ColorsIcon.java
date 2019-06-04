// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * @author Vassiliy Kudryashov
 * @author Konstantin Bulenkov
 */
public class ColorsIcon extends ColorIcon {
  private static final int SQUARE_SIZE = JBUIScale.scale(6);
  private static final BufferedImage CHESS_IMAGE = UIUtil.createImage(SQUARE_SIZE, SQUARE_SIZE, BufferedImage.TYPE_INT_RGB);
  private static final TexturePaint CHESS;

  static {
    Graphics2D graphics = CHESS_IMAGE.createGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    graphics.setColor(JBColor.LIGHT_GRAY);
    graphics.fillRect(0, 0, SQUARE_SIZE + 1, SQUARE_SIZE + 1);
    graphics.setColor(JBColor.GRAY);
    graphics.fillRect(0, 0, SQUARE_SIZE / 2, SQUARE_SIZE / 2);
    graphics.fillRect(SQUARE_SIZE / 2, SQUARE_SIZE / 2, SQUARE_SIZE / 2, SQUARE_SIZE / 2);
    graphics.dispose();
    CHESS = new TexturePaint(CHESS_IMAGE, new Rectangle(0, 0, SQUARE_SIZE, SQUARE_SIZE));
  }

  private final Color[] myColors;

  public ColorsIcon(int size, @NotNull Color... colors) {
    super(size, size, Gray.TRANSPARENT, false);
    myColors = ArrayUtil.reverseArray(colors);
  }

  protected ColorsIcon(ColorsIcon icon) {
    super(icon);
    myColors = icon.myColors;
  }

  @NotNull
  @Override
  public ColorsIcon copy() {
    return new ColorsIcon(this);
  }

  @Override
  public void paintIcon(final Component component, Graphics g, int x, int y) {
    Graphics2D g2d = (Graphics2D)g.create();
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g2d);
    try {
      final int w = getIconWidth();
      final int h = getIconHeight();
      if (myColors.length == 2) {
        g2d.setPaint(getPaint(myColors[0]));
        g2d.fillPolygon(new int[]{x, x + w, x}, new int[]{y, y, y + h}, 3);
        g2d.setPaint(getPaint(myColors[1]));
        g2d.fillPolygon(new int[]{x + w, x + w, x}, new int[]{y, y + h, y + h}, 3);
      }
      else {
        for (int i = 0; i < myColors.length; i++) {
          g2d.setPaint(getPaint(myColors[i]));
          RectanglePainter.FILL.paint(g2d,
                                      i % 2 == 0 ? x : x + w / 2 + 1,
                                      i < 2 ? y : y + h / 2 + 1,
                                      w / 2 - 1,
                                      h / 2 - 1, null);
          if (i ==3) break;
        }
      }
    }
    catch (Exception e) {
      g2d.dispose();
    }
    finally {
      config.restore();
    }
  }

  protected Paint getPaint(Color color) {
    return color == null || color.getAlpha() == 0 ? CHESS : color;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ColorsIcon icon = (ColorsIcon)o;

    if (getIconWidth() != icon.getIconWidth()) return false;
    if (getIconHeight() != icon.getIconHeight()) return false;
    if (!Arrays.equals(myColors, icon.myColors)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Arrays.hashCode(myColors);
    return result;
  }
}
