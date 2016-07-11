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
package com.intellij.openapi.ui.impl;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Konstantin Bulenkov
 */
public class ShadowPainter {
  private final Icon myTop;
  private final Icon myTopRight;
  private final Icon myRight;
  private final Icon myBottomRight;
  private final Icon myBottom;
  private final Icon myBottomLeft;
  private final Icon myLeft;
  private final Icon myTopLeft;
  @Nullable
  private Color myBorderColor;

  public ShadowPainter(Icon top, Icon topRight, Icon right, Icon bottomRight, Icon bottom, Icon bottomLeft, Icon left, Icon topLeft) {
    myTop = top;
    myTopRight = topRight;
    myRight = right;
    myBottomRight = bottomRight;
    myBottom = bottom;
    myBottomLeft = bottomLeft;
    myLeft = left;
    myTopLeft = topLeft;
  }

  public ShadowPainter(Icon top, Icon topRight, Icon right, Icon bottomRight, Icon bottom, Icon bottomLeft, Icon left, Icon topLeft, @Nullable Color borderColor) {
    this(top, topRight, right, bottomRight, bottom, bottomLeft, left, topLeft);
    myBorderColor = borderColor;
  }

  public void setBorderColor(@Nullable Color borderColor) {
    myBorderColor = borderColor;
  }

  public BufferedImage createShadow(final JComponent c, final int width, final int height) {
    final GraphicsConfiguration graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment().
      getDefaultScreenDevice().getDefaultConfiguration();

    final BufferedImage image = graphicsConfiguration.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
    final Graphics2D g = image.createGraphics();

    paintShadow(c, g, 0, 0, width, height);

    g.dispose();
    return image;
  }

  public void paintShadow(Component c, Graphics2D g, int x, int y, int width, int height) {
    final int leftSize = myLeft.getIconWidth();
    final int rightSize = myRight.getIconWidth();
    final int bottomSize = myBottom.getIconHeight();
    final int topSize = myTop.getIconHeight();


    myTopLeft.paintIcon(c, g, x, y);
    myTopRight.paintIcon(c, g, x + width - myTopRight.getIconWidth(), y);
    myBottomRight.paintIcon(c, g, x + width - myBottomRight.getIconWidth(), y + height - myBottomRight.getIconHeight());
    myBottomLeft.paintIcon(c, g, x, y + height - myBottomLeft.getIconHeight());

    for (int _x = myTopLeft.getIconWidth(); _x < width - myTopRight.getIconWidth(); _x++) {
      myTop.paintIcon(c, g, _x + x, y);
    }
    for (int _x = myBottomLeft.getIconWidth(); _x < width - myBottomLeft.getIconWidth(); _x++) {
      myBottom.paintIcon(c, g, _x + x, y + height - bottomSize);
    }
    for (int _y = myTopLeft.getIconHeight(); _y < height - myBottomLeft.getIconHeight(); _y++) {
      myLeft.paintIcon(c, g, x, _y + y);
    }
    for (int _y = myTopRight.getIconHeight(); _y < height - myBottomRight.getIconHeight(); _y++) {
      myRight.paintIcon(c, g, x + width - rightSize, _y + y);
    }

    if (myBorderColor != null) {
      g.setColor(myBorderColor);
      g.drawRect(x + leftSize - 1, y + topSize - 1, width - leftSize - rightSize + 1, height - topSize - bottomSize + 1);
    }
  }
}
