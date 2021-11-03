// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Alexander Lobas
 */
public class NotificationBalloonRoundShadowBorderProvider extends NotificationBalloonShadowBorderProvider {
  public NotificationBalloonRoundShadowBorderProvider(@NotNull Color fillColor, @NotNull Color borderColor) {
    super(fillColor, borderColor);
  }

  @Override
  public void paintBorder(@NotNull Rectangle bounds, @NotNull Graphics2D g) {
    g.setColor(myFillColor);
    g.fill(new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12));
    g.setColor(myBorderColor);
    g.draw(new RoundRectangle2D.Double(bounds.x + 0.5, bounds.y + 0.5, bounds.width - 1, bounds.height - 1, 12, 12));
  }
}