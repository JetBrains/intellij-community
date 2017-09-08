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
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
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
  private static final Color POPPED_BG  = new JBColor(Gray.xE8, new Color(0x464a4d));
  private static final Color PRESSED_BG = new JBColor(Gray.xDB, new Color(0x55595c));

  private static final Color POPPED_BORDER  = new JBColor(Gray.xCC, new Color(0x757b80));
  private static final Color PRESSED_BORDER = new JBColor(Gray.xC4, new Color(0x7a8084));

  public void paintBackground(Graphics g, JComponent component, int state) {
    if (state != ActionButtonComponent.NORMAL) {
      paintBackground(g, component.getSize(), state);
    }
  }

  protected static void paintBackground(Graphics g, Dimension size, int state) {
    Graphics2D g2 = (Graphics2D)g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

    try {
      if (UIUtil.isUnderAquaLookAndFeel() || UIUtil.isUnderDefaultMacTheme()) {
        g2.setColor(state == ActionButtonComponent.PUSHED ? Gray.xD7 : Gray.xE0);
      } else {
        g2.setColor(state == ActionButtonComponent.PUSHED ? PRESSED_BG : POPPED_BG);
      }
      g2.fill(getShape(size));
    } finally {
      g2.dispose();
    }
  }

  public void paintBorder(Graphics g, JComponent component, int state) {
    if (state != ActionButtonComponent.NORMAL) {
      paintBorder(g, component.getSize(), state);
    }
  }

  protected static void paintBorder(Graphics g, Dimension size, int state) {
    Graphics2D g2 = (Graphics2D)g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

    try {
      if (UIUtil.isUnderAquaLookAndFeel() || UIUtil.isUnderDefaultMacTheme()) {
        g2.setColor(state == ActionButtonComponent.PUSHED ? Gray.xB8 : Gray.xCA);
      } else {
        g2.setColor(state == ActionButtonComponent.PUSHED ? PRESSED_BORDER : POPPED_BORDER);
      }
      g2.draw(getShape(size));
    } finally {
      g2.dispose();
    }
  }

  private static Shape getShape(Dimension size) {
    return new RoundRectangle2D.Double(1, 1, size.width - 3, size.height - 3, 4, 4);
  }
}
