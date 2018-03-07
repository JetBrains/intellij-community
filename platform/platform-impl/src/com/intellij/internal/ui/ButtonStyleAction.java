// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings("UseJBColor")
public class ButtonStyleAction extends DumbAwareAction {
  private static final String TEXT_COLOR = "JButton.textColor";
  private static final String FOCUSED_TEXT_COLOR = "JButton.focusedTextColor";
  private static final String BACKGROUND_PROPERTY = "JButton.backgroundColor";
  private static final String FOCUSED_BACKGROUND_PROPERTY = "JButton.focusedBackgroundColor";
  private static final String BORDER_PROPERTY = "JButton.borderColor";
  private static final String FOCUSED_BORDER_PROPERTY = "JButton.focusedBorderColor";

  private static final Color WHITE_BACKGROUND = new JBColor(Color.WHITE, new Color(0x3c3f41));
  private static final Color WHITE_FOREGROUND = new JBColor(Color.WHITE, new Color(0xbbbbbb));
  private static final Color GREEN_BACKGROUND = new JBColor(0x5d9b47, 0x457335);
  private static final Color GREEN_BORDER = new JBColor(0x5d9b47, 0x457335);
  private static final Color GREEN_FOCUSED_BACKGROUND = new Color(0xe1f6da);

  private static final Color BLUE_BACKGROUND = new JBColor(0x1d73bf, 0x134d80);

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      new MyButtonStyleAction(project).show();
    }
  }

  private static class MyButtonStyleAction extends DialogWrapper {
    private MyButtonStyleAction(Project project) {
      super(project);
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new GridBagLayout());
      GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
                                                     JBUI.insets(5), 0, 0);

      JButton button1 = new JButton("Button 1");
      button1.putClientProperty(BACKGROUND_PROPERTY, WHITE_BACKGROUND);
      button1.putClientProperty(FOCUSED_BACKGROUND_PROPERTY, GREEN_FOCUSED_BACKGROUND);
      button1.putClientProperty(BORDER_PROPERTY, GREEN_BORDER);
      button1.putClientProperty(FOCUSED_BORDER_PROPERTY, GREEN_BORDER);
      button1.putClientProperty(FOCUSED_TEXT_COLOR, GREEN_BORDER);
      button1.putClientProperty(TEXT_COLOR, GREEN_BORDER);
      panel.add(button1, gc);

      JButton button2 = new JButton("Button 2");
      //Color fg2 = button2.getForeground();
      //button2.setForeground(new JBColor(() -> button2.hasFocus() ? fg2 : Color.WHITE));
      button2.putClientProperty(TEXT_COLOR, WHITE_FOREGROUND);
      button2.putClientProperty(BACKGROUND_PROPERTY, BLUE_BACKGROUND);
      button2.putClientProperty(BORDER_PROPERTY, BLUE_BACKGROUND);
      gc.gridy++;
      panel.add(button2, gc);

      JButton button3 = new JButton("Button 3");
      //Color fg3 = button3.getForeground();
      //button3.setForeground(new JBColor(() -> button3.hasFocus() ? fg3 : Color.WHITE));
      button3.putClientProperty(TEXT_COLOR, WHITE_FOREGROUND);
      button3.putClientProperty(BACKGROUND_PROPERTY, GREEN_BACKGROUND);
      button3.putClientProperty(FOCUSED_BACKGROUND_PROPERTY, GREEN_FOCUSED_BACKGROUND);
      button3.putClientProperty(BORDER_PROPERTY, GREEN_BORDER);
      button3.putClientProperty(FOCUSED_BORDER_PROPERTY, GREEN_BORDER);
      gc.gridy++;
      panel.add(button3, gc);

      JButton button4 = new JButton("Button 4");
      gc.gridy++;
      panel.add(button4, gc);

      gc.gridx++;
      gc.gridy = 0;
      gc.fill = GridBagConstraints.REMAINDER;
      gc.insets = JBUI.emptyInsets();
      gc.weightx = 1.0;
      panel.add(new JPanel(), gc);

      gc.gridy++;
      panel.add(new JPanel(), gc);

      gc.gridy++;
      panel.add(new JPanel(), gc);

      gc.gridy++;
      panel.add(new JPanel(), gc);

      return panel;
    }
  }
}
