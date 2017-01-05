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

import com.intellij.icons.AllIcons;
import com.intellij.ui.Gray;
import com.intellij.util.ui.ImageUtil;
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
  public static final ShadowPainter ourPopupShadowPainter = new ShadowPainter(AllIcons.Ide.Shadow.Popup.Top,
                                                                              AllIcons.Ide.Shadow.Popup.Top_right,
                                                                              AllIcons.Ide.Shadow.Popup.Right,
                                                                              AllIcons.Ide.Shadow.Popup.Bottom_right,
                                                                              AllIcons.Ide.Shadow.Popup.Bottom,
                                                                              AllIcons.Ide.Shadow.Popup.Bottom_left,
                                                                              AllIcons.Ide.Shadow.Popup.Left,
                                                                              AllIcons.Ide.Shadow.Popup.Top_left,
                                                                              Gray.x00.withAlpha(30));

  public static final ShadowPainter ourShadowPainter = new ShadowPainter(AllIcons.Ide.Shadow.Top,
                                                                         AllIcons.Ide.Shadow.Top_right,
                                                                         AllIcons.Ide.Shadow.Right,
                                                                         AllIcons.Ide.Shadow.Bottom_right,
                                                                         AllIcons.Ide.Shadow.Bottom,
                                                                         AllIcons.Ide.Shadow.Bottom_left,
                                                                         AllIcons.Ide.Shadow.Left,
                                                                         AllIcons.Ide.Shadow.Top_left,
                                                                         Gray.x00.withAlpha(30));


  private ShadowBorderPainter() {
  }

  public static BufferedImage createShadow(final JComponent c, final int width, final int height, boolean isPopup) {
    return getPainter(isPopup).createShadow(c, width, height);
  }

  private static ShadowPainter getPainter(boolean isPopup) {
    return isPopup ? ourPopupShadowPainter : ourShadowPainter;
  }

  public static BufferedImage createShadow(final JComponent c, final int width, final int height) {
    return createShadow(c, width, height, false);
  }

  public static BufferedImage createPopupShadow(final JComponent c, final int width, final int height) {
    return createShadow(c, width, height, true);
  }

  public static Shadow createShadow(Image source, int x, int y, boolean paintSource, int shadowSize) {
    source = ImageUtil.toBufferedImage(source);
    final float w = source.getWidth(null);
    final float h = source.getHeight(null);
    float ratio = w / h;
    float deltaX = shadowSize;
    float deltaY = shadowSize / ratio;

    final Image scaled = source.getScaledInstance((int)(w + deltaX), (int)(h + deltaY), Image.SCALE_SMOOTH);

    final BufferedImage s =
      GraphicsUtilities.createCompatibleTranslucentImage(scaled.getWidth(null), scaled.getHeight(null));
    final Graphics2D graphics = (Graphics2D)s.getGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.drawImage(scaled, 0, 0, null);

    final BufferedImage shadow = new ShadowRenderer(shadowSize, .25f, Gray.x00).createShadow(s);
    if (paintSource) {
      final Graphics imgG = shadow.getGraphics();
      final double d = shadowSize * 0.5;
      imgG.drawImage(source, (int)(shadowSize + d), (int)(shadowSize + d / ratio), null);
    }

    return new Shadow(shadow, x - shadowSize - 5, y - shadowSize + 2);
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
