// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.MethodInvocator;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImageOp;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Provides access (or converter) to {@code java.awt.image.MultiResolutionImage} available since JDK 9.
 *
 * @author tav
 */
public final class MultiResolutionImageProvider {
  /**
   * An accessor to the {@code MultiResolutionImage}'s resolution variants methods.
   */
  public static class Accessor {
    private static final Class<?> MRI_CLASS;
    private static final MethodInvocator GET_RESOLUTION_VARIANTS_METHOD;
    private static final MethodInvocator GET_RESOLUTION_VARIANT_METHOD;

    private final Image myMRImage;

    static {
      Class<?> cls = null;
      MethodInvocator m1 = null;
      MethodInvocator m2 = null;
      if (SystemInfoRt.IS_AT_LEAST_JAVA9) {
        try {
          cls = Class.forName("java.awt.image.MultiResolutionImage");
        }
        catch (ClassNotFoundException ignore) {
        }
        if (cls != null) {
          m1 = new MethodInvocator(cls, "getResolutionVariants");
          m2 = new MethodInvocator(cls, "getResolutionVariant", double.class, double.class);
        }
      }
      MRI_CLASS = cls;
      GET_RESOLUTION_VARIANTS_METHOD = m1;
      GET_RESOLUTION_VARIANT_METHOD = m2;
    }

    private Accessor(Image mrImage) {
      this.myMRImage = mrImage;
    }

    /**
     * @see {@code java.awt.image.MultiResolutionImage.getResolutionVariants}
     */
    public java.util.List<Image> getResolutionVariants() {
      if (!isMultiResolutionImage(myMRImage)) {
        return Collections.singletonList(myMRImage);
      }
      //noinspection unchecked
      return (List<Image>)GET_RESOLUTION_VARIANTS_METHOD.invoke(myMRImage);
    }

    /**
     * @see {@code java.awt.image.MultiResolutionImage.getResolutionVariant}
     */
    @SuppressWarnings("unused")
    public Image getResolutionVariant(double width, double height) {
      if (!isMultiResolutionImage(myMRImage)) {
        if (!checkSize(myMRImage)) {
          return myMRImage;
        }
        return Scalr.resize(ImageUtil.toBufferedImage(myMRImage), Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, (int)width, (int)height,
                            (BufferedImageOp[])null);
      }
      return (Image)GET_RESOLUTION_VARIANT_METHOD.invoke(myMRImage, width, height);
    }
  }

  /**
   * A converter from {@link JBHiDPIScaledImage} to {@code MultiResolutionImage}.
   */
  private static final class Converter {
    private static final Constructor<?> BMRI_CLASS_CTOR;

    static {
      Class<?> cls = null;
      Constructor<?> ctor = null;
      if (SystemInfoRt.IS_AT_LEAST_JAVA9) {
        try {
          cls = Class.forName("java.awt.image.BaseMultiResolutionImage");
        }
        catch (ClassNotFoundException ignore) {
        }
        if (cls != null) {
          try {
            //noinspection unchecked
            ctor = cls.getConstructor(Image[].class);
            ctor.setAccessible(true);
          }
          catch (NoSuchMethodException ignore) {
          }
        }
      }
      BMRI_CLASS_CTOR = ctor;
    }

    @Nullable
    public static Image convert(Image jbImage) {
      if (BMRI_CLASS_CTOR == null) return null;

      Image[] variants;
      if (jbImage instanceof JBHiDPIScaledImage) {
        JBHiDPIScaledImage scaledImage = (JBHiDPIScaledImage)jbImage;
        Image lowResImage = ImageUtil.toBufferedImage(scaledImage, true);
        Image highResImage = ImageUtil.toBufferedImage(scaledImage);
        variants = new Image[]{lowResImage, highResImage};
      }
      else {
        variants = new Image[]{jbImage};
      }
      try {
        return (Image)BMRI_CLASS_CTOR.newInstance(new Object[]{variants});
      }
      catch (InstantiationException | IllegalAccessException | InvocationTargetException ignore) {
      }
      return null;
    }
  }

