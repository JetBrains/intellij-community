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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diff.impl.patch.SelectFilesToAddTextsToPatchPanel;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.ui.JBColor;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.*;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CreatePatchConfigurationPanel {
  private static final String SYSTEM_DEFAULT = IdeBundle.message("encoding.name.system.default", CharsetToolkit.getDefaultSystemCharset().displayName());

  private JPanel myMainPanel;
  private TextFieldWithBrowseButton myFileNameField;
  private JCheckBox myReversePatchCheckbox;
  private JComboBox myEncoding;
  private JLabel myErrorLabel;
  private JCheckBox myIncludeBaseRevisionTextCheckBox;
  private Consumer<Boolean> myOkEnabledListener;
  private final Project myProject;
  private List<Change> myChanges;
  private boolean myExecute;

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
          checkName();
        }
      }
    });

    myIncludeBaseRevisionTextCheckBox.setVisible(false);
    myFileNameField.getTextField().addInputMethodListener(new InputMethodListener() {
      public void inputMethodTextChanged(final InputMethodEvent event) {
        checkName();
      }

      public void caretPositionChanged(final InputMethodEvent event) {
      }
    });
    myFileNameField.setTextFieldPreferredWidth(70);
    myFileNameField.getTextField().addKeyListener(new KeyListener() {
      public void keyTyped(final KeyEvent e) {
        checkName();
      }

      public void keyPressed(final KeyEvent e) {
        checkName();
      }

      public void keyReleased(final KeyEvent e) {
        checkName();
      }
    });
    myErrorLabel.setForeground(JBColor.RED);
    checkName();
    initEncodingCombo();
  }

  private void initEncodingCombo() {
    final DefaultComboBoxModel encodingsModel = new DefaultComboBoxModel(CharsetToolkit.getAvailableCharsets());
    encodingsModel.insertElementAt(SYSTEM_DEFAULT, 0);
    myEncoding.setModel(encodingsModel);

    final String name = EncodingProjectManager.getInstance(myProject).getDefaultCharsetName();
    if (StringUtil.isEmpty(name)) {
      myEncoding.setSelectedItem(SYSTEM_DEFAULT);
    }
    else {
      myEncoding.setSelectedItem(EncodingProjectManager.getInstance(myProject).getDefaultCharset());
    }
  }

  @Nullable
  public Charset getEncoding() {
    final Object selectedItem = myEncoding.getSelectedItem();
    if (SYSTEM_DEFAULT.equals(selectedItem)) {
      return CharsetToolkit.getDefaultSystemCharset();
    }
    return (Charset)selectedItem;
  }

  private void initMainPanel() {
    myFileNameField = new TextFieldWithBrowseButton();
    myReversePatchCheckbox = new JCheckBox(VcsBundle.message("create.patch.reverse.checkbox"));
    myEncoding = new ComboBox();
    myIncludeBaseRevisionTextCheckBox = new JCheckBox(VcsBundle.message("create.patch.base.revision", 0));
    myIncludeBaseRevisionTextCheckBox.setToolTipText(VcsBundle.message("create.patch.base.revision.tooltip"));
    myErrorLabel = new JLabel();

    myMainPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(VcsBundle.message("create.patch.file.path"), myFileNameField)
      .addComponent(myReversePatchCheckbox)
      .addComponent(myIncludeBaseRevisionTextCheckBox)
      .addLabeledComponent(VcsBundle.message("create.patch.encoding"), myEncoding)
      .addComponent(myErrorLabel)
      .getPanel();
  }

  public void showTextStoreOption() {
    if (myChanges.size() > 0) {
      myIncludeBaseRevisionTextCheckBox.setVisible(true);
      final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
      myIncludeBaseRevisionTextCheckBox.setSelected(configuration.INCLUDE_TEXT_INTO_PATCH);
    }
  }

  private void checkName() {
    final PatchNameChecker patchNameChecker = new PatchNameChecker(getFileName());
    if (patchNameChecker.nameOk()) {
      myErrorLabel.setText("");
    }
    else {
      myErrorLabel.setText(patchNameChecker.getError());
    }
    myExecute = patchNameChecker.nameOk() || !patchNameChecker.isPreventsOk();
    if (myOkEnabledListener != null) {
      myOkEnabledListener.consume(myExecute);
    }
  }

  public void onOk() {
    if (myIncludeBaseRevisionTextCheckBox.isVisible()) {
      final VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(myProject);
      vcsConfiguration.INCLUDE_TEXT_INTO_PATCH = myIncludeBaseRevisionTextCheckBox.isSelected();
    }
  }

  public boolean isStoreTexts() {
    return myIncludeBaseRevisionTextCheckBox.isSelected();
  }

  public Collection<Change> getIncludedChanges() {
    myChanges.removeAll(SelectFilesToAddTextsToPatchPanel.getBig(myChanges)) ;
    return myChanges;
  }

  public JComponent getPanel() {
    return myMainPanel;
  }

  public void installOkEnabledListener(final Consumer<Boolean> runnable) {
    myOkEnabledListener = runnable;
  }

  public String getFileName() {
    return FileUtil.expandUserHome(myFileNameField.getText().trim());
  }

  public void setFileName(final File file) {
    myFileNameField.setText(file.getPath());
    checkName();
  }

  public boolean isReversePatch() {
    return myReversePatchCheckbox.isSelected();
  }

  public void setReversePatch(boolean reverse) {
    myReversePatchCheckbox.setSelected(reverse);
  }

  public boolean isOkToExecute() {
    return myExecute;
  }

  public String getError() {
    return myErrorLabel.getText() == null ? "" : myErrorLabel.getText();
  }

  public void setChanges(@NotNull Collection<Change> changes) {
    myChanges = new ArrayList<Change>(changes);
  }
}