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
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ClickListener;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public abstract class TextFieldAction extends AnAction implements CustomComponentAction {
  protected JTextField myField;
  private final String myDescription;
  private final Icon myIcon;

  protected TextFieldAction(String text, String description, Icon icon, final int initSize) {
    super(text, description, icon);
    myDescription = description;
    myIcon = icon;
    myField = new JTextField(initSize);
    myField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          e.consume();
          actionPerformed(null);
        }
      }
    });
  }

  @Override
  public abstract void actionPerformed(@Nullable AnActionEvent e);

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    // honestly borrowed from SearchTextField
    
    final JPanel panel = new JPanel(new BorderLayout());
    final JLabel label = new JLabel(myIcon);
    label.setOpaque(true);
    label.setBackground(myField.getBackground());
    myField.setOpaque(true);
    panel.add(myField, BorderLayout.WEST);
    panel.add(label, BorderLayout.EAST);
    myField.setToolTipText(myDescription);
    label.setToolTipText(myDescription);
    final Border originalBorder;
    if (SystemInfo.isMac) {
      originalBorder = BorderFactory.createLoweredBevelBorder();
    }
    else {
      originalBorder = myField.getBorder();
    }

    panel.setBorder(new CompoundBorder(JBUI.Borders.empty(4, 0, 4, 0), originalBorder));

    myField.setOpaque(true);
    myField.setBorder(JBUI.Borders.empty(0, 5, 0, 5));

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        actionPerformed(null);
        return true;
      }
    }.installOn(label);

    return panel;
  }
}
