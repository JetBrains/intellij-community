/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

import static com.intellij.openapi.actionSystem.ActionButtonComponent.*;

public class Win10ActionButtonLook extends ActionButtonLook {
  @Override public void paintBackground(Graphics g, JComponent component, int state) {
    if (state != NORMAL) {
      paintBackground(g, component, getBackgroundColorForState(state));
    }
  }

  @Override
  public void paintBackground(@NotNull Graphics g, @NotNull JComponent component, @NotNull Color color) {
    Rectangle rect = new Rectangle(component.getSize());
    JBInsets.removeFrom(rect, component.getInsets());
    g.setColor(color);
    g.fillRect(rect.x, rect.y, rect.width, rect.height);
  }

  private static Color getBackgroundColorForState(int state) {
    switch (state) {
      case POPPED:
        return Gray.xE8;
      case PUSHED:
      case SELECTED:
        return Gray.xDB;
      default:
        return UIManager.getColor("Button.background");
    }
  }

  @Override public void paintBorder(Graphics g, JComponent component, int state) {
    if (state != NORMAL) {
      Graphics2D g2 = (Graphics2D)g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        g2.setColor(getBorderColorForState(state));

        Rectangle rect = new Rectangle(component.getSize());
        JBInsets.removeFrom(rect, component.getInsets());

        Rectangle innerRect = new Rectangle(rect);
        JBInsets.removeFrom(innerRect, JBUI.insets(1));

        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        border.append(rect, false);
        border.append(innerRect, false);

        g2.fill(border);
      } finally {
        g2.dispose();
      }
    }
  }

  private static Color getBorderColorForState(int state) {
    switch (state) {
      case POPPED:
        return Gray.xCC;
      case PUSHED:
      case SELECTED:
        return Gray.xC4;
      default:
        return UIManager.getColor("Button.intellij.native.borderColor");
    }
  }
}
