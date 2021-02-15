// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.project.ProjectKt;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class CreatePatchConfigurationPanel {
  private static final int TEXT_FIELD_WIDTH = 70;

  private JPanel myMainPanel;
  private TextFieldWithBrowseButton myFileNameField;
  private TextFieldWithBrowseButton myBasePathField;
  private JCheckBox myReversePatchCheckbox;
  private ComboBox<Charset> myEncoding;
  private JBTextField myContextLineCount;
  private JLabel myWarningLabel;
  private final Project myProject;
  @Nullable private File myCommonParentDir;
  private JBRadioButton myToClipboardButton;
  private JBRadioButton myToFileButton;

  public CreatePatchConfigurationPanel(@NotNull Project project) {
    myProject = project;
    initMainPanel();

    myFileNameField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        FileSaverDialog dialog = FileChooserFactory.getInstance()
          .createSaveFileDialog(new FileSaverDescriptor(VcsBundle.message("patch.creation.save.to.title"), ""), myMainPanel);
        String path = FileUtil.toSystemIndependentName(getFileName());
        int index = path.lastIndexOf("/");
        Path baseDir = index == -1 ? ProjectKt.getStateStore(project).getProjectBasePath() : Paths.get(path.substring(0, index));
        String name = index == -1 ? path : path.substring(index + 1);
        VirtualFileWrapper fileWrapper = dialog.save(baseDir, name);
        if (fileWrapper != null) {
          myFileNameField.setText(fileWrapper.getFile().getPath());
        }
      }
    });

    new ComponentValidator(myProject).withValidator(() -> {
      String lines = myContextLineCount.getText();
      if (StringUtil.isEmpty(lines)) {
        return null;
      }
      try {
        int contextLineCount = Integer.parseInt(lines);
        if (contextLineCount >= 0) {
          return null;
        }
        return new ValidationInfo(VcsBundle.message("patch.creation.context.line.count.not.a.number.error"), myContextLineCount);
      }
      catch (NumberFormatException nfe) {
        return new ValidationInfo(VcsBundle.message("patch.creation.context.line.count.not.a.number.error"), myContextLineCount);
      }
    }).installOn(myContextLineCount);

    myContextLineCount.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        ComponentValidator.getInstance(myContextLineCount).ifPresent(v -> v.revalidate());
      }
    });

    myToFileButton.addChangeListener(e -> myFileNameField.setEnabled(myToFileButton.isSelected()));
    myFileNameField.setTextFieldPreferredWidth(TEXT_FIELD_WIDTH);
    myBasePathField.setTextFieldPreferredWidth(TEXT_FIELD_WIDTH);
    myContextLineCount.setMinimumSize(myContextLineCount.getPreferredSize());
    myBasePathField.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()));
    myWarningLabel.setForeground(JBColor.RED);
    selectBasePath(ProjectKt.getStateStore(project).getProjectBasePath().toString());
    initEncodingCombo();
  }

  public void selectBasePath(@NotNull String baseDir) {
    myBasePathField.setText(baseDir);
  }

  private void initEncodingCombo() {
    ComboBoxModel<Charset> encodingsModel = new DefaultComboBoxModel<>(CharsetToolkit.getAvailableCharsets());
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
    myContextLineCount = new JBTextField();
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
      .addLabeledComponent(VcsBundle.message("patch.creation.context.field"), myContextLineCount)
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

  public int getContextLineCount() {
    return Integer.parseInt(myContextLineCount.getText());
  }

  public void setContextLineCount(int lineCount) {
    myContextLineCount.setText(Integer.toString(lineCount));
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
    ComponentValidator validator = ComponentValidator.getInstance(myContextLineCount).orElse(null);
    if (validator != null && validator.getValidationInfo() != null) {
      return validator.getValidationInfo();
    }
    return verifyBaseDirPath();
  }
}