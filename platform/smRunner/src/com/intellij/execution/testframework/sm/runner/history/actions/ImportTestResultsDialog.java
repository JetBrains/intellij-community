/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner.history.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;

public class ImportTestResultsDialog extends DialogWrapper {
  private final TextFieldWithBrowseButton myFileFieldComp;
  private final JBTextField myHistorySizeField = new JBTextField();
  private final JBList myList;
  private final JRadioButton myFileRb = new JBRadioButton("From File:");
  private final JRadioButton myHistoryRb = new JBRadioButton("From History:");
  
  
  protected ImportTestResultsDialog(@Nullable Project project) {
    super(project, false);
    setTitle("Import Test Results");
    final FileChooserDescriptor xmlDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(StdFileTypes.XML);
    myFileFieldComp = new TextFieldWithBrowseButton();
    myFileFieldComp.addBrowseFolderListener("Choose a File with Tests Result", null, project, xmlDescriptor);

    final DefaultListModel model = new DefaultListModel();
    final String[] historyFiles = ImportTestsAction.TEST_HISTORY_PATH.list();
    if (historyFiles != null) {
      for (String fileName : historyFiles) {
        model.addElement(fileName);
      }
    }
    myList = new JBList(model);
    myList.setSelectedIndex(0);
    
    final ButtonGroup group = new ButtonGroup();
    group.add(myFileRb);
    group.add(myHistoryRb);
    
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final JPanel wholePanel = new JPanel(new BorderLayout());
    
    final JPanel panelWithFileTextField = new JPanel(new BorderLayout());
    panelWithFileTextField.add(myFileRb, BorderLayout.NORTH);
    panelWithFileTextField.add(myFileFieldComp, BorderLayout.CENTER);
    
    final JPanel panelWithHistory = new JPanel(new BorderLayout());
    panelWithHistory.add(myHistoryRb, BorderLayout.NORTH);
    final JPanel historyPanel = new JPanel(new BorderLayout());
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList)
      .disableUpAction()
      .disableDownAction()
      .disableAddAction()
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          ListUtil.removeSelectedItems(myList);
        }
      });
    historyPanel.add(decorator.createPanel(), BorderLayout.CENTER);
    
    myHistorySizeField.setText(PropertiesComponent.getInstance().getValue(ImportTestsAction.TEST_HISTORY_SIZE, "5"));
    final LabeledComponent<JBTextField> sizeComponent = new LabeledComponent<JBTextField>();
    sizeComponent.setText("History size:");
    sizeComponent.setComponent(myHistorySizeField);
    sizeComponent.setLabelLocation(BorderLayout.WEST);
    
    historyPanel.add(sizeComponent, BorderLayout.SOUTH);
    panelWithHistory.add(historyPanel, BorderLayout.CENTER);
    
    wholePanel.add(panelWithFileTextField, BorderLayout.NORTH);
    wholePanel.add(panelWithHistory, BorderLayout.CENTER);

    final ActionListener enableListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateComponents(historyPanel);
      }
    };
    myFileRb.addActionListener(enableListener);
    myHistoryRb.addActionListener(enableListener);
    final boolean hasHistory = !myList.isEmpty();
    myFileRb.setSelected(!hasHistory);
    myHistoryRb.setSelected(hasHistory);
    updateComponents(historyPanel);
    return wholePanel;
  }

  private void updateComponents(JPanel historyPanel) {
    UIUtil.setEnabled(historyPanel, !myFileRb.isSelected(), true);
    UIUtil.setEnabled(myFileFieldComp, !myHistoryRb.isSelected(), true);
  }

  @Override
  protected void doOKAction() {
    PropertiesComponent.getInstance().setValue(ImportTestsAction.TEST_HISTORY_SIZE, myHistorySizeField.getText().trim());
    final String[] historyFiles = ImportTestsAction.TEST_HISTORY_PATH.list();
    final DefaultListModel model = (DefaultListModel)myList.getModel();
    final ArrayList<File> toDelete = new ArrayList<File>();
    for (String fileName : historyFiles) {
      if (!model.contains(fileName)) {
        toDelete.add(new File(ImportTestsAction.TEST_HISTORY_PATH, fileName));
      }
    }
    for (File file : toDelete) {
      FileUtil.delete(file);
    }
    super.doOKAction();
    
  }

  @Nullable
  public String getFilePath() {
    return myFileRb.isSelected() ? myFileFieldComp.getText() 
                                 : FileUtil.toSystemIndependentName(ImportTestsAction.TEST_HISTORY_PATH.getPath()) + "/" + myList.getSelectedValue();
  }
}
