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
package com.intellij.troubleshooting.ui;


import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.troubleshooting.CompositeGeneralTroubleInfoCollector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.troubleshooting.TroubleInfoCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class CollectTroubleshootingInformationDialog extends DialogWrapper {
  private JTextArea summary;
  private JPanel centerPanel;
  private ComboBox<TroubleInfoCollector> troubleTypeBox;

  public CollectTroubleshootingInformationDialog(@NotNull Project project) {
    super(project);
    setTitle(IdeBundle.message("dialog.title.collect.troubleshooting.information"));
    CompositeGeneralTroubleInfoCollector generalInfoCollector = new CompositeGeneralTroubleInfoCollector();
    troubleTypeBox.addItem(generalInfoCollector);
    TroubleInfoCollector[] extensions = TroubleInfoCollector.EP_SETTINGS.getExtensions();
    for (TroubleInfoCollector troubleInfoCollector : extensions){
      troubleTypeBox.addItem(troubleInfoCollector);
    }
    troubleTypeBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        summary.setText(CommonBundle.getLoadingTreeNodeText());
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          TroubleInfoCollector item = (TroubleInfoCollector)e.getItem();
          String collectedInfo = item.collectInfo(project);
          if (e.getItem() == troubleTypeBox.getSelectedItem()) {
            summary.setText(collectedInfo);
          }
        });
      }
    });
    summary.setText(generalInfoCollector.collectInfo(project));
    summary.setLineWrap(true);

    init();
    pack();
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  protected Action @NotNull [] createActions() {
    Action copy = new DialogWrapperAction(IdeBundle.message("action.text.copy")) {
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
    centerPanel.setMinimumSize(new Dimension(500, 600));
    return centerPanel;
  }
}
