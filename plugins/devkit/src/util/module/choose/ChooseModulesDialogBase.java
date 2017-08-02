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
package org.jetbrains.idea.devkit.util.module.choose;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

abstract class ChooseModulesDialogBase extends DialogWrapper {
  private final List<Module> myCandidateModules;
  private final Icon myIcon;
  private final String myMessage;

  protected ChooseModulesDialogBase(Project project, List<Module> candidateModules, String title, String message) {
    super(project, false);
    setTitle(title);

    myCandidateModules = candidateModules;
    myIcon = Messages.getQuestionIcon();
    myMessage = message;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    BorderLayoutPanel panel = JBUI.Panels.simplePanel(15, 10);
    JLabel iconLabel = new JLabel(myIcon);
    panel.addToLeft(JBUI.Panels.simplePanel().addToTop(iconLabel));

    BorderLayoutPanel messagePanel = JBUI.Panels.simplePanel();
    JLabel textLabel = new JLabel(myMessage);
    textLabel.setBorder(JBUI.Borders.emptyBottom(5));
    textLabel.setUI(new MultiLineLabelUI());
    messagePanel.addToTop(textLabel);
    panel.add(messagePanel, BorderLayout.CENTER);

    final JScrollPane jScrollPane = ScrollPaneFactory.createScrollPane();
    jScrollPane.setViewportView(getTable());
    jScrollPane.setPreferredSize(JBUI.size(300, 80));
    panel.addToBottom(jScrollPane);
    return panel;
  }

  protected List<Module> getCandidateModules() {
    return myCandidateModules;
  }

  protected abstract JTable getTable();
}
