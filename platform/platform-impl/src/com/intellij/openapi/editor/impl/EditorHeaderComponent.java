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
package com.intellij.openapi.editor.impl;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author gregsh
 */
public class EditorHeaderComponent extends JPanel {

  public EditorHeaderComponent() {
    super(new BorderLayout(0, 0));
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Color GRADIENT_C1 = new JBColor(getBackground(), JBColor.background());
    Color GRADIENT_C2 = new JBColor(new Color(Math.max(0, GRADIENT_C1.getRed() - 0x18), Math.max(0, GRADIENT_C1.getGreen() - 0x18),
                                              Math.max(0, GRADIENT_C1.getBlue() - 0x18)), Gray._75);

    final Graphics2D g2d = (Graphics2D)g;

    if (!UIUtil.isUnderGTKLookAndFeel()) {
      g2d.setPaint(UIUtil.getGradientPaint(0, 0, GRADIENT_C1, 0, getHeight(), GRADIENT_C2));
      g2d.fillRect(1, 1, getWidth(), getHeight() - 1);
      g2d.setPaint(null);
    }

    g.setColor(UIUtil.getBorderColor());
    g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
  }
}
