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

import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

/**
* @author Konstantin Bulenkov
* @author tav
*/
public class JBHiDPIScaledImage extends BufferedImage {
  private final @Nullable Image myImage;
  private final int myUserWidth;
  private final int myUserHeight;
  private final float myScale;

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the system default scale.
   *
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   */
  public JBHiDPIScaledImage(int width, int height, int type) {
    this((GraphicsConfiguration)null, width, height, type);
  }

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the graphics scale.
   *
   * @param g the graphics which provides the target scale
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   */
  public JBHiDPIScaledImage(@Nullable Graphics2D g, int width, int height, int type) {
    super((int)(width * JBUI.sysScale(g)), (int)(height * JBUI.sysScale(g)), type);
    myImage = null;
    myUserWidth = width;
    myUserHeight = height;
    myScale = JBUI.sysScale(g);
  }

  /**
   * Creates a scaled HiDPI-aware BufferedImage, targeting the graphics config.
   *
   * @param gc the graphics config which provides the target scale
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   */
  public JBHiDPIScaledImage(@Nullable GraphicsConfiguration gc, int width, int height, int type) {
    super((int)(width * JBUI.sysScale(gc)), (int)(height * JBUI.sysScale(gc)), type);
    myImage = null;
    myUserWidth = width;
    myUserHeight = height;
    myScale = JBUI.sysScale(gc);
  }

  /**
   * Creates a HiDPI-aware BufferedImage wrapper for the provided scaled raw image.
   * The wrapper image will represent the scaled raw image in user coordinate space.
   *
   * @param image the scaled raw image
   * @param width the width in user coordinate space
   * @param height the height in user coordinate space
   * @param type the type
   */
  public JBHiDPIScaledImage(@NotNull Image image, int width, int height, int type) {
    super(1, 1, type); // a dummy wrapper
    myImage = image;
    myUserWidth = width;
    myUserHeight = height;
    myScale = myUserWidth > 0 ? myImage.getWidth(null) / myUserWidth : 1f;
  }

  public float getScale() {
    return myScale;
  }

  /**
   * Returns JBHiDPIScaledImage of the same structure scaled by the provided factor.
   *
   * @param scaleFactor the scale factor
   * @return scaled instance
   */
  public JBHiDPIScaledImage scale(float scaleFactor) {
    Image img = myImage == null ? this: myImage;

    int w = (int)(scaleFactor * getRealWidth(null));
    int h = (int)(scaleFactor * getRealHeight(null));
    if (w <= 0 || h <= 0) return this;

    Image scaled = Scalr.resize(ImageUtil.toBufferedImage(img), Scalr.Method.QUALITY, w, h);

    int newUserWidth = (int)(w / this.myScale);
    int newUserHeight = (int)(h / this.myScale);

    if (myImage != null) {
      return new JBHiDPIScaledImage(scaled, newUserWidth, newUserHeight, getType());
    }
    JBHiDPIScaledImage newImg = new JBHiDPIScaledImage(newUserWidth, newUserHeight, getType());
    Graphics2D g = newImg.createGraphics();
    g.drawImage(scaled, 0, 0, newUserWidth, newUserHeight, 0, 0, scaled.getWidth(null), scaled.getHeight(null), null);
    g.dispose();
    return newImg;
  }

  public Image getDelegate() {
    return myImage;
  }

  /**
   * Returns the width in user coordinate space for the image created as a wrapper,
   * and the real width for the image created as a scaled one.
   *
   * @return the width
   */
  @Override
  public int getWidth() {
    return getWidth(null);
  }

  /**
   * Returns the height in user coordinate space for the image created as a wrapper,
   * and the real height for the image created as a scaled one.
   *
   * @return the height
   */
  @Override
  public int getHeight() {
    return getHeight(null);
  }

  /**
   * Returns the width in user coordinate space for the image created as a wrapper,
   * and the real width for the image created as a scaled one.
   *
   * @return the width
   */
  @Override
  public int getWidth(ImageObserver observer) {
    return myImage != null ? getUserWidth(observer) : getRealWidth(observer);
  }

  /**
   * Returns the height in user coordinate space for the image created as a wrapper,
   * and the real height for the image created as a scaled one.
   *
   * @return the height
   */
  @Override
  public int getHeight(ImageObserver observer) {
    return myImage != null ? getUserHeight(observer) : getRealHeight(observer);
  }

  /**
   * Returns the width in user coordinate space.
   *
   * @param observer the image observer
   * @return the width
   */
  public int getUserWidth(ImageObserver observer) {
    return myImage != null ? myUserWidth : (int)(super.getWidth(observer) / myScale);
  }

  /**
   * Returns the height in user coordinate space.
   *
   * @param observer the image observer
   * @return the height
   */
  public int getUserHeight(ImageObserver observer) {
    return myImage != null ? myUserHeight : (int)(super.getHeight(observer) / myScale);
  }

  /**
   * Returns the real width.
   *
   * @param observer the image observer
   * @return the width
   */
  public int getRealWidth(ImageObserver observer) {
    return myImage != null ? myImage.getWidth(observer) : super.getWidth(observer);
  }

  /**
   * Returns the real height.
   *
   * @param observer the image observer
   * @return the height
   */
  public int getRealHeight(ImageObserver observer) {
    return myImage != null ? myImage.getHeight(observer) : super.getHeight(observer);
  }

  @Override
  public Graphics2D createGraphics() {
    Graphics2D g = super.createGraphics();
    if (myImage == null) {
      g.scale(myScale, myScale);
      return new HiDPIScaledGraphics(g);
    }
    return g;
  }
}
