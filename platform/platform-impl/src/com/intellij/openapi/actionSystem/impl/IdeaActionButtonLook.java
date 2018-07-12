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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public class IdeaActionButtonLook extends ActionButtonLook {
  private static final Color POPPED_BG  = new JBColor(Gray.xE8, new Color(0x464a4d));
  private static final Color PRESSED_BG = new JBColor(Gray.xDB, new Color(0x55595c));

  private static final Color POPPED_BORDER  = new JBColor(Gray.xCC, new Color(0x757b80));
  private static final Color PRESSED_BORDER = new JBColor(Gray.xC4, new Color(0x7a8084));

  public void paintBackground(Graphics g, JComponent component, int state) {
    if (state != ActionButtonComponent.NORMAL) {
      paintBackground(g, component, getColorForState(state));
    }
  }

  @Override
  public void paintBackground(@NotNull Graphics g, @NotNull JComponent component, @NotNull Color color) {
    Rectangle rect = new Rectangle(component.getSize());
    JBInsets.removeFrom(rect, component.getInsets());
    paintBackgroundWithColor(g, rect, color);
  }

  protected static void paintBackground(@NotNull Graphics g, @NotNull Rectangle rect, int state) {
    paintBackgroundWithColor(g, rect, getColorForState(state));
  }

  private static Color getColorForState(int state) {
    Color color;
    if (UIUtil.isUnderAquaLookAndFeel() || UIUtil.isUnderDefaultMacTheme()) {
      color = state == ActionButtonComponent.PUSHED ? Gray.xD7 : Gray.xE0;
    } else {
      color = state == ActionButtonComponent.PUSHED ? PRESSED_BG : POPPED_BG;
    }
    return color;
  }

  protected static void paintBackgroundWithColor(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {
    Graphics2D g2 = (Graphics2D)g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
    g2.translate(rect.x, rect.y);

    try {
      g2.setColor(color);

      float arc = DarculaUIUtil.BUTTON_ARC.getFloat();
      g2.fill(new RoundRectangle2D.Float(0, 0, rect.width, rect.height, arc, arc));
    } finally {
      g2.dispose();
    }
  }

  public void paintBorder(Graphics g, JComponent component, int state) {
    if (state != ActionButtonComponent.NORMAL) {
      Rectangle rect = new Rectangle(component.getSize());
      JBInsets.removeFrom(rect, component.getInsets());
      paintBorder(g, rect, state);
    }
  }

  protected static void paintBorder(Graphics g, Rectangle rect, int state) {
    Graphics2D g2 = (Graphics2D)g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
    g2.translate(rect.x, rect.y);

    try {
      if (UIUtil.isUnderAquaLookAndFeel() || UIUtil.isUnderDefaultMacTheme()) {
        g2.setColor(state == ActionButtonComponent.PUSHED ? Gray.xB8 : Gray.xCA);
      } else {
        g2.setColor(state == ActionButtonComponent.PUSHED ? PRESSED_BORDER : POPPED_BORDER);
      }

      float arc = DarculaUIUtil.BUTTON_ARC.getFloat();
      float lw = DarculaUIUtil.LW.getFloat();
      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(new RoundRectangle2D.Float(0, 0, rect.width, rect.height, arc, arc), false);
      border.append(new RoundRectangle2D.Float(lw, lw, rect.width - lw*2, rect.height- lw*2, arc - lw, arc - lw), false);

      g2.fill(border);
    } finally {
      g2.dispose();
    }
  }
}
