// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.impl;

import com.intellij.ui.Gray;
import com.intellij.ui.ShadowJava2DPainter;
import com.intellij.util.ui.ImageUtil;
import org.jdesktop.swingx.graphics.GraphicsUtilities;
import org.jdesktop.swingx.graphics.ShadowRenderer;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Konstantin Bulenkov
 */
public final class ShadowBorderPainter {

  private ShadowBorderPainter() {
  }

  private static BufferedImage createJava2dShadow(JComponent component, int width, int height) {
    BufferedImage image = component.getGraphicsConfiguration().createCompatibleImage(width, height, Transparency.TRANSLUCENT);
    ShadowJava2DPainter painter = new ShadowJava2DPainter(ShadowJava2DPainter.Type.IDE, 0, Gray.x00.withAlpha(30));
    Graphics2D g = image.createGraphics();
    painter.paintShadow(g, 0, 0, width, height);
    g.dispose();
    return image;
  }

  @ApiStatus.Internal
  public static BufferedImage createShadow(final JComponent c, final int width, final int height) {
    return createJava2dShadow(c, width, height);
  }

  public static Shadow createShadow(Image source, int x, int y, boolean paintSource, int shadowSize) {
    source = ImageUtil.toBufferedImage(source);
    final float w = source.getWidth(null);
    final float h = source.getHeight(null);
    float ratio = w / h;
    float deltaX = shadowSize;
    float deltaY = shadowSize / ratio;

    final Image scaled = source.getScaledInstance((int)(w + deltaX), (int)(h + deltaY), Image.SCALE_FAST);

    final BufferedImage s =
      GraphicsUtilities.createCompatibleTranslucentImage(scaled.getWidth(null), scaled.getHeight(null));
    final Graphics2D graphics = (Graphics2D)s.getGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.drawImage(scaled, 0, 0, null);

    final BufferedImage shadow = new ShadowRenderer(shadowSize, .2f, Gray.x00).createShadow(s);
    if (paintSource) {
      final Graphics imgG = shadow.getGraphics();
      final double d = shadowSize * 0.5;
      imgG.drawImage(source, (int)(shadowSize + d), (int)(shadowSize + d / ratio), null);
    }

    return new Shadow(shadow, x - shadowSize - 5, y - shadowSize + 2);
  }


  public static final class Shadow {
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
