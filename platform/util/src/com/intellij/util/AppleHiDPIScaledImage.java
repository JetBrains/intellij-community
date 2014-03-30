package com.intellij.util;

import apple.awt.CImage;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Konstantin Bulenkov
 */
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
