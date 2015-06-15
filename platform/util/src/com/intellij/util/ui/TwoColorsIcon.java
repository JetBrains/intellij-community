/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * User: Vassiliy.Kudryashov
 */
public class TwoColorsIcon extends EmptyIcon {
  @NotNull private final Paint myColor1;
  @NotNull private final Paint myColor2;
  private static final int SQUARE_SIZE = 6;
  private static final BufferedImage CHESS_IMAGE = UIUtil.createImage(SQUARE_SIZE, SQUARE_SIZE, BufferedImage.TYPE_INT_RGB);
  static {
    Graphics2D graphics = CHESS_IMAGE.createGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    graphics.setColor(Color.LIGHT_GRAY);
    graphics.fillRect(0, 0, SQUARE_SIZE + 1, SQUARE_SIZE + 1);
    graphics.setColor(Color.GRAY);
    graphics.fillRect(0, 0, SQUARE_SIZE / 2, SQUARE_SIZE / 2);
    graphics.fillRect(SQUARE_SIZE / 2, SQUARE_SIZE / 2, SQUARE_SIZE / 2, SQUARE_SIZE / 2);
  }
  private TexturePaint CHESS = new TexturePaint(CHESS_IMAGE, new Rectangle(0, 0, SQUARE_SIZE, SQUARE_SIZE));

  public TwoColorsIcon(int size, @Nullable Color color1, @Nullable Color color2) {
    super(size, size);
    myColor1 = color1 != null ? color1 : CHESS;
    myColor2 = color2 != null ? color2 : CHESS;
  }

  @Override
  public void paintIcon(final Component component, final Graphics g, final int x, final int y) {
    Graphics2D g2d = (Graphics2D)g.create();
    try {
      final int w = getIconWidth();
      final int h = getIconHeight();
      GraphicsUtil.setupAAPainting(g2d);
      g2d.setPaint(myColor1);
      g2d.fillPolygon(new int[]{x, x + w, x}, new int[]{y, y, y + h}, 3);
      g2d.setPaint(myColor2);
      g2d.fillPolygon(new int[]{x + w, x + w, x}, new int[]{y, y + h, y + h}, 3);
    }
    catch (Exception e) {
      g2d.dispose();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    TwoColorsIcon icon = (TwoColorsIcon)o;

    if (getIconWidth() != icon.getIconWidth()) return false;
    if (getIconHeight() != icon.getIconHeight()) return false;
    if (!myColor1.equals(icon.myColor1)) return false;
    if (!myColor2.equals(icon.myColor2)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myColor1.hashCode();
    result = 31 * result + myColor2.hashCode();
    return result;
  }
}
