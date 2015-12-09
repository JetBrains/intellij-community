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
package com.intellij.platform;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton.BrowseFolderActionListener;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.io.File;

/**
 * Logic for updating 2 fields: name for new directory and it's base location
 *
 * @author catherine
 */
public class LocationNameFieldsBinding {
  private boolean myModifyingLocation = false;
  private boolean myModifyingProjectName = false;
  private boolean myExternalModify = false;
  private String myBaseDir;
  private String mySuggestedProjectName;

  public LocationNameFieldsBinding(@Nullable Project project,
                                   final TextFieldWithBrowseButton locationField,
                                   final JTextField nameField,
                                   String baseDir,
                                   String title) {
    myBaseDir = baseDir;
    File suggestedProjectDirectory = FileUtil.findSequentNonexistentFile(new File(baseDir), "untitled", "");
    locationField.setText(suggestedProjectDirectory.toString());
    nameField.setDocument(new NameFieldDocument(nameField, locationField));
    mySuggestedProjectName = suggestedProjectDirectory.getName();
    nameField.setText(mySuggestedProjectName);
    nameField.selectAll();

    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    BrowseFolderActionListener<JTextField> listener =
      new BrowseFolderActionListener<JTextField>(title, "", locationField, project, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {
        @Override
        protected void onFileChosen(@NotNull VirtualFile chosenFile) {
          myBaseDir = chosenFile.getPath();
          if (isProjectNameChanged(nameField.getText()) && !nameField.getText().equals(chosenFile.getName())) {
            myExternalModify = true;
            locationField.setText(new File(chosenFile.getPath(), nameField.getText()).toString());
            myExternalModify = false;
          }
          else {
            myExternalModify = true;
            locationField.setText(chosenFile.getPath());
            nameField.setText(chosenFile.getName());
            myExternalModify = false;
          }
        }
      };
    locationField.addActionListener(listener);
    locationField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (myExternalModify) {
          return;
        }
        myModifyingLocation = true;
        String path = locationField.getText().trim();
        path = StringUtil.trimEnd(path, File.separator);
        int ind = path.lastIndexOf(File.separator);
        if (ind != -1) {
          String projectName = path.substring(ind + 1, path.length());
          if (!nameField.getText().trim().isEmpty()) {
            myBaseDir = path.substring(0, ind);
          }
          if (!projectName.equals(nameField.getText())) {
            if (!myModifyingProjectName) {
              nameField.setText(projectName);
            }
          }
        }
        myModifyingLocation = false;
      }
    });
  }

  private boolean isProjectNameChanged(@NotNull String currentName) {
    return !currentName.equals(mySuggestedProjectName);
  }

  private class NameFieldDocument extends PlainDocument {
    public NameFieldDocument(final JTextField projectNameTextField, final TextFieldWithBrowseButton locationField) {
      addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(final DocumentEvent e) {
          if (!myModifyingLocation && !myExternalModify) {
            myModifyingProjectName = true;
            File f = new File(myBaseDir);
            locationField.setText(new File(f, projectNameTextField.getText()).getPath());
          }
        }
      });
    }

    @Override
    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
      StringBuilder sb = null;
      for (int i = 0; i < str.length(); i++) {
        char c = str.charAt(i);
        boolean replace = c == '\\' || c == '/' || SystemInfo.isWindows && (c == '|' || c == ':');
        if (replace) {
          if (sb == null) {
            sb = new StringBuilder(str.length());
            sb.append(str.substring(0, i));
          }
          sb.append('_');
        }
        else if (sb != null) {
          sb.append(c);
        }
      }
      if (sb != null) {
        str = sb.toString();
      }
      super.insertString(offs, str, a);
    }
  }
}
