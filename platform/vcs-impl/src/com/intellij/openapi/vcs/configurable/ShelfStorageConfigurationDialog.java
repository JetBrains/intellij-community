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
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.getDefaultShelfPath;
import static com.intellij.openapi.vcs.configurable.ShelfProjectConfigurationPanel.getDefaultShelfPresentationPath;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.ui.UIUtil.*;

public class ShelfStorageConfigurationDialog extends DialogWrapper {
  @NotNull private final Project myProject;
  @NotNull private final VcsConfiguration myVcsConfiguration;
  @NotNull private final JBRadioButton myUseCustomShelfDirectory;
  @NotNull private final JBRadioButton myUseDefaultShelfDirectory;
  @NotNull private final TextFieldWithBrowseButton myShelfDirectoryPath;
  @NotNull private final JBCheckBox myMoveShelvesCheckBox;


  protected ShelfStorageConfigurationDialog(@NotNull Project project) {
    super(project);
    setTitle("Change Shelves Location");
    myProject = project;
    myVcsConfiguration = VcsConfiguration.getInstance(project);
    myUseCustomShelfDirectory = new JBRadioButton("Custom directory:");
    if (isUnderWin10LookAndFeel()) {
      myUseCustomShelfDirectory.setBorder(JBUI.Borders.emptyRight(DEFAULT_HGAP));
    }
    myUseDefaultShelfDirectory = new JBRadioButton("Default directory:", true);
    myShelfDirectoryPath = new TextFieldWithBrowseButton();
    myShelfDirectoryPath.addBrowseFolderListener("Shelf", "Select a directory to store shelves in", myProject,
                                                 FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myMoveShelvesCheckBox = new JBCheckBox(VcsBundle.message("vcs.shelf.move.text"));
    setOKButtonText("_Change Location");
    initComponents();
    updateOkAction();
    getOKAction().putValue(DEFAULT_ACTION, null);
    getCancelAction().putValue(DEFAULT_ACTION, Boolean.TRUE);
    init();
    initValidation();
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  private void initComponents() {
    ButtonGroup bg = new ButtonGroup();
    bg.add(myUseCustomShelfDirectory);
    bg.add(myUseDefaultShelfDirectory);
    myUseCustomShelfDirectory.setMnemonic('U');
    myMoveShelvesCheckBox.setMnemonic('M');
    myUseCustomShelfDirectory.setSelected(myVcsConfiguration.USE_CUSTOM_SHELF_PATH);
    myMoveShelvesCheckBox.setSelected(myVcsConfiguration.MOVE_SHELVES);
    myShelfDirectoryPath
      .setText(
        FileUtil.toSystemDependentName(chooseNotNull(myVcsConfiguration.CUSTOM_SHELF_PATH, getDefaultShelfPresentationPath(myProject))));
    setEnabledCustomShelfDirectoryComponents(myUseCustomShelfDirectory.isSelected());
    myUseCustomShelfDirectory.addChangeListener(e -> setEnabledCustomShelfDirectoryComponents(myUseCustomShelfDirectory.isSelected()));
  }

  private void setEnabledCustomShelfDirectoryComponents(boolean enabled) {
    myShelfDirectoryPath.setEnabled(enabled);
    myShelfDirectoryPath.setEditable(enabled);
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    JPanel contentPanel = new JPanel(new BorderLayout(DEFAULT_HGAP, DEFAULT_VGAP));
    JBLabel label = new JBLabel("Store shelves in:");
    contentPanel.add(label, BorderLayout.NORTH);
    JPanel buttonPanel = new JPanel(new BorderLayout(DEFAULT_HGAP, DEFAULT_VGAP));
    buttonPanel.setBorder(JBUI.Borders.emptyLeft(20));
    buttonPanel.add(createCustomShelveLocationPanel(), BorderLayout.NORTH);
    buttonPanel.add(createDefaultLocationPanel(), BorderLayout.SOUTH);
    contentPanel.add(buttonPanel, BorderLayout.CENTER);
    myMoveShelvesCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    myMoveShelvesCheckBox.setBorder(null);
    contentPanel.add(myMoveShelvesCheckBox, BorderLayout.SOUTH);
    return contentPanel;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @NotNull
  private JPanel createCustomShelveLocationPanel() {
    JPanel customPanel = new JPanel(new BorderLayout());
    customPanel.add(myUseCustomShelfDirectory, BorderLayout.WEST);
    customPanel.add(myShelfDirectoryPath, BorderLayout.CENTER);
    return customPanel;
  }

  @NotNull
  private JPanel createDefaultLocationPanel() {
    JPanel defaultPanel = new JPanel(new BorderLayout());
    defaultPanel.add(myUseDefaultShelfDirectory, BorderLayout.WEST);
    JLabel infoLabel = new JLabel(getDefaultShelfPresentationPath(myProject));
    infoLabel.setBorder(null);
    infoLabel.setForeground(JBColor.GRAY);
    defaultPanel.add(infoLabel, BorderLayout.CENTER);
    return defaultPanel;
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "reference.dialogs.vcs.shelf.settings";
  }

  @Override
  protected void doOKAction() {
    boolean wasCustom = myVcsConfiguration.USE_CUSTOM_SHELF_PATH;
    String prevPath = myVcsConfiguration.CUSTOM_SHELF_PATH;
    String customPath = FileUtil.toSystemIndependentName(myShelfDirectoryPath.getText());
    boolean nowCustom = myUseCustomShelfDirectory.isSelected();
    if (nowCustom && !checkAndIgnoreIfCreated(customPath)) {
      PopupUtil
        .showBalloonForComponent(myShelfDirectoryPath, "Can't find or create new shelf directory", MessageType.WARNING, false, myProject);
      return;
    }
    myVcsConfiguration.USE_CUSTOM_SHELF_PATH = nowCustom;
    myVcsConfiguration.CUSTOM_SHELF_PATH = customPath;
    myVcsConfiguration.MOVE_SHELVES = myMoveShelvesCheckBox.isSelected();
    File fromFile = new File(wasCustom ? prevPath : getDefaultShelfPath(myProject));
    File toFile = new File(nowCustom ? customPath : getDefaultShelfPath(myProject));

    if (!FileUtil.filesEqual(fromFile, toFile)) {
      myProject.save();
      if (wasCustom) {
        ApplicationManager.getApplication().saveSettings();
      }
      ShelveChangesManager.getInstance(myProject).checkAndMigrateUnderProgress(fromFile, toFile, wasCustom);
    }
    super.doOKAction();
  }

  private boolean checkAndIgnoreIfCreated(@NotNull String newPath) {
    File newDir = new File(newPath);
    if (newDir.exists()) return true;
    if (!newDir.mkdirs()) return false;
    // new directory was successfully created -> should be ignored if under project
    String basePath = myProject.getBasePath();
    if (basePath != null && FileUtil.isAncestor(basePath, newPath, true)) {
      ChangeListManager.getInstance(myProject).addDirectoryToIgnoreImplicitly(newDir.getAbsolutePath());
    }
    return true;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    updateOkAction();
    if (myUseCustomShelfDirectory.isSelected()) {
      File toFile = new File(myShelfDirectoryPath.getText());
      if (!toFile.exists()) return null;   // check that file can be created after OK button pressed;
      String validationError = null;
      if (!toFile.canRead()) {
        validationError = "Destination shelf directory should have read access";
      }
      if (!toFile.canWrite()) {
        validationError = "Destination shelf directory should have write access";
      }
      if (validationError != null) return new ValidationInfo(validationError, myShelfDirectoryPath);
    }
    return super.doValidate();
  }

  private void updateOkAction() {
    setOKActionEnabled(isModified());
  }

  private boolean isModified() {
    if (myVcsConfiguration.USE_CUSTOM_SHELF_PATH != myUseCustomShelfDirectory.isSelected()) return true;
    return myUseCustomShelfDirectory.isSelected() &&
           !StringUtil.equals(myVcsConfiguration.CUSTOM_SHELF_PATH, myShelfDirectoryPath.getText());
  }
}
