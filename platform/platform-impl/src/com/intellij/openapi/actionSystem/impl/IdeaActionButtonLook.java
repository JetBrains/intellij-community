/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public class IdeaActionButtonLook extends ActionButtonLook {
  private static final Color ALPHA_20 = Gray._0.withAlpha(20);
  private static final Color ALPHA_30 = Gray._0.withAlpha(30);
  private static final Color ALPHA_40 = Gray._0.withAlpha(40);
  private static final Color ALPHA_120 = Gray._0.withAlpha(120);
  private static final BasicStroke BASIC_STROKE = new BasicStroke();

  public void paintBackground(Graphics g, JComponent component, int state) {
    if (state != ActionButtonComponent.NORMAL) {
      paintBackground(g, component.getSize(), state);
    }
  }

  protected void paintBackground(Graphics g, Dimension size, int state) {
    if (UIUtil.isUnderAquaLookAndFeel()) {
      if (state == ActionButtonComponent.PUSHED) {
        ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, ALPHA_40, size.width, size.height, ALPHA_20));
        g.fillRect(0, 0, size.width - 1, size.height - 1);

        g.setColor(ALPHA_120);
        g.drawLine(0, 0, 0, size.height - 2);
        g.drawLine(1, 0, size.width - 2, 0);

        g.setColor(ALPHA_30);
        g.drawRect(1, 1, size.width - 3, size.height - 3);
      } else if (state == ActionButtonComponent.POPPED) {
        ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, Gray._235, 0, size.height, Gray._200));
        g.fillRect(1, 1, size.width - 3, size.height - 3);
      }
    } else {
      final Color bg = UIUtil.getPanelBackground();
      final boolean dark = UIUtil.isUnderDarcula();
      g.setColor(state == ActionButtonComponent.PUSHED ? ColorUtil.shift(bg, dark ? 1d / 0.7d : 0.7d) : dark ? Gray._255.withAlpha(40) : ALPHA_40);
      g.fillRect(1, 1, size.width - 2, size.height - 2);
    }
  }

  public void paintBorder(Graphics g, JComponent component, int state) {
    if (state != ActionButtonComponent.NORMAL) {
      paintBorder(g, component.getSize(), state);
    }
  }

  protected void paintBorder(Graphics g, Dimension size, int state) {
    if (UIUtil.isUnderAquaLookAndFeel()) {
      if (state == ActionButtonComponent.POPPED) {
        g.setColor(ALPHA_30);
        g.drawRoundRect(0, 0, size.width - 2, size.height - 2, 4, 4);
      }
    } else {
      final double shift = UIUtil.isUnderDarcula() ? 1/0.49 : 0.49;
      g.setColor(ColorUtil.shift(UIUtil.getPanelBackground(), shift));
      ((Graphics2D)g).setStroke(BASIC_STROKE);
      final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      g.drawRoundRect(0, 0, size.width - 2, size.height - 2, 4, 4);
      config.restore();
    }
  }

  public void paintIcon(Graphics g, ActionButtonComponent actionButton, Icon icon) {
    final int width = icon.getIconWidth();
    final int height = icon.getIconHeight();
    final int x = (int)Math.ceil((actionButton.getWidth() - width) / 2);
    final int y = (int)Math.ceil((actionButton.getHeight() - height) / 2);
    paintIconAt(g, actionButton, icon, x, y);
  }

  public void paintIconAt(Graphics g, ActionButtonComponent button, Icon icon, int x, int y) {
    icon.paintIcon(null, g, x, y);
  }
}
