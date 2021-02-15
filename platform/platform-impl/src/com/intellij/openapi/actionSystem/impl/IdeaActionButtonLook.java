// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
public class IdeaActionButtonLook extends ActionButtonLook {

  @Override
  public void paintLookBackground(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {
    paintBackground(g, rect, color);
  }

  private static void paintBackground(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {
    Graphics2D g2 = (Graphics2D)g.create();

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.setColor(color);

      float arc = DarculaUIUtil.BUTTON_ARC.getFloat();
      g2.fill(new RoundRectangle2D.Float(rect.x, rect.y, rect.width, rect.height, arc, arc));
    }
    finally {
      g2.dispose();
    }
  }

  @Override
  public void paintLookBorder(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {
    Graphics2D g2 = (Graphics2D)g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

    try {
      g2.setColor(color);

      float arc = DarculaUIUtil.BUTTON_ARC.getFloat();
      float lw = DarculaUIUtil.LW.getFloat();
      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(new RoundRectangle2D.Float(rect.x, rect.y, rect.width, rect.height, arc, arc), false);
      border
        .append(new RoundRectangle2D.Float(rect.x + lw, rect.y + lw, rect.width - lw * 2, rect.height - lw * 2, arc - lw, arc - lw), false);

      g2.fill(border);
    } finally {
      g2.dispose();
    }
  }
}
