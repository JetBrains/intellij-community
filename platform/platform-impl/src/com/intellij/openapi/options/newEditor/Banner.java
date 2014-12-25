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
import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.labels.SwingActionLink;
import com.intellij.ui.components.panels.HorizontalLayout;

import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * @author Sergey.Malenkov
 */
final class Banner extends JPanel {
  private final JPanel myLeftPanel = new JPanel(null);
  private final JLabel myProjectIcon = new JLabel();

  Banner(Action action) {
    super(new BorderLayout(10, 0));
    myLeftPanel.setLayout(new HorizontalLayout(5));
    myProjectIcon.setIcon(AllIcons.General.ProjectConfigurableBanner);
    myProjectIcon.setForeground(JBColor.GRAY);
    myProjectIcon.setVisible(false);
    add(BorderLayout.WEST, myLeftPanel);
    add(BorderLayout.CENTER, myProjectIcon);
    add(BorderLayout.EAST, RelativeFont.BOLD.install(new SwingActionLink(action)));
  }

  void setText(String... names) {
    Component[] components = myLeftPanel.getComponents();
    for (Component component : components) {
      component.setVisible(false);
    }
    if (names != null) {
      int i = 0;
      for (String name : names) {
        if (i < components.length) {
          if (i > 0) {
            components[i - 1].setVisible(true);
          }
          components[i].setVisible(true);
          if (components[i] instanceof JLabel) {
            ((JLabel)components[i]).setText(name);
          }
        }
        else {
          if (i > 0) {
            myLeftPanel.add(RelativeFont.HUGE.install(new JLabel("\u203A")));
          }
          myLeftPanel.add(RelativeFont.BOLD.install(new JLabel(name)));
        }
        i += 2;
      }
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
