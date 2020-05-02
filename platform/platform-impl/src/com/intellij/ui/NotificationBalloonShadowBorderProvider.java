// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.icons.AllIcons.Ide.Shadow;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Alexander Lobas
 */
public final class NotificationBalloonShadowBorderProvider implements BalloonImpl.ShadowBorderProvider {
  private static final Insets INSETS = new JBInsets(4, 6, 8, 6);
  private final Color myFillColor;
  private final Color myBorderColor;

  public NotificationBalloonShadowBorderProvider(@NotNull Color fillColor, @NotNull Color borderColor) {
    myFillColor = fillColor;
    myBorderColor = borderColor;
  }

  @NotNull
  @Override
  public Insets getInsets() {
    return INSETS;
  }

  @Override
  public void paintShadow(@NotNull JComponent component, @NotNull Graphics g) {
    int width = component.getWidth();
    int height = component.getHeight();

    int topLeftWidth = Shadow.TopLeft.getIconWidth();
    int topLeftHeight = Shadow.TopLeft.getIconHeight();

    int topRightWidth = Shadow.TopRight.getIconWidth();
    int topRightHeight = Shadow.TopRight.getIconHeight();

    int bottomLeftWidth = Shadow.BottomLeft.getIconWidth();
    int bottomLeftHeight = Shadow.BottomLeft.getIconHeight();

    int bottomRightWidth = Shadow.BottomRight.getIconWidth();
    int bottomRightHeight = Shadow.BottomRight.getIconHeight();

    int topWidth = Shadow.Top.getIconWidth();

    int bottomWidth = Shadow.Bottom.getIconWidth();
    int bottomHeight = Shadow.Bottom.getIconHeight();

    int leftHeight = Shadow.Left.getIconHeight();

    int rightWidth = Shadow.Right.getIconWidth();
    int rightHeight = Shadow.Right.getIconHeight();

    drawLine(component, g, Shadow.Top, width, topLeftWidth, topRightWidth, topWidth, 0, true);
    drawLine(component, g, Shadow.Bottom, width, bottomLeftWidth, bottomRightWidth, bottomWidth, height - bottomHeight, true);

    drawLine(component, g, Shadow.Left, height, topLeftHeight, bottomLeftHeight, leftHeight, 0, false);
    drawLine(component, g, Shadow.Right, height, topRightHeight, bottomRightHeight, rightHeight, width - rightWidth, false);

    Shadow.TopLeft.paintIcon(component, g, 0, 0);
    Shadow.TopRight.paintIcon(component, g, width - topRightWidth, 0);
    Shadow.BottomRight.paintIcon(component, g, width - bottomRightWidth, height - bottomRightHeight);
    Shadow.BottomLeft.paintIcon(component, g, 0, height - bottomLeftHeight);
  }

  private static void drawLine(@NotNull JComponent component,
                               @NotNull Graphics g,
                               @NotNull Icon icon,
                               int fullLength,
                               int start,
                               int end,
                               int step,
                               int start2,
                               boolean horizontal) {
    int length = fullLength - start - end;
    int count = length / step;
    int calcLength = step * count;
    int lastValue = start + calcLength;

    if (horizontal) {
      for (int i = start; i < lastValue; i += step) {
        icon.paintIcon(component, g, i, start2);
      }
    }
    else {
      for (int i = start; i < lastValue; i += step) {
        icon.paintIcon(component, g, start2, i);
      }
    }

    if (calcLength < length) {
      Icon iconSnapshot = IconLoader.getIconSnapshot(icon);
      Image image = IconUtil.toImage(iconSnapshot, ScaleContext.create(component));
      if (horizontal) {
        StartupUiUtil.drawImage(g, image,
                                new Rectangle(lastValue, start2, length - calcLength, iconSnapshot.getIconHeight()),
                                new Rectangle(0, 0, length - calcLength, iconSnapshot.getIconHeight()),
                                component);
      }
      else {
        UIUtil.drawImage(g, image,
                         new Rectangle(start2, lastValue, iconSnapshot.getIconWidth(), length - calcLength),
                         new Rectangle(0, 0, iconSnapshot.getIconWidth(), length - calcLength),
                         component);
      }
    }
  }

  @Override
  public void paintBorder(@NotNull Rectangle bounds, @NotNull Graphics2D g) {
    g.setColor(myFillColor);
    g.fill(new Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height));
    g.setColor(myBorderColor);
    g.draw(new RoundRectangle2D.Double(bounds.x + 0.5, bounds.y + 0.5, bounds.width - 1, bounds.height - 1, 3, 3));
  }

  @Override
  public void paintPointingShape(@NotNull Rectangle bounds,
                                 @NotNull Point pointTarget,
                                 @NotNull Balloon.Position position,
                                 @NotNull Graphics2D g) {
    int x, y, length;

    if (position == Balloon.Position.above) {
      length = INSETS.bottom;
      x = pointTarget.x;
      y = bounds.y + bounds.height + length;
    }
    else if (position == Balloon.Position.below) {
      length = INSETS.top;
      x = pointTarget.x;
      y = bounds.y - length;
    }
    else if (position == Balloon.Position.atRight) {
      length = INSETS.left;
      x = bounds.x - length;
      y = pointTarget.y;
    }
    else {
      length = INSETS.right;
      x = bounds.x + bounds.width + length;
      y = pointTarget.y;
    }

    Polygon p = new Polygon();
    p.addPoint(x, y);

    length += 2;
    if (position == Balloon.Position.above) {
      p.addPoint(x - length, y - length);
      p.addPoint(x + length, y - length);
    }
    else if (position == Balloon.Position.below) {
      p.addPoint(x - length, y + length);
      p.addPoint(x + length, y + length);
    }
    else if (position == Balloon.Position.atRight) {
      p.addPoint(x + length, y - length);
      p.addPoint(x + length, y + length);
    }
    else {
      p.addPoint(x - length, y - length);
      p.addPoint(x - length, y + length);
    }

    g.setColor(myFillColor);
    g.fillPolygon(p);

    g.setColor(myBorderColor);

    length -= 2;
    if (position == Balloon.Position.above) {
      g.drawLine(x, y, x - length, y - length);
      g.drawLine(x, y, x + length, y - length);
    }
    else if (position == Balloon.Position.below) {
      g.drawLine(x, y, x - length, y + length);
      g.drawLine(x, y, x + length, y + length);
    }
    else if (position == Balloon.Position.atRight) {
      g.drawLine(x, y, x + length, y - length);
      g.drawLine(x, y, x + length, y + length);
    }
    else {
      g.drawLine(x, y, x - length, y - length);
      g.drawLine(x, y, x - length, y + length);
    }
  }
}