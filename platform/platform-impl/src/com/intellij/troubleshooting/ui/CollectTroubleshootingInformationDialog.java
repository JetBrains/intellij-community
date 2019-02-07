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


import com.intellij.ide.troubleshooting.CompositeGeneralTroubleInfoCollector;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.settingsSummary.ProblemType;
import com.intellij.troubleshooting.ProblemTypeAdapter;
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

  @SuppressWarnings("deprecation")
  public CollectTroubleshootingInformationDialog(@NotNull Project project) {
    super(project);
    setTitle("Collect Troubleshooting Information");
    CompositeGeneralTroubleInfoCollector generalInfoCollector = new CompositeGeneralTroubleInfoCollector();
    troubleTypeBox.addItem(generalInfoCollector);
    TroubleInfoCollector[] extensions = TroubleInfoCollector.EP_SETTINGS.getExtensions();
    for(TroubleInfoCollector troubleInfoCollector : extensions){
      troubleTypeBox.addItem(troubleInfoCollector);
    }
    ProblemType[] legacyExtensions = ProblemType.EP_SETTINGS.getExtensions();
    for(ProblemType problemType : legacyExtensions){
      troubleTypeBox.addItem(new ProblemTypeAdapter(problemType));
    }
    troubleTypeBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        TroubleInfoCollector item = (TroubleInfoCollector)e.getItem();
        summary.setText(item.collectInfo(project));
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
    centerPanel.setMinimumSize(new Dimension(500, 600));
    return centerPanel;
  }
}
