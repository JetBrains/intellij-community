/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.settingsSummary.ui;


import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.settingsSummary.ProblemType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class SettingsSummaryDialog extends DialogWrapper {
  private JTextArea summary;
  private JPanel centerPanel;
  private ComboBox<ProblemType> problemTypeBox;

  public static final ExtensionPointName<ProblemType> EP_SETTINGS = ExtensionPointName.create("com.intellij.settingsSummaryFactory");

  public SettingsSummaryDialog(Project project) {
    super(project);
    setTitle("Settings Summary");
    for(ProblemType problemType: EP_SETTINGS.getExtensions()){
      problemTypeBox.addItem(problemType);
    }
    problemTypeBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        ProblemType item = (ProblemType)e.getItem();
        summary.setText(item.collectInfo(project));
      }
    });
    summary.setText(EP_SETTINGS.getExtensions()[0].collectInfo(project));

    init();
    pack();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    Action copy = new DialogWrapperAction("&Copy") {
      @Override
      protected void doAction(ActionEvent e) {
        CopyPasteManager.getInstance().setContents(new StringSelection(summary.getText()));
      }
    };
    return new Action[]{copy, getOKAction()};
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return centerPanel;
  }
}
