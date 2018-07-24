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
package com.intellij.util.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Vassiliy Kudryashov
 * @author Konstantin Bulenkov
 */
public class TwoColorsIcon extends ColorIcon {
  @NotNull private final Color mySecondColor;
  private static final int SQUARE_SIZE = JBUI.scale(6);
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

  public TwoColorsIcon(int size, @Nullable Color color1, @Nullable Color secondColor) {
    super(size, size, color1 != null ? color1 : Gray.TRANSPARENT, false);
    mySecondColor = secondColor != null ? secondColor : Gray.TRANSPARENT;
  }

  protected TwoColorsIcon(TwoColorsIcon icon) {
    super(icon);
    mySecondColor = icon.mySecondColor;
  }

  @NotNull
  @Override
  public TwoColorsIcon copy() {
    return new TwoColorsIcon(this);
  }

  @Override
  public void paintIcon(final Component component, Graphics g, int x, int y) {
    Graphics2D g2d = (Graphics2D)g.create();
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g2d);
    try {
      final int w = getIconWidth();
      final int h = getIconHeight();
      g2d.setPaint(getPaint(getIconColor()));
      g2d.fillPolygon(new int[]{x, x + w, x}, new int[]{y, y, y + h}, 3);
      g2d.setPaint(getPaint(mySecondColor));
      g2d.fillPolygon(new int[]{x + w, x + w, x}, new int[]{y, y + h, y + h}, 3);
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

    TwoColorsIcon icon = (TwoColorsIcon)o;

    if (getIconWidth() != icon.getIconWidth()) return false;
    if (getIconHeight() != icon.getIconHeight()) return false;
    if (!mySecondColor.equals(icon.mySecondColor)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + mySecondColor.hashCode();
    return result;
  }
}
