// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.ui.JBValue;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Alexander Lobas
 */
public class NotificationBalloonRoundShadowBorderProvider extends NotificationBalloonShadowBorderProvider {
  public static final JBValue CORNER_RADIUS = new JBValue.UIInteger("Notification.arc", 12);

  public NotificationBalloonRoundShadowBorderProvider(@NotNull Color fillColor, @NotNull Color borderColor) {
    super(fillColor, borderColor);
  }

  @Override
  public void paintBorder(@NotNull Rectangle bounds, @NotNull Graphics2D g) {
    int cornerRadius = CORNER_RADIUS.get();
    g.setColor(myFillColor);
    g.fill(new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, cornerRadius, cornerRadius));
    g.setColor(myBorderColor);
    g.draw(new RoundRectangle2D.Double(bounds.x + 0.5, bounds.y + 0.5, bounds.width - 1, bounds.height - 1, cornerRadius, cornerRadius));
  }
}