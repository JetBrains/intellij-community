// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import apple.awt.CImage;

import java.awt.*;
import java.awt.image.BufferedImage;

/** @deprecated Apple JRE is no longer supported (to be removed in IDEA 2019) */
@Deprecated
public class AppleHiDPIScaledImage {
  public static BufferedImage create(int width, int height, int imageType) {
    return new CImage.HiDPIScaledImage(width, height, imageType) {
      @Override
      protected void drawIntoImage(BufferedImage image, float scale) {
      }
    };
  }

  public static boolean is(Image image) {
    return image instanceof CImage.HiDPIScaledImage;
  }
}
