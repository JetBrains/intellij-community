// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LocalPathCellEditor extends AbstractTableCellEditor {
  private final @DialogTitle String myTitle;
  private final Project myProject;

  private FileChooserDescriptor myFileChooserDescriptor;
  private boolean myNormalizePath;

  protected CellEditorComponentWithBrowseButton<JTextField> myComponent;

  public LocalPathCellEditor(@DialogTitle @Nullable String title, @Nullable Project project) {
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
    @NlsSafe String value = myComponent.getChildComponent().getText();
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
        if (StringUtil.isEmpty(initial)) {
          initial = getDefaultPath();
        }
        VirtualFile initialFile = StringUtil.isNotEmpty(initial) ? LocalFileSystem.getInstance().findFileByPath(initial) : null;
        FileChooser.chooseFile(getFileChooserDescriptor(), myProject, table, initialFile, file -> {
          String path = file.getPresentableUrl();
          if (SystemInfo.isWindows && path.length() == 2 && OSAgnosticPathUtil.startsWithWindowsDrive(path)) {
            path += "\\"; // make path absolute
          }
          myComponent.getChildComponent().setText(path);
        });
      }
    };
  }

  protected String getDefaultPath() {
    return null;
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
