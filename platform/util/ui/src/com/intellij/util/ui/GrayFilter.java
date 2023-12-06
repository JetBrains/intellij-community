// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.image.RGBImageFilter;
import java.util.Objects;

@ApiStatus.Internal
@ApiStatus.NonExtendable
public class GrayFilter extends RGBImageFilter {
  private final float brightness;
  private final float contrast;
  private final int alpha;

  private final int origContrast;
  private final int origBrightness;

  /**
   * @param brightness in range [-100..100] where 0 has no effect
   * @param contrast   in range [-100..100] where 0 has no effect
   * @param alpha      in range [0..100] where 0 is transparent, 100 has no effect
   */
  public GrayFilter(int brightness, int contrast, int alpha) {
    origBrightness = Math.max(-100, Math.min(100, brightness));
    this.brightness = (float)(Math.pow(origBrightness, 3) / (100f * 100f)); // cubic in [0..100]

    origContrast = Math.max(-100, Math.min(100, contrast));
    this.contrast = origContrast / 100f;

    this.alpha = Math.max(0, Math.min(100, alpha));
  }

  public GrayFilter() {
    this(0, 0, 100);
  }

  public int getBrightness() {
    return origBrightness;
  }

  public int getContrast() {
    return origContrast;
  }

  public int getAlpha() {
    return alpha;
  }

  @Override
  @SuppressWarnings("AssignmentReplaceableWithOperatorAssignment")
  public int filterRGB(int x, int y, int rgb) {
    // Use NTSC conversion formula.
    int gray = (int)(0.30 * (rgb >> 16 & 0xff) +
                     0.59 * (rgb >> 8 & 0xff) +
                     0.11 * (rgb & 0xff));

    if (brightness >= 0) {
      gray = (int)((gray + brightness * 255) / (1 + brightness));
    }
    else {
      gray = (int)(gray / (1 - brightness));
    }

    if (contrast >= 0) {
      if (gray >= 127) {
        gray = (int)(gray + (255 - gray) * contrast);
      }
      else {
        gray = (int)(gray - gray * contrast);
      }
    }
    else {
      gray = (int)(127 + (gray - 127) * (contrast + 1));
    }

    int a = ((rgb >> 24) & 0xff) * alpha / 100;

    return (a << 24) | (gray << 16) | (gray << 8) | gray;
  }

  public static @NotNull GrayFilter asUIResource(int brightness, int contrast, int alpha) {
    return new GrayFilterUIResource(brightness, contrast, alpha);
  }

  private static final class GrayFilterUIResource extends GrayFilter implements UIResource {
    private GrayFilterUIResource(int brightness, int contrast, int alpha) {
      super(brightness, contrast, alpha);
    }
  }

  public static @NotNull GrayFilter namedFilter(@NotNull String resourceName, @NotNull GrayFilter defaultFilter) {
    return Objects.requireNonNullElse((GrayFilter)UIManager.get(resourceName), defaultFilter);
  }
}