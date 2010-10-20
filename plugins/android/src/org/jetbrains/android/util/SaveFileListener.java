/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.util;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.filechooser.FileView;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class SaveFileListener implements ActionListener {
  private final JPanel myContentPanel;
  private final TextFieldWithBrowseButton myTextField;
  private final String myDialogTitle;

  public SaveFileListener(JPanel contentPanel, TextFieldWithBrowseButton textField, String dialogTitle) {
    myContentPanel = contentPanel;
    myTextField = textField;
    myDialogTitle = dialogTitle;
  }

  @Nullable
  protected abstract String getDefaultLocation();

  public void actionPerformed(ActionEvent e) {
    String path = myTextField.getText().trim();
    if (path.length() == 0) {
      String defaultLocation = getDefaultLocation();
      path = defaultLocation != null ? defaultLocation : "";
    }
    File file = new File(path);
    if (!file.exists()) {
      path = file.getParent();
    }
    JFileChooser fileChooser = new JFileChooser(path);
    FileView fileView = new FileView() {
      public Icon getIcon(File f) {
        if (f.isDirectory()) return super.getIcon(f);
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(f.getName());
        return fileType.getIcon();
      }
    };
    fileChooser.setFileView(fileView);
    fileChooser.setMultiSelectionEnabled(false);
    fileChooser.setAcceptAllFileFilterUsed(false);
    fileChooser.setDialogTitle(myDialogTitle);

    if (fileChooser.showSaveDialog(myContentPanel) != JFileChooser.APPROVE_OPTION) return;
    file = fileChooser.getSelectedFile();
    if (file == null) return;
    myTextField.setText(file.getPath());
  }
}
