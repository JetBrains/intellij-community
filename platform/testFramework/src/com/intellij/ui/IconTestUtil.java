// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.ImageObserver;
import java.text.AttributedCharacterIterator;
import java.util.Collections;
import java.util.List;

public final class IconTestUtil {
  @Nullable
  public static String getIconPath(Icon icon) {
    icon = unwrapRetrievableIcon(icon);
    return ((IconLoader.CachedImageIcon)icon).getOriginalPath();
  }

  public static Icon unwrapRetrievableIcon(Icon icon) {
    while (icon instanceof RetrievableIcon) {
      icon = ((RetrievableIcon)icon).retrieveIcon();
    }
    return icon;
  }

  @NotNull
  static List<Icon> autopsyIconsFrom(@NotNull Icon icon) {
    if (icon instanceof RetrievableIcon) {
      return autopsyIconsFrom(((RetrievableIcon)icon).retrieveIcon());
    }
    if (icon instanceof LayeredIcon) {
      return ContainerUtil.flatten(ContainerUtil.map(((LayeredIcon)icon).getAllLayers(), IconTestUtil::autopsyIconsFrom));
    }
    if (icon instanceof RowIcon) {
      return ContainerUtil.flatten(ContainerUtil.map(((RowIcon)icon).getAllIcons(), IconTestUtil::autopsyIconsFrom));
    }
    return Collections.singletonList(icon);
  }

  @NotNull
  public static List<Icon> renderDeferredIcon(Icon icon) {
    icon.paintIcon(new JLabel(), createMockGraphics(), 0, 0);  // force to eval
    TimeoutUtil.sleep(1000); // give chance to evaluate
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.dispatchAllInvocationEvents();

    return autopsyIconsFrom(icon);
  }

  @NotNull
  public static Graphics createMockGraphics() {
    return new Graphics() {
        @Override
        public Graphics create() {
          return this;
        }

        @Override
        public void translate(int x, int y) {

        }

        @Override
        public Color getColor() {
          return null;
        }

        @Override
        public void setColor(Color c) {

        }

        @Override
        public void setPaintMode() {

        }

        @Override
        public void setXORMode(Color c1) {

        }

        @Override
        public Font getFont() {
          return null;
        }

        @Override
        public void setFont(Font font) {

        }

        @Override
        public FontMetrics getFontMetrics(Font f) {
          return null;
        }

        @Override
        public Rectangle getClipBounds() {
          return null;
        }

        @Override
        public void clipRect(int x, int y, int width, int height) {

        }

        @Override
        public void setClip(int x, int y, int width, int height) {

        }

        @Override
        public Shape getClip() {
          return null;
        }

        @Override
        public void setClip(Shape clip) {

        }

        @Override
        public void copyArea(int x, int y, int width, int height, int dx, int dy) {

        }

        @Override
        public void drawLine(int x1, int y1, int x2, int y2) {

        }

        @Override
        public void fillRect(int x, int y, int width, int height) {

        }

        @Override
        public void clearRect(int x, int y, int width, int height) {

        }

        @Override
        public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {

        }

        @Override
        public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {

        }

        @Override
        public void drawOval(int x, int y, int width, int height) {

        }

        @Override
        public void fillOval(int x, int y, int width, int height) {

        }

        @Override
        public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {

        }

        @Override
        public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {

        }

        @Override
        public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {

        }

        @Override
        public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {

        }

        @Override
        public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {

        }

        @Override
        public void drawString(String str, int x, int y) {

        }

        @Override
        public void drawString(AttributedCharacterIterator iterator, int x, int y) {

        }

        @Override
        public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
          return false;
        }

        @Override
        public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
          return false;
        }

        @Override
        public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
          return false;
        }

        @Override
        public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
          return false;
        }

        @Override
        public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
          return false;
        }

        @Override
        public boolean drawImage(Image img,
                                 int dx1,
                                 int dy1,
                                 int dx2,
                                 int dy2,
                                 int sx1,
                                 int sy1,
                                 int sx2,
                                 int sy2,
                                 Color bgcolor,
                                 ImageObserver observer) {
          return false;
        }

        @Override
        public void dispose() {

        }
      };
  }
}
