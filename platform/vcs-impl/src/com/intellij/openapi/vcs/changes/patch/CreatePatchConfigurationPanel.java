/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 14.11.2006
 * Time: 19:04:28
 */
package com.intellij.openapi.vcs.changes.patch;

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
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.charset.Charset;

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

  public CreatePatchConfigurationPanel(@NotNull final Project project) {
    myProject = project;
    initMainPanel();

    myFileNameField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileSaverDialog dialog =
          FileChooserFactory.getInstance().createSaveFileDialog(
            new FileSaverDescriptor("Save Patch to", ""), myMainPanel);
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

    myFileNameField.setTextFieldPreferredWidth(TEXT_FIELD_WIDTH);
    myBasePathField.setTextFieldPreferredWidth(TEXT_FIELD_WIDTH);
    myBasePathField.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()));
    myWarningLabel.setForeground(JBColor.RED);
    selectBasePath(ObjectUtils.assertNotNull(myProject.getBaseDir()));
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

    myMainPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(VcsBundle.message("create.patch.file.path"), myFileNameField)
      .addLabeledComponent("&Base path:", myBasePathField)
      .addComponent(myReversePatchCheckbox)
      .addLabeledComponent(VcsBundle.message("create.patch.encoding"), myEncoding)
      .addComponent(myWarningLabel)
      .getPanel();
  }

  public void setCommonParentPath(@Nullable File commonParentPath) {
    myCommonParentDir = commonParentPath == null || commonParentPath.isDirectory() ? commonParentPath : commonParentPath.getParentFile();
  }

  private void checkExist() {
    myWarningLabel.setText(new File(getFileName()).exists() ? "File with the same name already exists" : "");
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

  public void setFileName(final File file) {
    myFileNameField.setText(file.getPath());
  }

  public boolean isReversePatch() {
    return myReversePatchCheckbox.isSelected();
  }

  public void setReversePatch(boolean reverse) {
    myReversePatchCheckbox.setSelected(reverse);
  }

  public boolean isOkToExecute() {
    return validateFields() == null;
  }

  @Nullable
  private ValidationInfo verifyBaseDirPath() {
    String baseDirName = getBaseDirName();
    if (StringUtil.isEmptyOrSpaces(baseDirName)) return new ValidationInfo("Base path can't be empty!", myBasePathField);
    File baseFile = new File(baseDirName);
    if (!baseFile.exists()) return new ValidationInfo("Base dir doesn't exist", myBasePathField);
    if (myCommonParentDir != null && !FileUtil.isAncestor(baseFile, myCommonParentDir, false)) {
      return new ValidationInfo(String.format("Base path doesn't contain all selected changes (use %s)", myCommonParentDir.getPath()),
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