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
import com.intellij.util.ui.JBInsets;
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


    Rectangle clipBounds = g.getClipBounds();

    g.setClip(topLeftWidth, 0, width - topLeftWidth - topRightWidth, Shadow.Top.getIconHeight());
    for (int x = topLeftWidth; x < width; x += topWidth) {
      Shadow.Top.paintIcon(component, g, x, 0);
    }
    g.setClip(bottomLeftWidth, height - bottomHeight, width - bottomLeftWidth - bottomRightWidth, bottomHeight);
    for (int x = bottomLeftWidth; x < width; x += bottomWidth) {
      Shadow.Bottom.paintIcon(component, g, x, height - bottomHeight);
    }
    g.setClip(0, topLeftHeight, Shadow.Left.getIconWidth(), height - topLeftHeight - bottomLeftHeight);
    for (int y = topLeftHeight; y < height; y += leftHeight) {
      Shadow.Left.paintIcon(component, g, 0, y);
    }
    g.setClip(width - rightWidth, topRightHeight, rightWidth, height - topRightHeight - bottomRightHeight);
    for (int y = topRightHeight; y < height; y += rightHeight) {
      Shadow.Right.paintIcon(component, g, width - rightWidth, y);
    }

    if (clipBounds == null) {
      g.setClip(null);
    }
    else {
      g.setClip(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
    }

    Shadow.Top_left.paintIcon(component, g, 0, 0);
    Shadow.Top_right.paintIcon(component, g, width - topRightWidth, 0);
    Shadow.Bottom_right.paintIcon(component, g, width - bottomRightWidth, height - bottomRightHeight);
    Shadow.Bottom_left.paintIcon(component, g, 0, height - bottomLeftHeight);
  }

  @Override
  public void paintBorder(@NotNull Rectangle bounds, @NotNull Graphics2D g) {
    g.setColor(myFillColor);
    g.fill(new Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height));
    g.setColor(myBorderColor);
    g.draw(new RoundRectangle2D.Double(bounds.x + 0.5, bounds.y + 0.5, bounds.width - 1, bounds.height - 1, 3, 3));
  }
}