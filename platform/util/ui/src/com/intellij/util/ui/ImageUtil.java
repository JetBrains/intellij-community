// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.RetinaImage;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageObserver;

import static com.intellij.ui.scale.ScaleType.SYS_SCALE;

/**
 * @author Konstantin Bulenkov
 */
public class ImageUtil {
  @NotNull
  public static BufferedImage toBufferedImage(@NotNull Image image) {
    return toBufferedImage(image, false);
  }

  @NotNull
  public static BufferedImage toBufferedImage(@NotNull Image image, boolean inUserSize) {
    if (image instanceof JBHiDPIScaledImage) {
      JBHiDPIScaledImage jbImage = (JBHiDPIScaledImage)image;
      Image img = jbImage.getDelegate();
      if (img != null) {
        if (inUserSize) {
          double scale = jbImage.getScale();
          img = scaleImage(img, 1 / scale);
        }
        image = img;
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

  public static Image filter(Image image, ImageFilter filter) {
    if (image == null || filter == null) return image;
    return Toolkit.getDefaultToolkit().createImage(
      new FilteredImageSource(toBufferedImage(image).getSource(), filter));
  }

  /**
   * Scales the image taking into account its HiDPI awareness.
   */
  public static Image scaleImage(Image image, double scale) {
    return ImageLoader.scaleImage(image, scale);
  }

  /**
   * Wraps the {@code image} with {@link JBHiDPIScaledImage} according to {@code ctx} when applicable.
   */
  @Contract("null, _ -> null; !null, _ -> !null")
  public static Image ensureHiDPI(@Nullable Image image, @NotNull ScaleContext ctx) {
    if (image == null) return null;
    if (StartupUiUtil.isJreHiDPI(ctx)) {
      return RetinaImage.createFrom(image, ctx.getScale(SYS_SCALE), null);
    }
    return image;
  }

  /**
   * @deprecated Use {@link #ensureHiDPI(Image, ScaleContext)}.
   */
  @Deprecated
  @Contract("null, _ -> null; !null, _ -> !null")
  public static Image ensureHiDPI(@Nullable Image image, @NotNull JBUI.ScaleContext ctx) {
    return ensureHiDPI(image, (ScaleContext)ctx);
  }

  /**
   * Wraps the {@code image} with {@link JBHiDPIScaledImage} according to {@code ctx} when applicable.
   * The real (dev) width/height of the provided image is usually calculated based on the scale context and the
   * expected user width/height of the target wrapped image. In the {@link #ensureHiDPI(Image, ScaleContext)} method version,
   * the expected user width/height of the wrapped image is reconstructed from the image's real width/height and the scale context.
   * However, the real with/height may lose precision (as it is integer) and as the result the reconstructed user width/height
   * may differ from the original values. To avoid the loss this method version accepts the original user width/height.
   *
   * @param image the raw image to wrap
   * @param ctx the scale context to match
   * @param userWidth the expected user width of the wrapped image
   * @param userHeight the expected user height of the wrapped image
   */
  @Contract("null, _, _, _ -> null; !null, _, _, _ -> !null")
  public static Image ensureHiDPI(@Nullable Image image, @NotNull ScaleContext ctx, double userWidth, double userHeight) {
    if (image == null) return null;
    if (StartupUiUtil.isJreHiDPI(ctx)) {
      return new JBHiDPIScaledImage(image, userWidth, userHeight, BufferedImage.TYPE_INT_ARGB);
    }
    return image;
  }
}
