// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.icons.HiDPIImage;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleType;
import com.intellij.util.ImageLoader;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.RetinaImage;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.*;

import static com.intellij.ui.scale.ScaleType.USR_SCALE;
import static java.lang.Math.min;

/**
 * @author Konstantin Bulenkov
 */
public final class ImageUtil {
  /**
   * Creates a HiDPI-aware BufferedImage in device scale.
   *
   * @param width  the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type   the type of the image
   * @return a HiDPI-aware BufferedImage in device scale
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  public static @NotNull BufferedImage createImage(int width, int height, int type) {
    return createImage((GraphicsConfiguration)null, width, height, type);
  }

  /**
   * Creates a HiDPI-aware BufferedImage in the graphics config scale.
   *
   * @param gc     the graphics config
   * @param width  the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type   the type of the image
   * @return a HiDPI-aware BufferedImage in the graphics scale
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  public static @NotNull BufferedImage createImage(@Nullable GraphicsConfiguration gc, int width, int height, int type) {
    if (JreHiDpiUtil.isJreHiDPI(gc)) {
      return new HiDPIImage(gc, width, height, type);
    }
    else {
      //noinspection UndesirableClassUsage
      return new BufferedImage(width, height, type);
    }
  }

  /**
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   * @see #createImage(ScaleContext, double, double, int, PaintUtil.RoundingMode)
   */
  public static @NotNull BufferedImage createImage(ScaleContext context,
                                                   double width,
                                                   double height,
                                                   int type,
                                                   @NotNull PaintUtil.RoundingMode rm) {
    if (StartupUiUtil.INSTANCE.isJreHiDPI(context)) {
      return new HiDPIImage(context, width, height, type, rm);
    }
    else {
      //noinspection UndesirableClassUsage
      return new BufferedImage(rm.round(width), rm.round(height), type);
    }
  }

  /**
   * Creates a HiDPI-aware BufferedImage in the graphics device scale.
   *
   * @param g      the graphics of the target device
   * @param width  the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type   the type of the image
   * @return a HiDPI-aware BufferedImage in the graphics scale
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  public static @NotNull BufferedImage createImage(Graphics g, int width, int height, int type) {
    return createImage(g, width, height, type, PaintUtil.RoundingMode.FLOOR);
  }

  /**
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   * @see #createImage(GraphicsConfiguration, int, int, int)
   */
  public static @NotNull BufferedImage createImage(Graphics g, double width, double height, int type, @NotNull PaintUtil.RoundingMode rm) {
    if (g instanceof Graphics2D g2d) {
      if (JreHiDpiUtil.isJreHiDPI(g2d)) {
        return new HiDPIImage(g2d, width, height, type, rm);
      }
      //noinspection UndesirableClassUsage
      return new BufferedImage(rm.round(width), rm.round(height), type);
    }
    return createImage(rm.round(width), rm.round(height), type);
  }

  public static @NotNull BufferedImage toBufferedImage(@NotNull Image image) {
    return toBufferedImage(image, false, false);
  }

  public static @NotNull BufferedImage toBufferedImage(@NotNull Image image, boolean inUserSize) {
    return toBufferedImage(image, inUserSize, false);
  }

  public static @NotNull BufferedImage toBufferedImage(@NotNull Image image, boolean inUserSize, boolean ensureOneComponent) {
    if (image instanceof JBHiDPIScaledImage jbImage) {
      Image delegate = jbImage.getDelegate();
      if (delegate != null) image = delegate;
      if (inUserSize) {
        image = scaleImage(image, 1 / jbImage.getScale());
      }
    }
    if (image instanceof BufferedImage && (!ensureOneComponent || ((BufferedImage)image).getColorModel().getNumComponents() == 1)) {
      return (BufferedImage)image;
    }

    final int width = image.getWidth(null);
    final int height = image.getHeight(null);
    if (width <= 0 || height <= 0) {
      // avoiding NPE
      return new BufferedImage(Math.max(width, 1), Math.max(height, 1), BufferedImage.TYPE_INT_ARGB) {
        @Override
        public int getWidth() {
          return Math.max(width, 0);
        }

        @Override
        public int getHeight() {
          return Math.max(height, 0);
        }

        @Override
        public int getWidth(ImageObserver observer) {
          return Math.max(width, 0);
        }

        @Override
        public int getHeight(ImageObserver observer) {
          return Math.max(height, 0);
        }
      };
    }

    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = bufferedImage.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();
    return bufferedImage;
  }

  public static double getImageScale(Image image) {
    if (image instanceof JBHiDPIScaledImage) {
      return ((JBHiDPIScaledImage)image).getScale();
    }
    return 1;
  }

  public static int getRealWidth(@NotNull Image image) {
    if (image instanceof JBHiDPIScaledImage) {
      Image img = ((JBHiDPIScaledImage)image).getDelegate();
      if (img != null) {
        image = img;
      }
    }
    return image.getWidth(null);
  }

  public static int getRealHeight(@NotNull Image image) {
    if (image instanceof JBHiDPIScaledImage) {
      Image img = ((JBHiDPIScaledImage)image).getDelegate();
      if (img != null) image = img;
    }
    return image.getHeight(null);
  }

  public static int getUserWidth(@NotNull Image image) {
    if (image instanceof JBHiDPIScaledImage) {
      return ((JBHiDPIScaledImage)image).getUserWidth();
    }
    return image.getWidth(null);
  }

