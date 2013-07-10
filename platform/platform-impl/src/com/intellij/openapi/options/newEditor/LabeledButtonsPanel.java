/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
class LabeledButtonsPanel extends JPanel {
  private final JPanel myButtonsPanel = new JPanel();
  LabeledButtonsPanel(String label) {
    super(new BorderLayout());
    final JLabel text = new JLabel(label);
    final Font font = UIUtil.getLabelFont();
    if (SystemInfo.isMac) {
      text.setFont(new Font("Lucida Grande", Font.BOLD, 12));
    } else {
      text.setFont(font.deriveFont(Font.BOLD).deriveFont(font.getSize() + 2f));
    }
    text.setBorder(new EmptyBorder(2, 10, 8, 0));
    add(text, BorderLayout.NORTH);
    myButtonsPanel.setLayout(new BoxLayout(myButtonsPanel, BoxLayout.X_AXIS));
    myButtonsPanel.setOpaque(false);
    add(myButtonsPanel, BorderLayout.CENTER);
  }

  @Override
  public Component add(Component comp) {
    return myButtonsPanel.add(comp);
  }

  public void addButton(PreferenceButton button) {
    add(button);
  }
}
