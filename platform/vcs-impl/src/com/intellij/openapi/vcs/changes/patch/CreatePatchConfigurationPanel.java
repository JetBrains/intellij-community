// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.patch;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;

public class CreatePatchConfigurationPanel {
  private static final int TEXT_FIELD_WIDTH = 70;

  private JPanel myMainPanel;
  private TextFieldWithBrowseButton myFileNameField;
  private TextFieldWithBrowseButton myBasePathField;
  private JCheckBox myReversePatchCheckbox;
  private ComboBox<Charset> myEncoding;
  private JLabel myWarningLabel;
  private final Project myProject;
  @Nullable private File myCommonParentDir;
  private JBRadioButton myToClipboardButton;
  private JBRadioButton myToFileButton;

  public CreatePatchConfigurationPanel(@NotNull final Project project) {
    myProject = project;
    initMainPanel();

    myFileNameField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final FileSaverDialog dialog =
          FileChooserFactory.getInstance().createSaveFileDialog(
            new FileSaverDescriptor(VcsBundle.message("patch.creation.save.to.title"), ""), myMainPanel);
        final String path = FileUtil.toSystemIndependentName(getFileName());
        final int idx = path.lastIndexOf("/");
        VirtualFile baseDir = idx == -1 ? project.getBaseDir() :
                              (LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path.substring(0, idx))));
        baseDir = baseDir == null ? project.getBaseDir() : baseDir;
        final String name = idx == -1 ? path : path.substring(idx + 1);
        final VirtualFileWrapper fileWrapper = dialog.save(baseDir, name);
        if (fileWrapper != null) {
          myFileNameField.setText(fileWrapper.getFile().getPath());
        }
      }
    });

    myToFileButton.addChangeListener(e -> myFileNameField.setEnabled(myToFileButton.isSelected()));
    myFileNameField.setTextFieldPreferredWidth(TEXT_FIELD_WIDTH);
    myBasePathField.setTextFieldPreferredWidth(TEXT_FIELD_WIDTH);
    myBasePathField.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()));
    myWarningLabel.setForeground(JBColor.RED);
    selectBasePath(Objects.requireNonNull(myProject.getBaseDir()));
    initEncodingCombo();
  }

  public void selectBasePath(@NotNull VirtualFile baseDir) {
    myBasePathField.setText(baseDir.getPresentableUrl());
  }

  private void initEncodingCombo() {
    final DefaultComboBoxModel<Charset> encodingsModel = new DefaultComboBoxModel<>(CharsetToolkit.getAvailableCharsets());
    myEncoding.setModel(encodingsModel);
    Charset projectCharset = EncodingProjectManager.getInstance(myProject).getDefaultCharset();
    myEncoding.setSelectedItem(projectCharset);
  }

  @NotNull
  public Charset getEncoding() {
    return (Charset)myEncoding.getSelectedItem();
  }

  private void initMainPanel() {
    myFileNameField = new TextFieldWithBrowseButton();
    myBasePathField = new TextFieldWithBrowseButton();
    myReversePatchCheckbox = new JCheckBox(VcsBundle.message("create.patch.reverse.checkbox"));
    myEncoding = new ComboBox<>();
    myWarningLabel = new JLabel();
    myToFileButton = new JBRadioButton(VcsBundle.message("create.patch.file.path"), true);

    if (UIUtil.isUnderWin10LookAndFeel()) {
      myToFileButton.setBorder(JBUI.Borders.emptyRight(UIUtil.DEFAULT_HGAP));
    }

    myToClipboardButton = new JBRadioButton(VcsBundle.message("create.patch.to.clipboard"));
    ButtonGroup group = new ButtonGroup();
    group.add(myToFileButton);
    group.add(myToClipboardButton);
    JPanel toFilePanel = JBUI.Panels.simplePanel().addToLeft(myToFileButton).addToCenter(myFileNameField);

    myMainPanel = FormBuilder.createFormBuilder()
      .addComponent(toFilePanel)
      .addComponent(myToClipboardButton)
      .addVerticalGap(5)
      .addLabeledComponent(VcsBundle.message("patch.creation.base.path.field"), myBasePathField)
      .addComponent(myReversePatchCheckbox)
      .addLabeledComponent(VcsBundle.message("create.patch.encoding"), myEncoding)
      .addComponent(myWarningLabel)
      .getPanel();
  }

  public void setCommonParentPath(@Nullable File commonParentPath) {
    myCommonParentDir = commonParentPath == null || commonParentPath.isDirectory() ? commonParentPath : commonParentPath.getParentFile();
  }

  private void checkExist() {
    String fileName = getFileName();
    myWarningLabel.setText(new File(fileName).exists() ? IdeBundle.message("error.file.with.name.already.exists", fileName) : "");
  }

  public JComponent getPanel() {
    return myMainPanel;
  }

  public String getFileName() {
    return FileUtil.expandUserHome(myFileNameField.getText().trim());
  }

  @NotNull
  public String getBaseDirName() {
    return FileUtil.expandUserHome(myBasePathField.getText().trim());
  }

  public void setFileName(@NotNull Path file) {
    myFileNameField.setText(file.toString());
  }

  public boolean isReversePatch() {
    return myReversePatchCheckbox.isSelected();
  }

  public void setReversePatch(boolean reverse) {
    myReversePatchCheckbox.setSelected(reverse);
  }

  public void setReverseEnabledAndVisible(boolean isAvailable) {
    myReversePatchCheckbox.setVisible(isAvailable);
    myReversePatchCheckbox.setEnabled(isAvailable);
  }

  public boolean isToClipboard() {
    return myToClipboardButton.isSelected();
  }

  public void setToClipboard(boolean toClipboard) {
    myToClipboardButton.setSelected(toClipboard);
  }

  public boolean isOkToExecute() {
    return validateFields() == null;
  }

  @Nullable
  private ValidationInfo verifyBaseDirPath() {
    String baseDirName = getBaseDirName();
    if (StringUtil.isEmptyOrSpaces(baseDirName)) {
      return new ValidationInfo(
        VcsBundle.message("patch.creation.empty.base.path.error"), myBasePathField);
    }
    File baseFile = new File(baseDirName);
    if (!baseFile.exists()) return new ValidationInfo(VcsBundle.message("patch.creation.base.dir.does.not.exist.error"), myBasePathField);
    if (myCommonParentDir != null && !FileUtil.isAncestor(baseFile, myCommonParentDir, false)) {
      return new ValidationInfo(VcsBundle.message("patch.creation.wrong.base.path.for.changes.error", myCommonParentDir.getPath()),
                                myBasePathField);
    }
    return null;
  }

  @Nullable
  public ValidationInfo validateFields() {
    checkExist();
    String validateNameError = PatchNameChecker.validateName(getFileName());
    if (validateNameError != null) return new ValidationInfo(validateNameError, myFileNameField);
    return verifyBaseDirPath();
  }
}