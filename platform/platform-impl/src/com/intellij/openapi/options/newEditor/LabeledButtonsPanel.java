// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
