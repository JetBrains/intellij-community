// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.impl;

import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.ImageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderContext;
import java.awt.image.renderable.RenderableImage;
import java.io.IOException;
import java.net.URL;
import java.util.Vector;

final class JBHtmlPaneRenderableImage implements RenderableImage {

  private final @NotNull URL myImageUrl;
  private final @NotNull Component myReferenceComponent;

  private Image myImage;
  private boolean myImageLoaded;

  JBHtmlPaneRenderableImage(@NotNull URL imageUrl, @NotNull Component referenceComponent) {
    myImageUrl = imageUrl;
    myReferenceComponent = referenceComponent;
  }

  @Override
  public Vector<RenderableImage> getSources() {
    return null;
  }

  @Override
  public Object getProperty(String name) {
    return null;
  }

  @Override
  public String[] getPropertyNames() {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @Override
  public boolean isDynamic() {
    return false;
  }

  @Override
  public float getWidth() {
    return getImage().getWidth(null);
  }

  @Override
  public float getHeight() {
    return getImage().getHeight(null);
  }

  @Override
  public float getMinX() {
    return 0;
  }

  @Override
  public float getMinY() {
    return 0;
  }

  @Override
  public RenderedImage createScaledRendering(int w, int h, RenderingHints hints) {
    // TODO improve rendering of SVGs and re-render in appropriate size
    return createDefaultRendering();
  }

  @Override
  public RenderedImage createDefaultRendering() {
    return (RenderedImage)getImage();
  }

  @Override
  public RenderedImage createRendering(RenderContext renderContext) { return createDefaultRendering(); }

  private Image getImage() {
    if (!myImageLoaded) {
      Image image = loadImageFromUrl();
      myImage = ImageUtil.toBufferedImage(
        image != null ? image : ((ImageIcon)UIManager.getLookAndFeelDefaults().get("html.missingImage")).getImage(),
        false, true
      );

      myImageLoaded = true;
    }
    return myImage;
  }

  private @Nullable Image loadImageFromUrl() {
    // TODO unify with DocRenderImageManager.ManagedImage
    Image image = ImageLoader.loadFromUrl(myImageUrl);
    if (image != null &&
        image.getWidth(null) > 0 &&
        image.getHeight(null) > 0) {
      return image;
    }
    try {
      BufferedImage direct = ImageIO.read(myImageUrl);
      if (direct != null) {
        return ImageUtil.ensureHiDPI(direct, ScaleContext.create(myReferenceComponent));
      }
    }
    catch (IOException e) {
      //ignore
    }
    return image;
  }
}
