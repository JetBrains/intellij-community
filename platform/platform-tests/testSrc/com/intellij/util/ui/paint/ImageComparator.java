// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.paint;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * @author tav
 */
public class ImageComparator {
  private ColorComparator colorComparator;

  public interface ColorComparator {
    boolean compare(int argb1, int argb2);
  }

  /**
   * Smooths difference in greyscale antialiasing.
   */
  public static class GreyscaleAASmoother implements ColorComparator {
    private static final int FG_COLOR = Color.WHITE.getRGB();
    private static final int BG_COLOR = Color.BLACK.getRGB();

    private float boundaryColorsDist;
    private float medianColorsDist;

    /**
     * The distance b/w two colors are in [0..1]. It's assumed {@code boundaryColorDist} is relatively small,
     * whereas {@code medianColorDist} is larger.
     *
     * @param boundaryColorsDist tolerant distance b/w the input color and the boundary (BG or FG) color
     * @param medianColorsDist tolerant distance b/w the input colors on the median values (b/w the boundaries)
     */
    public GreyscaleAASmoother(float boundaryColorsDist, float medianColorsDist) {
      this.boundaryColorsDist = boundaryColorsDist;
      this.medianColorsDist = medianColorsDist;
    }

    @Override
    public boolean compare(int argb1, int argb2) {
      if (argb1 == argb2) return true;
      if (FG_COLOR == argb1 || FG_COLOR == argb2 || BG_COLOR == argb1 || BG_COLOR == argb2) {
        return dist(argb1, argb2) <= boundaryColorsDist;
      }
      else {
        return dist(argb1, argb2) <= medianColorsDist;
      }
    }

    private float dist(int argb1, int argb2) {
      int a1 = (argb1 >> 24 & 0xff) / 0xff;
      int a2 = (argb2 >> 24 & 0xff) / 0xff;
      // colors are grey
      return Math.abs((argb1 & 0xff) * a1 - (argb2 & 0xff) * a2) / 255f;
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
  public boolean compare(@NotNull BufferedImage img1, @NotNull BufferedImage img2) {
    return compare(img1, img2, null);
  }

  public boolean compare(@NotNull BufferedImage img1, @NotNull BufferedImage img2, @Nullable /*OUT*/StringBuilder reason) {
    int[] d1 = ((DataBufferInt)img1.getRaster().getDataBuffer()).getData();
    int[] d2 = ((DataBufferInt)img2.getRaster().getDataBuffer()).getData();
    if  (d1.length != d2.length) {
      if (reason != null) reason.append("size mismatch: " +
                                        "[" + img1.getWidth() + "x" + img1.getHeight() + "] vs " +
                                        "[" + img2.getWidth() + "x" + img2.getHeight() + "]");
      return false;
    }
    for (int i = 0; i < d1.length; i++) {
      if (!colorComparator.compare(d1[i], d2[i])) {
        int y = i / img1.getWidth();
        int x = i - y * img1.getWidth();
        if (reason != null) reason.append("colors differ at [" + x + "," + y + "]; " +
                                          "0x" + Integer.toHexString(d1[i]) + " vs 0x" + Integer.toHexString(d2[i]));
        return false;
      }
    }
    return true;
  }
}
