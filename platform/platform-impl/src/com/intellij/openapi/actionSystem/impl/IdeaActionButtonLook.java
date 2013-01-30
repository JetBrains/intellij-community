/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class IdeaActionButtonLook extends ActionButtonLook {
  private static final Color ALPHA_20 = new Color(0, 0, 0, 20);
  private static final Color ALPHA_30 = new Color(0, 0, 0, 30);
  private static final Color ALPHA_40 = new Color(0, 0, 0, 40);
  private static final Color ALPHA_120 = new Color(0, 0, 0, 120);
  private static final BasicStroke BASIC_STROKE = new BasicStroke();

  public void paintBackground(Graphics g, JComponent component, int state) {
    if (state == ActionButtonComponent.NORMAL) return;
    Dimension dimension = component.getSize();

    if (UIUtil.isUnderAquaLookAndFeel()) {
      if (state == ActionButtonComponent.PUSHED) {
        ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, ALPHA_40, dimension.width, dimension.height, ALPHA_20));
        g.fillRect(0, 0, dimension.width - 1, dimension.height - 1);

        g.setColor(ALPHA_120);
        g.drawLine(0, 0, 0, dimension.height - 2);
        g.drawLine(1, 0, dimension.width - 2, 0);

        g.setColor(ALPHA_30);
        g.drawRect(1, 1, dimension.width - 3, dimension.height - 3);
      }
      else if (state == ActionButtonComponent.POPPED) {
        ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, Gray._235, 0, dimension.height, Gray._200));
        g.fillRect(1, 1, dimension.width - 3, dimension.height - 3);
      }
    }
    else {
      final Color bg = UIUtil.getPanelBackground();
      final boolean dark = UIUtil.isUnderDarcula();
      g.setColor(state == ActionButtonComponent.PUSHED ? ColorUtil.shift(bg, dark ? 1d / 0.7d : 0.7d) : dark ? Gray._255.withAlpha(40) : ALPHA_40);
      g.fillRect(1, 1, dimension.width - 2, dimension.height - 2);
    }
  }

  public void paintBorder(Graphics g, JComponent component, int state) {
    if (state == ActionButtonComponent.NORMAL) return;
    Rectangle r = new Rectangle(component.getWidth(), component.getHeight());

    if (UIUtil.isUnderAquaLookAndFeel()) {
      if (state == ActionButtonComponent.POPPED) {
        g.setColor(ALPHA_30);
        g.drawRoundRect(r.x, r.y, r.width - 2, r.height - 2, 4, 4);
      }
    }
    else {
      final double shift = UIUtil.isUnderDarcula() ? 1/0.49 : 0.49;
      g.setColor(ColorUtil.shift(UIUtil.getPanelBackground(), shift));
      ((Graphics2D)g).setStroke(BASIC_STROKE);
      final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      g.drawRoundRect(r.x, r.y, r.width - 2, r.height - 2, 4, 4);
      config.restore();
    }
  }

  public void paintIcon(Graphics g, ActionButtonComponent actionButton, Icon icon) {
    int width = icon.getIconWidth();
    int height = icon.getIconHeight();
    int x = (int)Math.ceil((actionButton.getWidth() - width) / 2);
    int y = (int)Math.ceil((actionButton.getHeight() - height) / 2);
    paintIconAt(g, actionButton, icon, x, y);
  }

  public void paintIconAt(Graphics g, ActionButtonComponent button, Icon icon, int x, int y) {
    icon.paintIcon(null, g, x, y);
  }
}
