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
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.JBUI.ScaleContextSupport;
import com.intellij.util.ui.JBUI.ScaleContextAware;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
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

  private void updateIcon(Icon icon, ScaleContext ctx, Runnable r) {
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

    myTopLeft.paintIcon(c, g, x, y);
    myTopRight.paintIcon(c, g, x + width - myTopRight.getIconWidth(), y);
    myBottomRight.paintIcon(c, g, x + width - myBottomRight.getIconWidth(), y + height - myBottomRight.getIconHeight());
    myBottomLeft.paintIcon(c, g, x, y + height - myBottomLeft.getIconHeight());

    for (int _x = myTopLeft.getIconWidth(); _x < width - myTopRight.getIconWidth(); _x++) {
      myCroppedTop.paintIcon(c, g, _x + x, y);
    }
    for (int _x = myBottomLeft.getIconWidth(); _x < width - myBottomLeft.getIconWidth(); _x++) {
      myCroppedBottom.paintIcon(c, g, _x + x, y + height - bottomSize);
    }
    for (int _y = myTopLeft.getIconHeight(); _y < height - myBottomLeft.getIconHeight(); _y++) {
      myCroppedLeft.paintIcon(c, g, x, _y + y);
    }
    for (int _y = myTopRight.getIconHeight(); _y < height - myBottomRight.getIconHeight(); _y++) {
      myCroppedRight.paintIcon(c, g, x + width - rightSize, _y + y);
    }

    if (myBorderColor != null) {
      g.setColor(myBorderColor);
      g.drawRect(x + leftSize - 1, y + topSize - 1, width - leftSize - rightSize + 1, height - topSize - bottomSize + 1);
    }
  }
}
