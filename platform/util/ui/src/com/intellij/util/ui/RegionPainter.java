// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Painter;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * The {@code RegionPainter} interface defines exactly one method, {@code paint}.
 * It is used in situations where the developer can change the painting routine
 * of a component without having to resort to subclassing the component.
 * It is also generically useful when doing any form of painting delegation.
 * <p>
 * Painters are simply encapsulations of Java2D code and make it fairly trivial
 * to reuse existing painters or to combine them together.
 * Implementations of this interface are also trivial to write,
 * such that if you can't find a painter that does what you need,
 * you can write one with minimal effort.
 *
 * @see Painter
 */
public interface RegionPainter<T> {
  /**
   * Renders to the given {@link Graphics2D} object.
   *
   * @param g      the {@code Graphics2D} object to render to
   * @param x      X position of the area to paint
   * @param y      Y position of the area to paint
   * @param width  width of the area to paint
   * @param height height of the area to paint
   * @param object an optional configuration parameter
   */
  void paint(@NotNull Graphics2D g, int x, int y, int width, int height, @Nullable T object);

  /**
   * This class provides a base functionality to paint a region with the specified alpha.
   */
  abstract class Alpha implements RegionPainter<Float> {
    @Override
    public void paint(@NotNull Graphics2D g, int x, int y, int width, int height, Float value) {
      float alpha = getAlpha(value);
      if (alpha > 0) {
        Composite composite = g.getComposite();
        g.setComposite(getComposite(alpha));
        paint(g, x, y, width, height);
        g.setComposite(composite);
      }
    }

    /**
     * Calculates alpha from the specified value.
     *
     * @param value a configuration parameter used to calculate alpha
     * @return an alpha calculated from the specified value
     */
    protected float getAlpha(Float value) {
      return value != null ? value : 0;
    }

    /**
     * Returns new composite with the specified alpha.
     *
     * @param alpha the constant alpha to be multiplied with the alpha of the source
     * @return new composite with the specified alpha
     */
    protected Composite getComposite(float alpha) {
      return alpha < 1
             ? AlphaComposite.SrcOver.derive(alpha)
             : AlphaComposite.SrcOver;
    }

    /**
     * Renders to the given {@link Graphics2D} object.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      X position of the area to paint
     * @param y      Y position of the area to paint
     * @param width  width of the area to paint
     * @param height height of the area to paint
     */
    protected abstract void paint(Graphics2D g, int x, int y, int width, int height);
  }

  /**
   * This class provides a caching functionality for a region painter.
   */
  class Image implements RegionPainter<Object> {
    private BufferedImage myImage;

    /**
     * This method is called for the cached image before {@code drawImage}.
     * It should be overridden if the image must be updated without creating a new image.
     *
     * @param image the cached image to update
     */
    protected void updateImage(BufferedImage image) {
    }

    /**
     * This method is called if the cached image is invalidated or it's size is changed.
     * It must be overridden to paint on newly created image.
     *
     * @param width  width of the new image
     * @param height height of the new image
     * @return new {@link BufferedImage} object
     */
    protected BufferedImage createImage(int width, int height) {
      return ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * This method is called to invalidate the cached image.
     */
    protected void invalidate() {
      myImage = null;
    }

    @Override
    public void paint(@NotNull Graphics2D g, int x, int y, int width, int height, Object object) {
      if (width > 0 && height > 0) {
        if (myImage == null || width != ImageUtil.getUserWidth(myImage) || height != ImageUtil.getUserHeight(myImage)) {
          myImage = createImage(width, height);
        }
        else if (myImage != null) {
          updateImage(myImage);
        }
        if (myImage != null) {
          StartupUiUtil.drawImage(g, myImage, null, x, y);
        }
      }
    }
  }
}