  public static int getUserHeight(@NotNull Image image) {
    if (image instanceof JBHiDPIScaledImage) {
      return ((JBHiDPIScaledImage)image).getUserHeight();
    }
    return image.getHeight(null);
  }

  public static Image filter(@Nullable Image image, @Nullable ImageFilter filter) {
    if (image == null || filter == null) {
      return image;
    }
    return Toolkit.getDefaultToolkit().createImage(new FilteredImageSource(toBufferedImage(image).getSource(), filter));
  }

  /**
   * Scales the image taking into account its HiDPI awareness.
   */
  public static Image scaleImage(Image image, double scale) {
    return ImageLoader.scaleImage(image, scale);
  }

  /**
   * Scales the image taking into account its HiDPI awareness.
   *
   * @param width  target user width
   * @param height target user height
   */
  public static Image scaleImage(Image image, int width, int height) {
    if (width <= 0 || height <= 0) return image;

    if (image instanceof JBHiDPIScaledImage) {
      return ((JBHiDPIScaledImage)image).scale(width, height);
    }
    return Scalr.resize(toBufferedImage(image), Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, width, height, (BufferedImageOp[])null);
  }

  /**
   * Wraps the {@code image} with {@link JBHiDPIScaledImage} according to {@code ctx} when applicable.
   */
  @Contract("null, _ -> null; !null, _ -> !null")
  public static Image ensureHiDPI(@Nullable Image image, @NotNull ScaleContext ctx) {
    if (image == null) {
      return null;
    }
    if (StartupUiUtil.INSTANCE.isJreHiDPI(ctx)) {
      return RetinaImage.createFrom(image, ctx.getScale(ScaleType.SYS_SCALE), null);
    }
    return image;
  }

  /**
   * Wraps the {@code image} with {@link JBHiDPIScaledImage} according to {@code context} when applicable.
   * The real (dev) width/height of the provided image is usually calculated based on the scale context and the
   * expected user width/height of the target wrapped image. In the {@link #ensureHiDPI(Image, ScaleContext)} method version,
   * the expected user width/height of the wrapped image is reconstructed from the image's real width/height and the scale context.
   * However, the real with/height may lose precision (as it is integer) and as the result the reconstructed user width/height
   * may differ from the original values. To avoid the loss this method version accepts the original user width/height.
   *
   * @param image      the raw image to wrap
   * @param context    the scale context to match
   * @param userWidth  the expected user width of the wrapped image
   * @param userHeight the expected user height of the wrapped image
   */
  @Contract("null, _, _, _ -> null; !null, _, _, _ -> !null")
  public static Image ensureHiDPI(@Nullable Image image, @NotNull ScaleContext context, double userWidth, double userHeight) {
    if (image == null) {
      return null;
    }
    if (StartupUiUtil.INSTANCE.isJreHiDPI(context)) {
      return new HiDPIImage(image, userWidth, userHeight, BufferedImage.TYPE_INT_ARGB);
    }
    return image;
  }

  // NB: Execution may take more than 50ms, so if a lot of images should be resized, it is better to call it in background thread
  public static @NotNull Image resize(@NotNull Image image, int size, @NotNull ScaleContext scaleContext) {
    Image hidpiImage = ensureHiDPI(image, scaleContext);
    int scaledSize = (int)scaleContext.apply(size, USR_SCALE);
    return scaleImage(hidpiImage, scaledSize, scaledSize);
  }

  public static @NotNull BufferedImage createCircleImage(@NotNull BufferedImage image) {
    int size = min(image.getWidth(), image.getHeight());
    Area avatarOvalArea = new Area(new Ellipse2D.Double(0.0, 0.0, size, size));

    return clipImage(image, avatarOvalArea);
  }

  public static @NotNull BufferedImage createRoundedImage(@NotNull BufferedImage image, double arc) {
    int size = min(image.getWidth(), image.getHeight());
    Area avatarOvalArea = new Area(new RoundRectangle2D.Double(0.0, 0.0, size, size, arc, arc));
    return clipImage(image, avatarOvalArea);
  }

  public static @NotNull BufferedImage clipImage(@NotNull BufferedImage image, @NotNull Shape clip) {
    if (image instanceof JBHiDPIScaledImage scaledImage) {
      Image delegate = scaledImage.getDelegate();
      if (delegate == null) return doClipImage(scaledImage, clip);
      BufferedImage clippedImage = doClipImage(toBufferedImage(delegate), clip);
      return new JBHiDPIScaledImage(clippedImage,
                                    ScaleContext.create(ScaleType.SYS_SCALE.of(scaledImage.getScale())),
                                    scaledImage.getType());
    }

    return doClipImage(image, clip);
  }


  private static @NotNull BufferedImage doClipImage(@NotNull BufferedImage image, @NotNull Shape clip) {
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = newImage.createGraphics();
    try {
      applyQualityRenderingHints(g);
      g.setPaint(new TexturePaint(image, new Rectangle(0, 0, image.getWidth(), image.getHeight())));
      g.fill(clip);
      return newImage;
    }
    finally {
      g.dispose();
    }
  }

  public static void applyQualityRenderingHints(@NotNull Graphics2D g2) {
    g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
  }
}