  /**
   * Checks whether the image is an instance of MultiResolutionImage.
   */
  public static boolean isMultiResolutionImage(@Nullable Image image) {
    return image != null && Accessor.MRI_CLASS != null && Accessor.MRI_CLASS.isInstance(image);
  }

  /**
   * Checks whether {@code MultiResolutionImage} is available in this runtime.
   */
  public static boolean isMultiResolutionImageAvailable() {
    return Accessor.MRI_CLASS != null;
  }

  /**
   * Converts the provided {@link JBHiDPIScaledImage} to {@code MultiResolutionImage}.
   * If the provided image is not {@code JBHiDPIScaledImage} the returned {@code MultiResolutionImage} will
   * default to the provided image's single resolution variant.
   */
  @Contract("null -> null; !null -> !null")
  public static Image convertFromJBImage(@Nullable Image jbImage) {
    if (jbImage == null) return null;

    if (!checkSize(jbImage)) {
      //noinspection CallToPrintStackTrace
      new IllegalArgumentException("the image has illegal size 0x0").printStackTrace();
    }
    return Converter.convert(jbImage);
  }

  /**
   * Converts the provided icon with {@link JBHiDPIScaledImage} to an {@link ImageIcon} with {@code MultiResolutionImage}.
   * If the provided icon's image is not {@code JBHiDPIScaledImage} the returned icon's {@code MultiResolutionImage} will
   * default to the provided image's single resolution variant.
   */
  @Contract("null, _ -> null; !null, _ -> !null")
  public static Icon convertFromJBIcon(@Nullable Icon jbIcon, @Nullable ScaleContext ctx) {
    if (jbIcon == null) return null;

    Image image = IconLoader.toImage(jbIcon, ctx);
    if (image == null) {
      return jbIcon; // not convertable icon (e.g. with zero size)
    }
    image = convertFromJBImage(image);
    return new ImageIcon(image);
  }

  /**
   * Returns the max-size resolution variant image of the provided {@code MultiResolutionImage}.
   * If the provided image is not {@code MultiResolutionImage} then returns same image.
   */
  @Contract("null -> null; !null -> !null")
  public static Image getMaxSizeResolutionVariant(@Nullable Image mrImage) {
    if (isMultiResolutionImage(mrImage)) {
      List<Image> variants = getAccessor(mrImage).getResolutionVariants();
      int width = mrImage.getWidth(null);
      for (Image img : variants) {
        if (img.getWidth(null) >= width) {
          mrImage = img;
        }
      }
    }
    return mrImage;
  }

  /**
   * Converts from the provided icon with {@code MultiResolutionImage} to an icon with {@link JBHiDPIScaledImage}.
   * If the provided icon's image is not {@code MultiResolutionImage} then returns same icon.
   */
  @Contract("null, _ -> null; !null, _ -> !null")
  public static Icon convertFromMRIcon(@Nullable Icon mrIcon, @Nullable ScaleContext ctx) {
    if (mrIcon == null) return null;

    if (ctx == null) ctx = ScaleContext.create();
    Image image = Objects.requireNonNull(IconLoader.toImage(mrIcon, ctx));
    if (isMultiResolutionImage(image)) {
      return mrIcon;
    }
    image = getMaxSizeResolutionVariant(image);
    image = ImageUtil.ensureHiDPI(image, ctx);
    return new JBImageIcon(image);
  }

  /**
   * Returns an accessor to the provided {@code MultiResolutionImage}.
   * If the provided image is not {@code MultiResolutionImage} the resolution variants methods will default to the provided image.
   */
  @Contract("null -> null; !null -> !null")
  public static Accessor getAccessor(@Nullable Image mrImage) {
    if (mrImage == null) return null;

    if (!checkSize(mrImage)) {
      //noinspection CallToPrintStackTrace
      new IllegalArgumentException("the image has illegal size 0x0").printStackTrace();
    }
    return new Accessor(mrImage);
  }

  private static boolean checkSize(Image image) {
    return image.getWidth(null) != 0 && image.getHeight(null) != 0;
  }
}
