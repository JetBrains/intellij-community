// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.JBHiDPIScaledImage;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.MultiResolutionImage;
import java.util.Objects;

/**
 * Provides access (or converter) to {@code java.awt.image.MultiResolutionImage} available since JDK 9.
 *
 * @author tav
 */
public final class MultiResolutionImageProvider {
  /**JBHiDPIScaledImage
   * A converter from {@link JBHiDPIScaledImage} to {@code MultiResolutionImage}.
   */
  private static final class Converter {
    @Nullable
    public static Image convert(Image jbImage) {
      if (jbImage instanceof JBHiDPIScaledImage) {
        JBHiDPIScaledImage scaledImage = (JBHiDPIScaledImage)jbImage;
        Image lowResImage = ImageUtil.toBufferedImage(scaledImage, true);
        Image highResImage = ImageUtil.toBufferedImage(scaledImage);
        return new BaseMultiResolutionImage(lowResImage, highResImage);
      }
      return jbImage;
    }
  }

  /**
   * Converts the provided {@link JBHiDPIScaledImage} to {@code MultiResolutionImage}.
   * If the provided image is not {@code JBHiDPIScaledImage} the provided image is returned unchanged.
   */
  public static Image convertFromJBImage(@Nullable Image jbImage) {
    if (jbImage == null) {
      return null;
    }

    if (!checkSize(jbImage)) {
      //noinspection CallToPrintStackTrace
      new IllegalArgumentException("the image has illegal size 0x0").printStackTrace();
    }
    return Converter.convert(jbImage);
  }

  /**
   * Converts the provided icon with {@link JBHiDPIScaledImage} to an {@link ImageIcon} with {@code MultiResolutionImage}.
   * If the provided icon's image is not {@code JBHiDPIScaledImage} the provided icon is returned unchanged.
   */
  @Contract("null, _ -> null; !null, _ -> !null")
  public static Icon convertFromJBIcon(@Nullable Icon jbIcon, @Nullable ScaleContext ctx) {
    if (jbIcon == null) {
      return null;
    }

    Image image = IconLoader.toImage(jbIcon, ctx);
    if (image == null) {
      return jbIcon; // not convertable icon (e.g. with zero size)
    }
    Image newImage = convertFromJBImage(image);
    return newImage == image ? jbIcon : new ImageIcon(newImage);
  }

  /**
   * Returns the max-size resolution variant image of the provided {@code MultiResolutionImage}.
   * If the provided image is not {@code MultiResolutionImage} the provided image is returned unchanged.
   */
  @Contract("null -> null; !null -> !null")
  public static Image getMaxSizeResolutionVariant(@Nullable Image mrImage) {
    if (!(mrImage instanceof MultiResolutionImage)) {
      return mrImage;
    }

    if (!checkSize(mrImage)) {
      //noinspection CallToPrintStackTrace
      new IllegalArgumentException("the image has illegal size 0x0").printStackTrace();
    }
    int width = mrImage.getWidth(null);
    for (Image img : ((MultiResolutionImage)mrImage).getResolutionVariants()) {
      if (img.getWidth(null) >= width) {
        mrImage = img;
      }
    }
    return mrImage;
  }

  /**
   * Converts from the provided icon with {@code MultiResolutionImage} to an icon with {@link JBHiDPIScaledImage}.
   * If the provided icon's image is not {@code MultiResolutionImage} the provided icon is returned unchanged.
   */
  @Contract("null, _ -> null; !null, _ -> !null")
  public static Icon convertFromMRIcon(@Nullable Icon mrIcon, @Nullable ScaleContext scaleContext) {
    if (mrIcon == null) {
      return null;
    }

    if (scaleContext == null) {
      scaleContext = ScaleContext.create();
    }
    Image image = Objects.requireNonNull(IconLoader.toImage(mrIcon, scaleContext));
    if (image instanceof MultiResolutionImage) {
      return mrIcon;
    }
    image = getMaxSizeResolutionVariant(image);
    image = ImageUtil.ensureHiDPI(image, scaleContext);
    return new JBImageIcon(image);
  }

  private static boolean checkSize(Image image) {
    return image.getWidth(null) != 0 && image.getHeight(null) != 0;
  }
}
