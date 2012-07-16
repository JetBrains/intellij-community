/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import org.jdesktop.swingx.graphics.GraphicsUtilities;
import org.jdesktop.swingx.graphics.ShadowRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author spleaner
 * @author Konstantin Bulenkov
 */
public class ShadowBorderPainter {
  private static final Icon TOP = AllIcons.Ide.Shadow.Top;
  private static final Icon TOP_RIGHT = AllIcons.Ide.Shadow.Top_right;
  private static final Icon RIGHT = AllIcons.Ide.Shadow.Right;
  private static final Icon BOTTOM_RIGHT = AllIcons.Ide.Shadow.Bottom_right;
  private static final Icon BOTTOM = AllIcons.Ide.Shadow.Bottom;
  private static final Icon BOTTOM_LEFT = AllIcons.Ide.Shadow.Bottom_left;
  private static final Icon LEFT = AllIcons.Ide.Shadow.Left;
  private static final Icon TOP_LEFT = AllIcons.Ide.Shadow.Top_left;

  public static final int SIDE_SIZE = 35;
  public static final int TOP_SIZE = 20;
  public static final int BOTTOM_SIZE = 49;

  private static final Icon POPUP_TOP = AllIcons.Ide.Shadow.Popup.Top;
  private static final Icon POPUP_TOP_RIGHT = AllIcons.Ide.Shadow.Popup.Top_right;
  private static final Icon POPUP_RIGHT = AllIcons.Ide.Shadow.Popup.Right;
  private static final Icon POPUP_BOTTOM_RIGHT = AllIcons.Ide.Shadow.Popup.Bottom_right;
  private static final Icon POPUP_BOTTOM = AllIcons.Ide.Shadow.Popup.Bottom;
  private static final Icon POPUP_BOTTOM_LEFT = AllIcons.Ide.Shadow.Popup.Bottom_left;
  private static final Icon POPUP_LEFT = AllIcons.Ide.Shadow.Popup.Left;
  private static final Icon POPUP_TOP_LEFT = AllIcons.Ide.Shadow.Popup.Top_left;

  public static final int POPUP_SIDE_SIZE = 7;
  public static final int POPUP_TOP_SIZE = 4;
  public static final int POPUP_BOTTOM_SIZE = 10;

  private ShadowBorderPainter() {
  }

  public static BufferedImage createShadow(final JComponent c, final int width, final int height, boolean isPopup) {
    final GraphicsConfiguration graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment().
      getDefaultScreenDevice().getDefaultConfiguration();

    final BufferedImage image = graphicsConfiguration.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
    final Graphics2D g = image.createGraphics();

    final Icon topLeft = isPopup ? POPUP_TOP_LEFT : TOP_LEFT;
    final Icon topRight = isPopup ? POPUP_TOP_RIGHT : TOP_RIGHT;
    final Icon bottom = isPopup ? POPUP_BOTTOM : BOTTOM;
    final Icon top = isPopup ? POPUP_TOP : TOP;
    final Icon bottomRight = isPopup ? POPUP_BOTTOM_RIGHT : BOTTOM_RIGHT;
    final Icon bottomLeft = isPopup ? POPUP_BOTTOM_LEFT : BOTTOM_LEFT;
    final Icon left = isPopup ? POPUP_LEFT : LEFT;
    final Icon right = isPopup ? POPUP_RIGHT : RIGHT;
    final int sideSize = isPopup ? POPUP_SIDE_SIZE : SIDE_SIZE;
    final int bottomSize = isPopup ? POPUP_BOTTOM_SIZE : BOTTOM_SIZE;


    topLeft.paintIcon(c, g, 0, 0);
    topRight.paintIcon(c, g, width - topRight.getIconWidth(), 0);
    bottomRight.paintIcon(c, g, width - bottomRight.getIconWidth(), height - bottomRight.getIconHeight());
    bottomLeft.paintIcon(c, g, 0, height - bottomLeft.getIconHeight());

    for (int _x = topLeft.getIconWidth(); _x < width - topRight.getIconWidth(); _x++) {
      top.paintIcon(c, g, _x, 0);
    }
    for (int _x = bottomLeft.getIconWidth(); _x < width - bottomLeft.getIconWidth(); _x++) {
      bottom.paintIcon(c, g, _x, height - bottomSize);
    }
    for (int _y = topLeft.getIconHeight(); _y < height - bottomLeft.getIconHeight(); _y++) {
      left.paintIcon(c, g, 0, _y);
    }
    for (int _y = topRight.getIconHeight(); _y < height - bottomRight.getIconHeight(); _y++) {
      right.paintIcon(c, g, width - sideSize, _y);
    }

    g.setColor(new Color(0, 0, 0, 30));
    g.drawRect(SIDE_SIZE - 1, TOP_SIZE - 1,
               width - SIDE_SIZE * 2 + 1, height - TOP_SIZE - BOTTOM_SIZE + 1);

    g.dispose();
    return image;
  }

  public static BufferedImage createShadow(final JComponent c, final int width, final int height) {
    return createShadow(c, width, height, false);
  }

  public static BufferedImage createPopupShadow(final JComponent c, final int width, final int height) {
    return createShadow(c, width, height, true);
  }

  public static Shadow createShadow(Image source, int x, int y, boolean paintSource, int shadowSize) {
    int size = shadowSize;
    final float w = source.getWidth(null);
    final float h = source.getHeight(null);
    float ratio = w / h;
    float deltaX = size;
    float deltaY = size / ratio;

    final Image scaled = source.getScaledInstance((int)(w + deltaX), (int)(h + deltaY), Image.SCALE_SMOOTH);

    final BufferedImage s =
      GraphicsUtilities.createCompatibleTranslucentImage(scaled.getWidth(null), scaled.getHeight(null));
    final Graphics2D graphics = (Graphics2D)s.getGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.drawImage(scaled, 0, 0, null);

    final BufferedImage shadow = new ShadowRenderer(size, .25f, Color.black).createShadow(s);
    if (paintSource) {
      final Graphics imgG = shadow.getGraphics();
      final double d = size * 0.5;
      imgG.drawImage(source, (int)(size + d), (int)(size + d / ratio), null);
    }

    return new Shadow(shadow, x - size - 5, y - size + 2);
  }


  public static class Shadow {
    int x;
    int y;
    Image image;

    public Shadow(Image image, int x, int y) {
      this.x = x;
      this.y = y;
      this.image = image;
    }

    public int getX() {
      return x;
    }

    public int getY() {
      return y;
    }

    public Image getImage() {
      return image;
    }
  }
}
