// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.JreHiDpiUtil;
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

import static java.lang.Math.min;

/**
 * @author Konstantin Bulenkov
 */
public final class ImageUtil {
  /**
   * Creates a HiDPI-aware BufferedImage in device scale.
   *
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type of the image
   *
   * @return a HiDPI-aware BufferedImage in device scale
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  public static @NotNull BufferedImage createImage(int width, int height, int type) {
    return createImage((GraphicsConfiguration)null, width, height, type);
  }

  /**
   * Creates a HiDPI-aware BufferedImage in the graphics config scale.
   *
   * @param gc the graphics config
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type of the image
   *
   * @return a HiDPI-aware BufferedImage in the graphics scale
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  public static @NotNull BufferedImage createImage(@Nullable GraphicsConfiguration gc, int width, int height, int type) {
    if (JreHiDpiUtil.isJreHiDPI(gc)) {
      return new JBHiDPIScaledImage(gc, width, height, type);
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
  public static @NotNull BufferedImage createImage(ScaleContext context, double width, double height, int type, @NotNull PaintUtil.RoundingMode rm) {
    if (StartupUiUtil.isJreHiDPI(context)) {
      return new JBHiDPIScaledImage(context, width, height, type, rm);
    }
    else {
      //noinspection UndesirableClassUsage
      return new BufferedImage(rm.round(width), rm.round(height), type);
    }
  }

  /**
   * Creates a HiDPI-aware BufferedImage in the graphics device scale.
   *
   * @param g the graphics of the target device
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type of the image
   *
   * @return a HiDPI-aware BufferedImage in the graphics scale
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   */
  @NotNull
  public static BufferedImage createImage(Graphics g, int width, int height, int type) {
    return createImage(g, width, height, type, PaintUtil.RoundingMode.FLOOR);
  }

  /**
   * @throws IllegalArgumentException if {@code width} or {@code height} is not greater than 0
   * @see #createImage(GraphicsConfiguration, double, double, int, PaintUtil.RoundingMode)
   */
  @NotNull
  public static BufferedImage createImage(Graphics g, double width, double height, int type, @NotNull PaintUtil.RoundingMode rm) {
    if (g instanceof Graphics2D) {
      Graphics2D g2d = (Graphics2D)g;
      if (JreHiDpiUtil.isJreHiDPI(g2d)) {
        return RetinaImage.create(g2d, width, height, type, rm);
      }
      //noinspection UndesirableClassUsage
      return new BufferedImage(rm.round(width), rm.round(height), type);
    }
    return createImage(rm.round(width), rm.round(height), type);
  }

  @NotNull
  public static BufferedImage toBufferedImage(@NotNull Image image) {
    return toBufferedImage(image, false);
  }

  @NotNull
  public static BufferedImage toBufferedImage(@NotNull Image image, boolean inUserSize) {
    if (image instanceof JBHiDPIScaledImage) {
      JBHiDPIScaledImage jbImage = (JBHiDPIScaledImage)image;
      Image delegate = jbImage.getDelegate();
      if (delegate != null) image = delegate;
      if (inUserSize) {
        image = scaleImage(image, 1 / jbImage.getScale());
      }
    }
    if (image instanceof BufferedImage) {
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
      if (img != null) image = img;
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
      return ((JBHiDPIScaledImage)image).getUserWidth(null);
    }
    return image.getWidth(null);
  }

  public static int getUserHeight(@NotNull Image image) {
    if (image instanceof JBHiDPIScaledImage) {
      return ((JBHiDPIScaledImage)image).getUserHeight(null);
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
   * @param width target user width
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
    if (StartupUiUtil.isJreHiDPI(ctx)) {
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
   * @param image the raw image to wrap
   * @param context the scale context to match
   * @param userWidth the expected user width of the wrapped image
   * @param userHeight the expected user height of the wrapped image
   */
  @Contract("null, _, _, _ -> null; !null, _, _, _ -> !null")
  public static Image ensureHiDPI(@Nullable Image image, @NotNull ScaleContext context, double userWidth, double userHeight) {
    if (image == null) {
      return null;
    }
    if (StartupUiUtil.isJreHiDPI(context)) {
      return new JBHiDPIScaledImage(image, userWidth, userHeight, BufferedImage.TYPE_INT_ARGB);
    }
    return image;
  }

  @NotNull
  public static BufferedImage createCircleImage(@NotNull BufferedImage image) {
    int size = min(image.getWidth(), image.getHeight());
    Area avatarOvalArea = new Area(new Ellipse2D.Double(0.0, 0.0, size, size));

    return createImageByMask(image, avatarOvalArea);
  }

  @NotNull
  public static BufferedImage createRoundedImage(@NotNull BufferedImage image, double arc) {
    int size = min(image.getWidth(), image.getHeight());
    Area avatarOvalArea = new Area(new RoundRectangle2D.Double(0.0, 0.0, size, size, arc, arc));
    return createImageByMask(image, avatarOvalArea);
  }

  @NotNull
  public static BufferedImage createImageByMask(@NotNull BufferedImage image, @NotNull Area area) {
    int size = min(image.getWidth(), image.getHeight());
    BufferedImage mask = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = mask.createGraphics();
    applyQualityRenderingHints(g2);
    g2.fill(area);

    BufferedImage shapedImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    g2 = shapedImage.createGraphics();
    applyQualityRenderingHints(g2);
    g2.drawImage(image, 0, 0, null);
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_IN));
    g2.drawImage(mask, 0, 0, null);
    g2.dispose();

    return shapedImage;
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
