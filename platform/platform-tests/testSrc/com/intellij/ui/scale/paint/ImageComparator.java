// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale.paint;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;

/**
 * @author tav
 */
public final class ImageComparator {
  private final ColorComparator colorComparator;

  public interface ColorComparator {
    boolean compare(int argb1, int argb2);
  }

  /**
   * Used to smooth difference b/w antialiased images.
   */
  public static final class AASmootherComparator implements ColorComparator {
    private final double backgroundColorsDist;
    private final double inputColorsDist;
    private final int backgroundRGB;

    /**
     * The distance b/w two colors is in [0..1]. It's assumed {@code backgroundColorsDist} is less tolerant,
     * whereas {@code inputColorDist} is more tolerant.
     *
     * @param backgroundColorsDist tolerant distance b/w the input color and the background color
     * @param inputColorsDist tolerant distance b/w the input colors
     */
    public AASmootherComparator(double backgroundColorsDist, double inputColorsDist, Color background) {
      this.backgroundColorsDist = backgroundColorsDist;
      this.inputColorsDist = inputColorsDist;
      this.backgroundRGB = background.getRGB();
    }

    @Override
    public boolean compare(int argb1, int argb2) {
      if (argb1 == argb2) return true;
      if (argb1 == backgroundRGB || argb2 == backgroundRGB) {
        return dist(argb1, argb2) <= backgroundColorsDist;
      }
      return dist(argb1, argb2) <= inputColorsDist;
    }

    private static double dist(int argb1, int argb2) {
      double[] comp = diff(argb1, argb2);
      // normalize dist to [0..1]
      return Math.sqrt((comp[0] * comp[0] + comp[1] * comp[1] + comp[2] * comp[2] + comp[3] * comp[3]) / comp.length);
    }

    private static double[] diff(int argb1, int argb2) {
      double a1 = a(argb1);
      double a2 = a(argb2);
      return new double[] {
        Math.abs(a1 * a1 - a2 * a2),
        Math.abs(r(argb1) * a1 - r(argb2) * a2),
        Math.abs(g(argb1) * a1 - g(argb2) * a2),
        Math.abs(b(argb1) * a1 - b(argb2) * a2)
      };
    }

    private static double a(int argb) {
      return ((argb >> 24) & 0xFF) / 255d;
    }

    private static double r(int argb) {
      return ((argb >> 16) & 0xFF) / 255d;
    }

    private static double g(int argb) {
      return ((argb >> 8) & 0xFF) / 255d;
    }

    private static double b(int argb) {
      return (argb & 0xFF) / 255d;
    }
  }

  public ImageComparator() {
    this(null);
  }

  public ImageComparator(@Nullable ColorComparator colorComparator) {
    this.colorComparator = colorComparator != null ? colorComparator : (c1, c2) -> c1 == c2;
  }

  /**
   * BufferedImage.TYPE_INT_ARGB is expected
   */
  public static void compareAndAssert(@Nullable ColorComparator colorComparator,
                                      @NotNull Image img1, @NotNull Image img2,
                                      @Nullable String errMsgPrefix) {
    new ImageComparator(colorComparator).compareAndAssert(img1, img2, errMsgPrefix);
  }

  /**
   * BufferedImage.TYPE_INT_ARGB is expected
   */
  public void compareAndAssert(@NotNull Image img1, @NotNull Image img2, @Nullable String errMsgPrefix) {
    StringBuilder sb = new StringBuilder(errMsgPrefix == null ? "images mismatch: " : errMsgPrefix);
    if (!compare(img1, img2, sb)) {
      throw new AssertionError(sb.toString());
    }
  }

  /**
   * BufferedImage.TYPE_INT_ARGB is expected
   */
  public boolean compare(@NotNull Image img1, @NotNull Image img2, @Nullable /*OUT*/StringBuilder reason) {
    int[] d1 = getArgbData(img1);
    int[] d2 = getArgbData(img2);
    if  (d1.length != d2.length) {
      if (reason != null) //noinspection StringConcatenationInsideStringBufferAppend
        reason.append("size mismatch: " +
                      "[" + img1.getWidth(null) + "x" + img1.getHeight(null) + "] vs " +
                      "[" + img2.getWidth(null) + "x" + img2.getHeight(null) + "]");
      return false;
    }
    for (int i = 0; i < d1.length; i++) {
      if (!colorComparator.compare(d1[i], d2[i])) {
        int y = i / img1.getWidth(null);
        int x = i - y * img1.getWidth(null);
        if (reason != null)  //noinspection StringConcatenationInsideStringBufferAppend
          reason.append("colors differ at [" + x + "," + y + "]; " +
                        "0x" + Integer.toHexString(d1[i]) + " vs 0x" + Integer.toHexString(d2[i]));
        return false;
      }
    }
    return true;
  }

  private static int[] getArgbData(@NotNull Image image) {
    DataBuffer buffer = image instanceof BufferedImage ? ((BufferedImage)image).getRaster().getDataBuffer() : null;
    if (buffer instanceof DataBufferInt) {
      return ((DataBufferInt)buffer).getData();
    }
    else {
      @SuppressWarnings("UndesirableClassUsage")
      BufferedImage convertedImage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
      convertedImage.getGraphics().drawImage(image, 0, 0, null);
      return getArgbData(convertedImage);
    }
  }
}
