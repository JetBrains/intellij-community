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
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LocalPathCellEditor extends AbstractTableCellEditor {
  private final String myTitle;
  private final Project myProject;

  private FileChooserDescriptor myFileChooserDescriptor;
  private boolean myNormalizePath;

  protected CellEditorComponentWithBrowseButton<JTextField> myComponent;

  public LocalPathCellEditor(@Nullable String title, @Nullable Project project) {
    myTitle = title;
    myProject = project;
  }

  public LocalPathCellEditor(@Nullable Project project) {
    this(null, project);
  }

  public LocalPathCellEditor() {
    this(null, null);
  }

  public LocalPathCellEditor fileChooserDescriptor(@NotNull FileChooserDescriptor value) {
    myFileChooserDescriptor = value;
    return this;
  }

  /**
   * If true, path will be nullified and converted to system dependent
   */
  public LocalPathCellEditor normalizePath(boolean value) {
    myNormalizePath = value;
    return this;
  }

  @Override
  public Object getCellEditorValue() {
    String value = myComponent.getChildComponent().getText();
    return myNormalizePath ? PathUtil.toSystemDependentName(StringUtil.nullize(value)) : value;
  }

  @Override
  public Component getTableCellEditorComponent(final JTable table, Object value, boolean isSelected, final int row, int column) {
    myComponent = new CellEditorComponentWithBrowseButton<>(new TextFieldWithBrowseButton(createActionListener(table)), this);
    myComponent.getChildComponent().setText((String)value);
    return myComponent;
  }

  protected ActionListener createActionListener(final JTable table) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String initial = (String)getCellEditorValue();
        VirtualFile initialFile = StringUtil.isNotEmpty(initial) ? LocalFileSystem.getInstance().findFileByPath(initial) : null;
        FileChooser.chooseFile(getFileChooserDescriptor(), myProject, table, initialFile, file -> {
          String path = file.getPresentableUrl();
          if (SystemInfo.isWindows && path.length() == 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
            path += "\\"; // make path absolute
          }
          myComponent.getChildComponent().setText(path);
        });
      }
    };
  }

  public FileChooserDescriptor getFileChooserDescriptor() {
    if (myFileChooserDescriptor == null) {
      myFileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      if (myTitle != null) {
        myFileChooserDescriptor.setTitle(myTitle);
      }
      myFileChooserDescriptor.setShowFileSystemRoots(true);
    }
    return myFileChooserDescriptor;
  }
}
