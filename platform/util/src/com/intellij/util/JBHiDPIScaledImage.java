/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

/**
* @author Konstantin Bulenkov
*/
public class JBHiDPIScaledImage extends BufferedImage {
  private final Image myImage;
  private int myWidth;  // == myImage.width / scale
  private int myHeight; // == myImage.height / scale

  public JBHiDPIScaledImage(int width, int height, int type) {
    this(null, 2 * width, 2 * height, type);
  }

  public JBHiDPIScaledImage(Image image, int width, int height, int type) {
    // In case there's a delegate image, create a dummy wrapper image of 1x1 size
    super(image != null ? 1 : width, image != null ? 1 : height, type);
    myImage = image;
    myWidth = width;
    myHeight = height;
  }

  public Image getDelegate() {
    return myImage;
  }

  @Override
  public int getWidth() {
    return myImage != null ? myWidth : super.getWidth();
  }

  @Override
  public int getHeight() {
    return myImage != null ? myHeight : super.getHeight();
  }

  @Override
  public int getWidth(ImageObserver observer) {
    return myImage != null ? myWidth : super.getWidth(observer);
  }

  @Override
  public int getHeight(ImageObserver observer) {
    return myImage != null ? myHeight : super.getHeight(observer);
  }

  @Override
  public Graphics2D createGraphics() {
    Graphics2D g = super.createGraphics();
    if (myImage == null) {
      return new HiDPIScaledGraphics(g, this);
    }
    return g;
  }
}
