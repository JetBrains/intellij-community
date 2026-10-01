// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.util.ui.JBValue;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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

  private void paintBackground(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {
    PaintUtil.useCopy(g, g2 -> {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.setColor(color);

      float arc = getButtonArc().getFloat();
      g2.fill(new RoundRectangle2D.Float(rect.x, rect.y, rect.width, rect.height, arc, arc));
    });
  }

  protected @NotNull JBValue getButtonArc() {
    return DarculaUIUtil.BUTTON_ARC;
  }

  @Override
  public void paintLookBorder(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {
    DarculaNewUIUtil.INSTANCE.drawRoundedRectangle(g, rect, color, getButtonArc().getFloat(), DarculaUIUtil.LW.getFloat());
  }
}
