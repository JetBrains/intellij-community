// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.getDefaultShelfPath;
import static com.intellij.openapi.vcs.configurable.ShelfProjectConfigurable.getDefaultShelfPresentationPath;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.ui.UIUtil.*;

@ApiStatus.Internal
public class ShelfStorageConfigurationDialog extends DialogWrapper {
  @NotNull private final Project myProject;
  @NotNull private final VcsConfiguration myVcsConfiguration;
  @NotNull private final JBRadioButton myUseCustomShelfDirectory;
  @NotNull private final JBRadioButton myUseDefaultShelfDirectory;
  @NotNull private final TextFieldWithBrowseButton myShelfDirectoryPath;
  @NotNull private final JBCheckBox myMoveShelvesCheckBox;


  protected ShelfStorageConfigurationDialog(@NotNull Project project) {
    super(project);
    setTitle(VcsBundle.message("change.shelves.location.dialog.title"));
    myProject = project;
    myVcsConfiguration = VcsConfiguration.getInstance(project);
    myUseCustomShelfDirectory = new JBRadioButton(VcsBundle.message("change.shelves.location.dialog.custom.label"));
    if (isUnderWin10LookAndFeel()) {
      myUseCustomShelfDirectory.setBorder(JBUI.Borders.emptyRight(DEFAULT_HGAP));
    }
    myUseDefaultShelfDirectory = new JBRadioButton(VcsBundle.message("change.shelves.location.dialog.default.label"), true);
    myShelfDirectoryPath = new TextFieldWithBrowseButton();
    myShelfDirectoryPath.addBrowseFolderListener(myProject, FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(VcsBundle.message("shelf.tab"))
      .withDescription(VcsBundle.message("change.shelves.location.dialog.location.browser.title")));
    myMoveShelvesCheckBox = new JBCheckBox(VcsBundle.message("vcs.shelf.move.text"));
    setOKButtonText(VcsBundle.message("change.shelves.location.dialog.action.button"));
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
    JBLabel label = new JBLabel(VcsBundle.message("change.shelves.location.dialog.group.title"));
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
        .showBalloonForComponent(myShelfDirectoryPath,
                                 VcsBundle.message("configurable.shelf.storage.cant.find.or.create.new.shelf.directory"), MessageType.WARNING, false, myProject);
      return;
    }
    myVcsConfiguration.USE_CUSTOM_SHELF_PATH = nowCustom;
    myVcsConfiguration.CUSTOM_SHELF_PATH = customPath;
    myVcsConfiguration.MOVE_SHELVES = myMoveShelvesCheckBox.isSelected();
    Path fromFile = wasCustom ? Paths.get(prevPath) : getDefaultShelfPath(myProject);
    Path toFile = nowCustom ? Paths.get(customPath) : getDefaultShelfPath(myProject);

    if (!FileUtil.pathsEqual(fromFile.toString(), toFile.toString())) {
      myProject.save();
      if (wasCustom) {
        ApplicationManager.getApplication().saveSettings();
      }
      ShelveChangesManager.getInstance(myProject).checkAndMigrateUnderProgress(fromFile.toFile(), toFile.toFile(), wasCustom);
    }
    super.doOKAction();
  }

  private static boolean checkAndIgnoreIfCreated(@NotNull String newPath) {
    File newDir = new File(newPath);
    if (newDir.exists()) return true;
    if (!newDir.mkdirs()) return false;
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
        validationError = VcsBundle.message("configurable.shelf.storage.destination.shelf.directory.should.have.read.access");
      }
      if (!toFile.canWrite()) {
        validationError = VcsBundle.message("configurable.shelf.storage.destination.shelf.directory.should.have.write.access");
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
