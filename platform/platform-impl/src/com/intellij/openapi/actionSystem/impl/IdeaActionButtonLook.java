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

import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public class IdeaActionButtonLook extends ActionButtonLook {
  private static final Color ALPHA_20 = Gray._0.withAlpha(20);
  private static final Color ALPHA_30 = Gray._0.withAlpha(30);
  private static final Color ALPHA_40 = Gray._0.withAlpha(40);
  private static final BasicStroke BASIC_STROKE = new BasicStroke();

  public void paintBackground(Graphics g, JComponent component, int state) {
    if (state != ActionButtonComponent.NORMAL) {
      Component opaque = UIUtil.findNearestOpaque(component);
      Color bg = opaque != null? opaque.getBackground() : null;

      paintBackground(g, component.getSize(), bg, state);
    }
  }

  protected static void paintBackground(Graphics g, Dimension size, Color background, int state) {
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    try {
      Color bg = background == null ? JBColor.background() : background;
      if (UIUtil.isUnderAquaLookAndFeel() || (SystemInfo.isMac && UIUtil.isUnderIntelliJLaF())) {
        if (state == ActionButtonComponent.PUSHED) {
          if (UIUtil.isUnderAquaLookAndFeel()) {
            ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, ALPHA_40, size.width, size.height, ALPHA_20));
            ((Graphics2D)g).fill(getShape(size));
            g.setColor(ALPHA_30);
            ((Graphics2D)g).draw(getShape(size));
          } else {
            g.setColor(ColorUtil.darker(bg, 1));
            ((Graphics2D)g).fill(getShape(size));
            g.setColor(Gray.xC0);
            ((Graphics2D)g).draw(getShape(size));
          }
        }
        else if (state == ActionButtonComponent.POPPED || state == ActionButtonComponent.SELECTED) {
          if (UIUtil.isUnderAquaLookAndFeel()) {
            ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, bg, 0, size.height, ColorUtil.darker(bg, 2)));
            ((Graphics2D)g).fill(getShape(size));
          } else {
            ((Graphics2D)g).setPaint(ColorUtil.darker(bg, 1));
            ((Graphics2D)g).fill(getShape(size));
            g.setColor(Gray.xCC);
            ((Graphics2D)g).draw(getShape(size));
          }
        }
      }
      else {
        boolean dark = UIUtil.isUnderDarcula();
        Color pushed = dark ? ColorUtil.shift(bg,  1.428D) : Gray.xD0;
        Color dark_normal = Gray._255.withAlpha(40);
        g.setColor(state == ActionButtonComponent.PUSHED ? pushed : dark ? dark_normal : Gray.xD9);
        ((Graphics2D)g).fill(getShape(size));
      }
    }
    finally {
      config.restore();
    }
  }

  public void paintBorder(Graphics g, JComponent component, int state) {
    if (state != ActionButtonComponent.NORMAL) {
      paintBorder(g, component.getSize(), state);
    }
  }

  protected static void paintBorder(Graphics g, Dimension size, int state) {
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    try {
      if (UIUtil.isUnderAquaLookAndFeel()) {
        if (state == ActionButtonComponent.POPPED) {
          g.setColor(JBColor.GRAY);
          ((Graphics2D)g).draw(getShape(size));
        }
      } else if (!UIUtil.isUnderDefaultMacTheme()) {
        Color color = UIUtil.isUnderDarcula() ? ColorUtil.shift(UIUtil.getPanelBackground(), 2.04D) : Gray.xA1;
        g.setColor(color);
        ((Graphics2D)g).setStroke(BASIC_STROKE);
        ((Graphics2D)g).draw(getShape(size));
      }
    }
    finally {
      config.restore();
    }
  }

  private static Shape getShape(Dimension size) {
    return new RoundRectangle2D.Double(1, 1, size.width - 3, size.height - 3, 4, 4);
  }
}
