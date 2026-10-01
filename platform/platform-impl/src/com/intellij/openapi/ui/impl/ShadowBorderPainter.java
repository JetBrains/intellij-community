// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.impl;

import com.intellij.ui.Gray;
import com.intellij.ui.ShadowJava2DPainter;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.util.ui.ImageUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.JComponent;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;

/**
 * @author Konstantin Bulenkov
 */
public final class ShadowBorderPainter {
  private static final float SHADOW_OPACITY = .2f;

  private ShadowBorderPainter() {
  }

  private static BufferedImage createJava2dShadow(JComponent component, int width, int height) {
    BufferedImage image = component.getGraphicsConfiguration().createCompatibleImage(width, height, Transparency.TRANSLUCENT);
    ShadowJava2DPainter painter = new ShadowJava2DPainter(ShadowJava2DPainter.Type.IDE, 0, Gray.x00.withAlpha(30));
    PaintUtil.use(image.createGraphics(), g -> painter.paintShadow(g, 0, 0, width, height));
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

    //noinspection UndesirableClassUsage
    final BufferedImage s = new BufferedImage(scaled.getWidth(null), scaled.getHeight(null), BufferedImage.TYPE_INT_ARGB);
    PaintUtil.use(s.createGraphics(), graphics -> {
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.drawImage(scaled, 0, 0, null);
    });

    final BufferedImage shadow = blurAlpha(s, shadowSize);
    if (paintSource) {
      final Graphics imgG = shadow.getGraphics();
      final double d = shadowSize * 0.5;
      imgG.drawImage(source, (int)(shadowSize + d), (int)(shadowSize + d / ratio), null);
    }

    return new Shadow(shadow, x - shadowSize - 5, y - shadowSize + 2);
  }

  /**
   * Blurs the alpha of {@code source} into a black shadow that is {@code 2 * size} pixels larger on each axis.
   * The source origin maps to {@code (size, size)}. A box average over {@code 2 * size} pixels runs along the rows, then
   * along the columns, and the result is scaled by {@link #SHADOW_OPACITY}. The code uses no Swing, so it works on a
   * frozen EDT. The result is a plain image in device pixels, because the callers scale the shadow themselves.
   */
  @ApiStatus.Internal
  @VisibleForTesting
  public static BufferedImage blurAlpha(BufferedImage source, int size) {
    if (size < 1) {
      throw new IllegalArgumentException("The shadow size must be positive: " + size);
    }

    int sourceWidth = source.getWidth();
    int sourceHeight = source.getHeight();
    int window = size * 2;
    int width = sourceWidth + window;
    int height = sourceHeight + window;

    int[] pixels = source.getRGB(0, 0, sourceWidth, sourceHeight, null, 0, sourceWidth);
    int[] alpha = new int[width * height];
    for (int y = 0; y < sourceHeight; y++) {
      for (int x = 0; x < sourceWidth; x++) {
        alpha[(y + size) * width + x + size] = pixels[y * sourceWidth + x] >>> 24;
      }
    }

    int[] prefix = new int[Math.max(width, height) + 1];
    int[] rows = new int[width * height];
    for (int y = 0; y < height; y++) {
      boxAverage(alpha, y * width, 1, width, size, window, prefix, rows, 1f);
    }
    int[] result = new int[width * height];
    for (int x = 0; x < width; x++) {
      boxAverage(rows, x, width, height, size, window, prefix, result, SHADOW_OPACITY);
    }

    for (int i = 0; i < result.length; i++) {
      result[i] <<= 24;
    }
    //noinspection UndesirableClassUsage
    BufferedImage shadow = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    shadow.setRGB(0, 0, width, height, result, 0, width);
    return shadow;
  }

  /**
   * Writes to {@code to} the average of the {@code window} values of {@code from} from {@code i - size} to
   * {@code i + size - 1} for each index {@code i} of one line. A value outside the line counts as zero.
   */
  private static void boxAverage(int[] from, int start, int step, int length, int size, int window, int[] prefix, int[] to, float scale) {
    prefix[0] = 0;
    for (int i = 0; i < length; i++) {
      prefix[i + 1] = prefix[i] + from[start + i * step];
    }
    for (int i = 0; i < length; i++) {
      int low = Math.max(i - size, 0);
      int high = Math.min(i + size, length);
      to[start + i * step] = (int)((prefix[high] - prefix[low]) * scale / window);
    }
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
