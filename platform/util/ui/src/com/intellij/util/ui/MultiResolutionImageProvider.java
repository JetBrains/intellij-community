// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.MethodInvocator;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImageOp;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;

/**
 * Provides access (or converter) to {@code java.awt.image.MultiResolutionImage} available since JDK 9.
 *
 * @author tav
 */
@ApiStatus.Experimental
public class MultiResolutionImageProvider {
  /**
   * An accessor to the {@code MultiResolutionImage}'s resolution variants methods.
   */
  public static class Accessor {
    private static final Class MRI_CLASS;
    private static final MethodInvocator GET_RESOLUTION_VARIANTS_METHOD;
    private static final MethodInvocator GET_RESOLUTION_VARIANT_METHOD;

    private final Image myMRImage;

    static {
      Class cls = null;
      MethodInvocator m1 = null;
      MethodInvocator m2 = null;
      if (SystemInfo.IS_AT_LEAST_JAVA9) {
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
  private static class Converter {
    private static final Constructor BMRI_CLASS_CTOR;

    static {
      Class cls = null;
      Constructor ctor = null;
      if (SystemInfo.IS_AT_LEAST_JAVA9) {
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
  @Nullable
  public static Image convertFromJBImage(@Nullable Image jbImage) {
    if (jbImage == null) return null;

    if (!checkSize(jbImage)) {
      //noinspection CallToPrintStackTrace
      new IllegalArgumentException("the image has illegal size 0x0").printStackTrace();
    }
    return Converter.convert(jbImage);
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
