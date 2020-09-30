// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * This class allows to cache repeatedly painted elements, by drawing them to an in-memory image, and transferring that image to the target
 * graphics instead of performing the original painting.
 */
public final class CachingPainter {
  private static final Map<Object, CachedPainting> ourCache = new WeakHashMap<>();

  /**
   * Performs painting of frequently used pattern, using image cache. {@code x}, {@code y}, {@code width}, {@code height} define the region
   * where painting is done, {@code painter} performs the actual drawing, it's called with graphics origin set to the origin or the painting
   * region. Painter logic shouldn't depend on anything except the size of the region and values of {@code parameters}. Result of painting
   * will be cached with {@code key} as a key, and used for subsequent painting requests with the same region size and parameter values.
   * <p>
   * Subpixel-antialiased text shouldn't be rendered using this procedure, as the result depends on the target surface's background color,
   * and it cannot be determined when cached image is produced.
   */
  public static void paint(@NotNull Graphics2D g, float x, float y, float width, float height, @NotNull Consumer<Graphics2D> painter,
                           @NotNull Object key, Object @NotNull ... parameters) {
    GraphicsConfiguration config = g.getDeviceConfiguration();
    float scale = JBUIScale.sysScale(config);
    if ((int) scale != scale) {
      // fractional-scale setups are not supported currently
      paintAndDispose((Graphics2D)g.create(), _g -> {
        _g.setComposite(AlphaComposite.SrcOver);
        _g.translate(x, y);
        painter.accept(_g);
      });
      return;
    }
    int xInt = (int)Math.floor(x);
    int yInt = (int)Math.floor(y);
    int widthInt = (int)Math.ceil(x + width) - xInt;
    int heightInt = (int)Math.ceil(y + height) - yInt;
    CachedPainting painting = ourCache.get(key);
    if (painting != null && !painting.matches(config, width, height, parameters)) {
      painting = null;
    }
    int validationResult = painting == null ? VolatileImage.IMAGE_INCOMPATIBLE : painting.image.validate(config);
    if (validationResult == VolatileImage.IMAGE_INCOMPATIBLE) {
      ourCache.put(key, painting = new CachedPainting(config, width, height, widthInt, heightInt, parameters));
    }
    if (validationResult != VolatileImage.IMAGE_OK) {
      // We cannot perform antialiased rendering onto volatile image using Src composite, so we draw to a buffered image first.
      BufferedImage bi = new JBHiDPIScaledImage(config, widthInt, heightInt, BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.ROUND);
      paintAndDispose(bi.createGraphics(), _g -> {
        _g.setComposite(AlphaComposite.Src);
        _g.translate(x - xInt, y - yInt);
        painter.accept(_g);
      });
      paintAndDispose(painting.image.createGraphics(), _g -> {
        _g.setComposite(AlphaComposite.Src);
        StartupUiUtil.drawImage(_g, bi, 0, 0, null);
      });
    }
    Composite savedComposite = g.getComposite();
    g.setComposite(AlphaComposite.SrcOver);
    g.drawImage(painting.image, xInt, yInt, null);
    g.setComposite(savedComposite);
    // We don't check whether volatile image's content was lost at this point,
    // cause we cannot repeat painting over the initial graphics reliably anyway (without restoring its initial contents first).
  }

  private static void paintAndDispose(Graphics2D g, Consumer<Graphics2D> painter) {
    try {
      painter.accept(g);
    }
    finally {
      g.dispose();
    }
  }

  private static final class CachedPainting {
    private final float width;
    private final float height;
    private final Object[] parameters;
    private final VolatileImage image;
    private final AffineTransform deviceTransform;

    private CachedPainting(GraphicsConfiguration config, float width, float height, int widthInt, int heightInt, Object[] parameters) {
      this.width = width;
      this.height = height;
      this.parameters = parameters;
      this.image = config.createCompatibleVolatileImage(widthInt, heightInt, Transparency.TRANSLUCENT);
      this.deviceTransform = config.getDefaultTransform();
    }

    private boolean matches(GraphicsConfiguration config, float width, float height, Object[] parameters) {
      return this.width == width &&
             this.height == height &&
             Objects.equals(deviceTransform, config.getDefaultTransform()) &&
             Arrays.equals(this.parameters, parameters);
    }
  }
}
