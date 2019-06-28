// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ClickListener;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public abstract class TextFieldAction extends AnAction implements CustomComponentAction, DumbAware {
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
          perform();
        }
      }
    });
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    perform();
  }

  public void perform() {}

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
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
        perform();
        return true;
      }
    }.installOn(label);

    return panel;
  }
}
