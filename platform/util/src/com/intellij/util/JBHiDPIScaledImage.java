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

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

/**
* @author Konstantin Bulenkov
*/
public class JBHiDPIScaledImage extends BufferedImage {
  private final Image myImage;
  private final int myWidth;
  private final int myHeight;

  /**
   * Creates a HiDPI-aware BufferedImage to draw on.
   *
   * @param width width in a user space coordinates
   * @param height height in a user space coordinates
   * @param type type of the created image
   */
  public JBHiDPIScaledImage(int width, int height, int type) {
    super(2 * width, 2 * height, type);
    myImage = null;
    myWidth = width;
    myHeight = height;
  }

  /**
   * Creates a HiDPI-aware BufferedImage wrapping a delegate Image.
   *
   * @param image the delegate image of size in a device space coordinates to wrap
   * @param width width in a user space coordinates
   * @param height height in a user space coordinates
   * @param type type of the created image
   */
  public JBHiDPIScaledImage(@NotNull Image image, int width, int height, int type) {
    super(1, 1, type); // give the wrapper a minimal 1x1 size to save space
    myImage = image;
    myWidth = width;
    myHeight = height;
  }

  public Image getDelegate() {
    return myImage;
  }

  @Override
  public int getWidth() {
    return myWidth;
  }

  @Override
  public int getHeight() {
    return myHeight;
  }

  @Override
  public int getWidth(ImageObserver observer) {
    return myWidth;
  }

  @Override
  public int getHeight(ImageObserver observer) {
    return myHeight;
  }

  @Override
  public Graphics2D createGraphics() {
    assert myImage == null : "graphics should only be created for the image used for drawing";
    Graphics2D g = super.createGraphics();
    if (myImage == null) {
      return new HiDPIScaledGraphics(g);
    }
    return g;
  }
}
