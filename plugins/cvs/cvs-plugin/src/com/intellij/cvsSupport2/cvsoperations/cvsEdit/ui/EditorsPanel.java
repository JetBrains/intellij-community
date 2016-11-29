/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.EditorInfo;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Comparator;
import java.util.List;


/**
 * author: lesya
 */
public class EditorsPanel extends JPanel implements TypeSafeDataProvider {

  private final ListTableModel<EditorInfo> myModel = new ListTableModel<>(COLUMNS);
  private final TableView<EditorInfo> myTable = new TableView<>(myModel);

  private final static ColumnInfo<EditorInfo, String> USER = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.user.column.name")){
    public String valueOf(EditorInfo object) {
      return object.getUserName();
    }

    public Comparator<EditorInfo> getComparator() {
      return (o, o1) -> o.getUserName().compareTo(o1.getUserName());
    }
  };

  private final static ColumnInfo<EditorInfo, String> HOST = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.host.column.name")){
    public String valueOf(EditorInfo object) {
      return object.getHostName();
    }

    public Comparator<EditorInfo> getComparator() {
      return (o, o1) -> o.getHostName().compareTo(o1.getHostName());
    }
  };

  private final static ColumnInfo<EditorInfo, String> DATE = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.date.column.name")){
    public String valueOf(EditorInfo object) {
      return DateFormatUtil.formatPrettyDateTime(object.getEditDate());
    }

    public Comparator<EditorInfo> getComparator() {
      return (o, o1) -> o.getEditDate().compareTo(o1.getEditDate());
    }
  };

  private final static ColumnInfo<EditorInfo, String> DIR = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.directory.column.name")){
    public String valueOf(EditorInfo object) {
      return object.getPath();
    }

    public Comparator<EditorInfo> getComparator() {
      return (o, o1) -> o.getPath().compareTo(o1.getPath());
    }
  };

  private final static ColumnInfo<EditorInfo, String> FILE = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.file.column.name")){
    public String valueOf(EditorInfo object) {
      return object.getFilePath();
    }

    public Comparator<EditorInfo> getComparator() {
      return (o, o1) -> o.getFilePath().compareTo(o1.getFilePath());
    }
  };

  private final static ColumnInfo[] COLUMNS = new ColumnInfo[]{
    FILE, USER, DATE, HOST, DIR
  };
  private final Project myProject;

  public EditorsPanel(final Project project, List<EditorInfo> editors) {
    super(new BorderLayout());
    myProject = project;
    myModel.setItems(editors);
    add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    EditSourceOnDoubleClickHandler.install(myTable);
    EditSourceOnEnterKeyHandler.install(myTable, null);
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key.equals(CommonDataKeys.PROJECT)) {
      sink.put(CommonDataKeys.PROJECT, myProject);
    }
    else if (key.equals(CommonDataKeys.NAVIGATABLE)) {
      final EditorInfo editorInfo = myTable.getSelectedObject();
      if (editorInfo == null) {
        return;
      }
      String filePath = editorInfo.getFilePath();
      final int pos = filePath.lastIndexOf('/');
      if (pos >= 0) {
        filePath = filePath.substring(pos+1);
      }
      final File file = new File(editorInfo.getPath(), filePath);
      final VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(file);
      if (vf != null) {
        sink.put(CommonDataKeys.NAVIGATABLE, new OpenFileDescriptor(myProject, vf));
      }
    }
  }
}
