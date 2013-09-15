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
package com.intellij.platform;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
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
 * User: catherine
 * <p/>
 * Logic for updating 2 fields: name for new directory and it's base location
 */
public class LocationNameFieldsBinding {
  private boolean myModifyingLocation = false;
  private boolean myModifyingProjectName = false;
  private boolean myExternalModify = false;
  private String myBaseDir;
  private String mySuggestedProjectName;

  public LocationNameFieldsBinding(@Nullable Project project, final TextFieldWithBrowseButton locationTextField,
                                   final JTextField nameTextField, String baseDir, final String browseFolderTitle) {

    myBaseDir = baseDir;
    File suggestedProjectDirectory = FileUtil.findSequentNonexistentFile(new File(baseDir), "untitled", "");
    locationTextField.setText(suggestedProjectDirectory.toString());
    nameTextField.setDocument(new NameFieldDocument(nameTextField, locationTextField));
    mySuggestedProjectName = suggestedProjectDirectory.getName();
    nameTextField.setText(mySuggestedProjectName);
    nameTextField.selectAll();

    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> listener =
      new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>(browseFolderTitle, "", locationTextField,
                                                                           project,
                                                                           descriptor,
                                                                           TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {
        @Override
        protected void onFileChoosen(@NotNull VirtualFile chosenFile) {
          myBaseDir = chosenFile.getPath();
          if (isProjectNameChanged(nameTextField.getText()) && !nameTextField.getText().equals(chosenFile.getName())) {
            myExternalModify = true;
            locationTextField.setText(new File(chosenFile.getPath(), nameTextField.getText()).toString());
            myExternalModify = false;
          }
          else {
            myExternalModify = true;
            locationTextField.setText(chosenFile.getPath());
            nameTextField.setText(chosenFile.getName());
            myExternalModify = false;
          }
        }
      };
    locationTextField.addActionListener(listener);
    locationTextField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (myExternalModify) {
          return;
        }
        myModifyingLocation = true;
        String path = locationTextField.getText().trim();
        if (path.endsWith(File.separator)) {
          path = path.substring(0, path.length() - File.separator.length());
        }
        int ind = path.lastIndexOf(File.separator);
        if (ind != -1) {
          String projectName = path.substring(ind + 1, path.length());
          if (!nameTextField.getText().trim().isEmpty()) {
            myBaseDir = path.substring(0, ind);
          }
          if (!projectName.equals(nameTextField.getText())) {
            if (!myModifyingProjectName) {
              nameTextField.setText(projectName);
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
      boolean ok = true;
      for (int idx = 0; idx < str.length() && ok; idx++) {
        char ch = str.charAt(idx);
        ok = ch != File.separatorChar && ch != '\\' && ch != '/' && ch != '|' && ch != ':';
      }
      if (ok) {
        super.insertString(offs, str, a);
      }
    }
  }
}
