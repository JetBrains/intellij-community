// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.getDefaultShelfPath;

@ApiStatus.Internal
public class ShelfStorageConfigurationDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(ShelfStorageConfigurationDialog.class);

  private final @NotNull Project myProject;
  private final @NotNull VcsConfiguration myVcsConfiguration;
  private final @NotNull ShelfStorageConfigurationUi ui;


  protected ShelfStorageConfigurationDialog(@NotNull Project project) {
    super(project);
    setTitle(VcsBundle.message("change.shelves.location.dialog.title"));
    myProject = project;
    myVcsConfiguration = VcsConfiguration.getInstance(project);
    ui = new ShelfStorageConfigurationUi(myProject, myVcsConfiguration);
    setOKButtonText(VcsBundle.message("change.shelves.location.dialog.action.button"));
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

  @Override
  protected @Nullable JComponent createNorthPanel() {
    return ui.panel;
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected @Nullable String getHelpId() {
    return "reference.dialogs.vcs.shelf.settings";
  }

  @Override
  protected void doOKAction() {
    boolean wasCustom = myVcsConfiguration.USE_CUSTOM_SHELF_PATH;
    String prevPath = myVcsConfiguration.CUSTOM_SHELF_PATH;
    String customPath = FileUtil.toSystemIndependentName(ui.shelfDirectoryPath.getText());
    boolean nowCustom = ui.useCustomShelfDirectory.isSelected();
    if (nowCustom && !checkAndIgnoreIfCreated(customPath)) {
      PopupUtil
        .showBalloonForComponent(ui.shelfDirectoryPath,
                                 VcsBundle.message("configurable.shelf.storage.cant.find.or.create.new.shelf.directory"),
                                 MessageType.WARNING, false, myProject);
      return;
    }
    myVcsConfiguration.USE_CUSTOM_SHELF_PATH = nowCustom;
    myVcsConfiguration.CUSTOM_SHELF_PATH = customPath;
    myVcsConfiguration.MOVE_SHELVES = ui.moveShelvesCheckBox.isSelected();
    Path fromFile = wasCustom ? Paths.get(prevPath) : getDefaultShelfPath(myProject);
    Path toFile = nowCustom ? Paths.get(customPath) : getDefaultShelfPath(myProject);

    if (!FileUtil.pathsEqual(fromFile.toString(), toFile.toString())) {
      LOG.info(String.format("Migrating shelve location from '%s' to '%s'", fromFile, toFile));

      myProject.save();
      if (wasCustom) {
        ApplicationManager.getApplication().saveSettings();
      }
      ShelveChangesManager.getInstance(myProject).checkAndMigrateUnderProgress(fromFile, toFile, wasCustom);
    }
    super.doOKAction();
  }

  private static boolean checkAndIgnoreIfCreated(@NotNull String newPath) {
    Path newDir = Paths.get(newPath);
    if (Files.exists(newDir)) return true;
    try {
      Files.createDirectories(newDir);
      return true;
    }
    catch (IOException e) {
      return false;
    }
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    updateOkAction();
    if (ui.useCustomShelfDirectory.isSelected()) {
      Path toFile = Paths.get(ui.shelfDirectoryPath.getText());
      if (!Files.exists(toFile)) return null;   // check that file can be created after OK button pressed;
      String validationError = null;
      if (!Files.isReadable(toFile)) {
        validationError = VcsBundle.message("configurable.shelf.storage.destination.shelf.directory.should.have.read.access");
      }
      if (!Files.isWritable(toFile)) {
        validationError = VcsBundle.message("configurable.shelf.storage.destination.shelf.directory.should.have.write.access");
      }
      if (validationError != null) return new ValidationInfo(validationError, ui.shelfDirectoryPath);
    }
    return super.doValidate();
  }

  private void updateOkAction() {
    setOKActionEnabled(isModified());
  }

  private boolean isModified() {
    if (myVcsConfiguration.USE_CUSTOM_SHELF_PATH != ui.useCustomShelfDirectory.isSelected()) return true;
    return ui.useCustomShelfDirectory.isSelected() &&
           !StringUtil.equals(myVcsConfiguration.CUSTOM_SHELF_PATH, ui.shelfDirectoryPath.getText());
  }
}
