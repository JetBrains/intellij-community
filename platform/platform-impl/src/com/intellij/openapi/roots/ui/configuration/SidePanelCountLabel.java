/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class SidePanelCountLabel extends JLabel {
  private boolean mySelected;

  public SidePanelCountLabel() {
    super();
    setBorder(new Border() {
      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      }

      @Override
      public Insets getBorderInsets(Component c) {
        return StringUtil.isEmpty(getText()) ? new Insets(0, 0, 0, 0) : new Insets(2, 5, 2, 6 + 6);
      }

      @Override
      public boolean isBorderOpaque() {
        return false;
      }
    });
    setFont(UIUtil.getListFont().deriveFont(Font.BOLD));
  }

  public boolean isSelected() {
    return mySelected;
  }

  public void setSelected(boolean selected) {
    mySelected = selected;
  }

  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(isSelected() ? UIUtil.getListSelectionBackground() : UIUtil.SIDE_PANEL_BACKGROUND);
    g.fillRect(0, 0, getWidth(), getHeight());
    if (StringUtil.isEmpty(getText())) return;
    final JBColor deepBlue = new JBColor(new Color(0x97A4B2), new Color(92, 98, 113));
    g.setColor(isSelected() ? Gray._255.withAlpha(UIUtil.isUnderDarcula() ? 100 : 220) : deepBlue);
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    g.fillRoundRect(0, 3, getWidth() - 6 - 1, getHeight() - 6, getHeight() - 6, getHeight() - 6);
    config.restore();
    setForeground(isSelected() ? deepBlue.darker() : UIUtil.getListForeground(true));

    super.paintComponent(g);
  }
}
