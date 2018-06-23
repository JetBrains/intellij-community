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
package com.intellij.ui;

import com.intellij.icons.AllIcons.Ide.Notification.Shadow;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Alexander Lobas
 */
public class NotificationBalloonShadowBorderProvider implements BalloonImpl.ShadowBorderProvider {
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

    int topLeftWidth = Shadow.Top_left.getIconWidth();
    int topLeftHeight = Shadow.Top_left.getIconHeight();

    int topRightWidth = Shadow.Top_right.getIconWidth();
    int topRightHeight = Shadow.Top_right.getIconHeight();

    int bottomLeftWidth = Shadow.Bottom_left.getIconWidth();
    int bottomLeftHeight = Shadow.Bottom_left.getIconHeight();

    int bottomRightWidth = Shadow.Bottom_right.getIconWidth();
    int bottomRightHeight = Shadow.Bottom_right.getIconHeight();

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

    Shadow.Top_left.paintIcon(component, g, 0, 0);
    Shadow.Top_right.paintIcon(component, g, width - topRightWidth, 0);
    Shadow.Bottom_right.paintIcon(component, g, width - bottomRightWidth, height - bottomRightHeight);
    Shadow.Bottom_left.paintIcon(component, g, 0, height - bottomLeftHeight);
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
      ImageIcon imageIcon = (ImageIcon)IconLoader.getIconSnapshot(icon);
      if (horizontal) {
        UIUtil.drawImage(g, imageIcon.getImage(),
                         new Rectangle(lastValue, start2, length - calcLength, imageIcon.getIconHeight()),
                         new Rectangle(0, 0, length - calcLength, imageIcon.getIconHeight()),
                         component);
      }
      else {
        UIUtil.drawImage(g, imageIcon.getImage(),
                         new Rectangle(start2, lastValue, imageIcon.getIconWidth(), length - calcLength),
                         new Rectangle(0, 0, imageIcon.getIconWidth(), length - calcLength),
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