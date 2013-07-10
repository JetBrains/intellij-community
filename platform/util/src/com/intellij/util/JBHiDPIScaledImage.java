package com.intellij.util;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
* @author Konstantin Bulenkov
*/
public class JBHiDPIScaledImage extends BufferedImage {
  private final Image myImage;

  public JBHiDPIScaledImage(Image image, int width, int height, int type) {
    super(width, height, type);
    myImage = image;
  }

  public Image getDelegate() {
    return myImage;
  }
}
