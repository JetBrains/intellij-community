/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.ui.impl;

import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.JBUI.ScaleContextAware;
import com.intellij.util.ui.JBUI.ScaleContextSupport;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * @author Konstantin Bulenkov
 */
public class ShadowPainter extends ScaleContextSupport<ScaleContext> {
  private final Icon myTop;
  private final Icon myTopRight;
  private final Icon myRight;
  private final Icon myBottomRight;
  private final Icon myBottom;
  private final Icon myBottomLeft;
  private final Icon myLeft;
  private final Icon myTopLeft;

  private Icon myCroppedTop = null;
  private Icon myCroppedRight = null;
  private Icon myCroppedBottom = null;
  private Icon myCroppedLeft = null;

  @Nullable
  private Color myBorderColor;

  public ShadowPainter(Icon top, Icon topRight, Icon right, Icon bottomRight, Icon bottom, Icon bottomLeft, Icon left, Icon topLeft) {
    super(ScaleContext.create());
    myTop = top;
    myTopRight = topRight;
    myRight = right;
    myBottomRight = bottomRight;
    myBottom = bottom;
    myBottomLeft = bottomLeft;
    myLeft = left;
    myTopLeft = topLeft;

    updateIcons(null);
  }

  public ShadowPainter(Icon top, Icon topRight, Icon right, Icon bottomRight, Icon bottom, Icon bottomLeft, Icon left, Icon topLeft, @Nullable Color borderColor) {
    this(top, topRight, right, bottomRight, bottom, bottomLeft, left, topLeft);
    myBorderColor = borderColor;
  }

  public void setBorderColor(@Nullable Color borderColor) {
    myBorderColor = borderColor;
  }

  public BufferedImage createShadow(final JComponent c, final int width, final int height) {
    final BufferedImage image = c.getGraphicsConfiguration().createCompatibleImage(width, height, Transparency.TRANSLUCENT);
    final Graphics2D g = image.createGraphics();

    paintShadow(c, g, 0, 0, width, height);

    g.dispose();
    return image;
  }

  private void updateIcons(ScaleContext ctx) {
    updateIcon(myTop, ctx, () -> myCroppedTop = IconUtil.cropIcon(myTop, 1, Integer.MAX_VALUE));
    updateIcon(myTopRight, ctx, null);
    updateIcon(myRight, ctx, () -> myCroppedRight = IconUtil.cropIcon(myRight, Integer.MAX_VALUE, 1));
    updateIcon(myBottomRight, ctx, null);
    updateIcon(myBottom, ctx, () -> myCroppedBottom = IconUtil.cropIcon(myBottom, 1, Integer.MAX_VALUE));
    updateIcon(myBottomLeft, ctx, null);
    updateIcon(myLeft, ctx, () -> myCroppedLeft = IconUtil.cropIcon(myLeft, Integer.MAX_VALUE, 1));
    updateIcon(myTopLeft, ctx, null);
  }

  private static void updateIcon(Icon icon, ScaleContext ctx, Runnable r) {
    if (icon instanceof ScaleContextAware) ((ScaleContextAware)icon).updateScaleContext(ctx);
    if (r != null) r.run();
  }

  public void paintShadow(Component c, Graphics2D g, int x, int y, int width, int height) {
    ScaleContext ctx = ScaleContext.create(c);
    if (updateScaleContext(ctx)) {
      updateIcons(ctx);
    }
    final int leftSize = myCroppedLeft.getIconWidth();
    final int rightSize = myCroppedRight.getIconWidth();
    final int bottomSize = myCroppedBottom.getIconHeight();
    final int topSize = myCroppedTop.getIconHeight();

    int delta = myTopLeft.getIconHeight() + myBottomLeft.getIconHeight() - height;
    if (delta > 0) { // Corner icons are overlapping. Need to handle this
      Shape clip = g.getClip();

      int topHeight = myTopLeft.getIconHeight() - delta / 2;
      Area top = new Area(new Rectangle2D.Float(x, y, width, topHeight));
      if (clip != null) {
        top.intersect(new Area(clip));
      }
      g.setClip(top);

      myTopLeft.paintIcon(c, g, x, y);
      myTopRight.paintIcon(c, g, x + width - myTopRight.getIconWidth(), y);

      int bottomHeight = myBottomLeft.getIconHeight() - delta + delta / 2;
      Area bottom = new Area(new Rectangle2D.Float(x, y + topHeight, width, bottomHeight));
      if (clip != null) {
        bottom.intersect(new Area(clip));
      }
      g.setClip(bottom);

      myBottomLeft.paintIcon(c, g, x, y + height - myBottomLeft.getIconHeight());
      myBottomRight.paintIcon(c, g, x + width - myBottomRight.getIconWidth(), y + height - myBottomRight.getIconHeight());

      g.setClip(clip);
    } else {
      myTopLeft.paintIcon(c, g, x, y);
      myTopRight.paintIcon(c, g, x + width - myTopRight.getIconWidth(), y);
      myBottomLeft.paintIcon(c, g, x, y + height - myBottomLeft.getIconHeight());
      myBottomRight.paintIcon(c, g, x + width - myBottomRight.getIconWidth(), y + height - myBottomRight.getIconHeight());
    }

    fill(g, myCroppedTop, x, y, myTopLeft.getIconWidth(), width - myTopRight.getIconWidth(), true);
    fill(g, myCroppedBottom, x, y + height - bottomSize, myBottomLeft.getIconWidth(), width - myBottomRight.getIconWidth(), true);
    fill(g, myCroppedLeft, x, y, myTopLeft.getIconHeight(), height - myBottomLeft.getIconHeight(), false);
    fill(g, myCroppedRight, x + width - rightSize, y, myTopRight.getIconHeight(), height - myBottomRight.getIconHeight(), false);

    if (myBorderColor != null) {
      g.setColor(myBorderColor);
      g.drawRect(x + leftSize - 1, y + topSize - 1, width - leftSize - rightSize + 1, height - topSize - bottomSize + 1);
    }
  }

  private static void fill(Graphics g, Icon pattern, int x, int y, int from, int to, boolean horizontally) {
    double scale = JBUI.sysScale((Graphics2D)g);
    if (UIUtil.isJreHiDPIEnabled() && Math.ceil(scale) > scale) {
      // Direct painting for fractional scale
      BufferedImage img = ImageUtil.toBufferedImage(IconUtil.toImage(pattern));
      int patternSize = horizontally ? img.getWidth() : img.getHeight();
      Graphics2D g2d = (Graphics2D)g.create();
      try {
        g2d.scale(1 / scale, 1 / scale);
        g2d.translate(x * scale, y * scale);
        for (int at = (int)Math.floor(from * scale); at < to * scale; at += patternSize) {
          if (horizontally) {
            g2d.drawImage(img, at, 0, null);
          }
          else {
            g2d.drawImage(img, 0, at, null);
          }
        }
      } finally {
        g2d.dispose();
      }
    }
    else {
      for (int at = from; at < to; at++) {
        if (horizontally) {
          pattern.paintIcon(null, g, x + at, y);
        }
        else {
          pattern.paintIcon(null, g, x, y + at);
        }
      }
    }
  }
}
