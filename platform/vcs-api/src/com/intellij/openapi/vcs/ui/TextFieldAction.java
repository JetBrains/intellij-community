/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.event.*;
import java.awt.*;

public abstract class TextFieldAction extends AnAction implements CustomComponentAction {
  protected JTextField myField;
  private final String myDescription;
  private final Icon myIcon;

  protected TextFieldAction(String text, String description, Icon icon, final int initSize) {
    super(text, description, icon);
    myDescription = description;
    myIcon = icon;
    myField = new JTextField(initSize);
  }

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

    panel.setBorder(new CompoundBorder(IdeBorderFactory.createEmptyBorder(4, 0, 4, 0), originalBorder));

    myField.setOpaque(true);
    myField.setBorder(IdeBorderFactory.createEmptyBorder(0, 5, 0, 5));

    label.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        actionPerformed(null);
      }
    });
    /*myField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        actionPerformed(null);
      }
    });*/
    myField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        reaction(e);
      }
      @Override
      public void keyPressed(KeyEvent e) {
        reaction(e);
      }
    });
    return panel;
  }

  private void reaction(KeyEvent e) {
    if ((KeyEvent.VK_ENTER == e.getKeyCode()) || ('\n' == e.getKeyChar())) {
      e.consume();
      actionPerformed(null);
    }
  }
}
