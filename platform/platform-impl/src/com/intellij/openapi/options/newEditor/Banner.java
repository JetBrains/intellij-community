/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.labels.SwingActionLink;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;

/**
 * @author Sergey.Malenkov
 */
final class Banner extends JPanel {
  private final JPanel myLeftPanel = new JPanel(null);
  private final JLabel myProjectIcon = new JLabel();

  Banner(Action action) {
    super(new BorderLayout(10, 0));
    SwingActionLink link = new SwingActionLink(action);
    myLeftPanel.setLayout(new BoxLayout(myLeftPanel, BoxLayout.X_AXIS));
    myProjectIcon.setIcon(AllIcons.General.ProjectConfigurableBanner);
    myProjectIcon.setForeground(JBColor.GRAY);
    myProjectIcon.setVisible(false);
    add(BorderLayout.WEST, myLeftPanel);
    add(BorderLayout.CENTER, myProjectIcon);
    add(BorderLayout.EAST, link);
    Font font = link.getFont();
    if (font != null) {
      link.setFont(font.deriveFont(Font.BOLD));
    }
  }

  void setText(String... text) {
    Component[] components = myLeftPanel.getComponents();
    int length = text == null ? 0 : text.length;
    for (int i = 0; i < length; i++) {
      if (i < components.length) {
        components[i].setVisible(true);
        if (components[i] instanceof JLabel) {
          ((JLabel)components[i]).setText(text[i]);
        }
      }
      else {
        JLabel label = new JLabel(text[i]);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        if (i > 0) {
          label.setIcon(AllIcons.General.Divider);
        }
        myLeftPanel.add(label);
      }
    }
    while (length < components.length) {
      components[length++].setVisible(false);
    }
  }

  void setProject(Project project) {
    if (project == null) {
      myProjectIcon.setVisible(false);
    }
    else {
      myProjectIcon.setVisible(true);
      myProjectIcon.setText(OptionsBundle.message(project.isDefault()
                                                  ? "configurable.default.project.tooltip"
                                                  : "configurable.current.project.tooltip"));
    }
  }
}
