/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ui.SelectCvsConfigurationPanel;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;

public class MigrateRootDialog extends DialogWrapper {

  private final JRadioButton myRadioButton1;
  private final JRadioButton myRadioButton2 = new JRadioButton(CvsBundle.message("migrate.replace.all.roots.label"));
  private final TextFieldWithBrowseButton myDirectoryField = new TextFieldWithBrowseButton();
  private final SelectCvsConfigurationPanel myCvsConfigurationPanel;
  private final ListSelectionListener myListener;
  private String myCvsRoot;

  public MigrateRootDialog(Project project, VirtualFile directory) {
    super(project);
    setTitle("Migrate CVS Root");
    final File file = CvsVfsUtil.getFileFor(directory);
    final String root = CvsUtil.loadRootFrom(file);
    myRadioButton1 = new JRadioButton(CvsBundle.message("migrate.replace.if.root.equals.label", root));
    myRadioButton1.setSelected(true);
    final ButtonGroup group = new ButtonGroup();
    group.add(myRadioButton1);
    group.add(myRadioButton2);
    myDirectoryField.setText(directory.getPath());
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        for (VirtualFile vFile : files) {
          final File file = CvsVfsUtil.getFileFor(vFile);
          final String root = CvsUtil.loadRootFrom(file);
          if (root == null) {
            throw new Exception(CvsBundle.message("error.message.directory.is.not.under.cvs"));
          }
        }
      }
    };
    descriptor.setRoots(ProjectRootManager.getInstance(project).getContentRootsFromAllModules());
    myDirectoryField.addBrowseFolderListener("Select directory to migrate to a new CVS root", "", project, descriptor);
    FileChooserFactory.getInstance().installFileCompletion(myDirectoryField.getChildComponent(), descriptor, true, getDisposable());
    myDirectoryField.getTextField().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        enableOKActionConditionally();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        enableOKActionConditionally();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        enableOKActionConditionally();
      }
    });
    myCvsConfigurationPanel = new SelectCvsConfigurationPanel(project);
    if (SystemInfo.isMac) {
      myCvsConfigurationPanel.setBorder(new EmptyBorder(2, 3, 2, 0));
    }
    myListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        enableOKActionConditionally();
      }
    };
    myCvsConfigurationPanel.addListSelectionListener(myListener);
    setOKButtonText("Migrate");
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.insets.bottom = 2;
    constraints.weightx = 1.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.LINE_START;
    final JLabel label1 = new JLabel(CvsBundle.message("migrate.cvs.root.directory.label"));
    label1.setLabelFor(myDirectoryField);
    panel.add(label1, constraints);
    constraints.gridy = 1;
    panel.add(myDirectoryField, constraints);

    constraints.gridy = 2;
    constraints.insets.left = 5;
    panel.add(myRadioButton1,  constraints);
    constraints.gridy = 3;
    panel.add(myRadioButton2, constraints);

    constraints.gridy = 4;
    constraints.insets.top = 8;
    constraints.insets.left = 0;
    final JLabel label2 = new JLabel(CvsBundle.message("migrate.target.root.label"));
    panel.add(label2, constraints);

    final JComponent component = myCvsConfigurationPanel.getPreferredFocusedComponent();
    label2.setLabelFor(component);
    constraints.insets.top = 0;
    constraints.gridy = 5;
    panel.add(myCvsConfigurationPanel, constraints);
    return panel;
  }

  private boolean check() {
    final String text = myDirectoryField.getText();
    final File file = new File(text);
    if (!file.exists() || !file.isDirectory()) {
      return false;
    }
    myCvsRoot = CvsUtil.loadRootFrom(file);
    if (myCvsRoot == null) {
      return false;
    }
    myRadioButton1.setText(CvsBundle.message("migrate.replace.if.root.equals.label", myCvsRoot));
    if (getSelectedCvsConfiguration() == null) {
      return false;
    }
    return true;
  }

  @Override
  protected void dispose() {
    myCvsConfigurationPanel.removeListSelectionListener(myListener);
    super.dispose();
  }

  private void enableOKActionConditionally() {
    setOKActionEnabled(check());
  }

  public String getCvsRoot() {
    return myCvsRoot;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCvsConfigurationPanel;
  }

  public CvsRootConfiguration getSelectedCvsConfiguration() {
    return myCvsConfigurationPanel.getSelectedConfiguration();
  }

  public File getSelectedDirectory() {
    return new File(myDirectoryField.getText());
  }

  public boolean shouldReplaceAllRoots() {
    return myRadioButton2.isSelected();
  }
}
