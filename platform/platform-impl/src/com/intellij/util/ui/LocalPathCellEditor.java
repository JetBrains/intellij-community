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
package com.intellij.util.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LocalPathCellEditor extends AbstractTableCellEditor {
  private final String myTitle;
  private final Project myProject;

  private CellEditorComponentWithBrowseButton<JTextField> myComponent;

  public LocalPathCellEditor(String title, Project project) {
    myTitle = title;
    myProject = project;
  }

  public Object getCellEditorValue() {
    return myComponent.getChildComponent().getText();
  }

  public Component getTableCellEditorComponent(final JTable table, Object value, boolean isSelected, final int row, int column) {
    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileChooserDescriptor d = getFileChooserDescriptor();
        String initial = (String)getCellEditorValue();
        VirtualFile initialFile = StringUtil.isNotEmpty(initial) ? LocalFileSystem.getInstance().findFileByPath(initial) : null;
        VirtualFile file = FileChooser.chooseFile(d, table, myProject, initialFile);
        if (file != null) {
          String path = file.getPresentableUrl();
          if (SystemInfo.isWindows && path.length() == 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
            path += "\\"; // make path absolute
          }
          myComponent.getChildComponent().setText(path);
        }
      }
    };
    myComponent = new CellEditorComponentWithBrowseButton<JTextField>(new TextFieldWithBrowseButton(listener), this);
    myComponent.getChildComponent().setText((String)value);
    return myComponent;
  }

  public FileChooserDescriptor getFileChooserDescriptor() {
    FileChooserDescriptor d = new FileChooserDescriptor(false, true, false, true, false, false);
    if (myTitle != null) {
      d.setTitle(myTitle);
    }
    d.setShowFileSystemRoots(true);
    return d;
  }
}
