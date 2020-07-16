// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.EditorInfo;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
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
import org.jetbrains.annotations.NotNull;

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
    @Override
    public String valueOf(EditorInfo object) {
      return object.getUserName();
    }

    @Override
    public Comparator<EditorInfo> getComparator() {
      return (o, o1) -> o.getUserName().compareTo(o1.getUserName());
    }
  };

  private final static ColumnInfo<EditorInfo, String> HOST = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.host.column.name")){
    @Override
    public String valueOf(EditorInfo object) {
      return object.getHostName();
    }

    @Override
    public Comparator<EditorInfo> getComparator() {
      return (o, o1) -> o.getHostName().compareTo(o1.getHostName());
    }
  };

  private final static ColumnInfo<EditorInfo, String> DATE = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.date.column.name")){
    @Override
    public String valueOf(EditorInfo object) {
      return DateFormatUtil.formatPrettyDateTime(object.getEditDate());
    }

    @Override
    public Comparator<EditorInfo> getComparator() {
      return (o, o1) -> o.getEditDate().compareTo(o1.getEditDate());
    }
  };

  private final static ColumnInfo<EditorInfo, String> DIR = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.directory.column.name")){
    @Override
    public String valueOf(EditorInfo object) {
      return object.getPath();
    }

    @Override
    public Comparator<EditorInfo> getComparator() {
      return (o, o1) -> o.getPath().compareTo(o1.getPath());
    }
  };

  private final static ColumnInfo<EditorInfo, String> FILE = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.file.column.name")){
    @Override
    public String valueOf(EditorInfo object) {
      return object.getFilePath();
    }

    @Override
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
    EditSourceOnEnterKeyHandler.install(myTable);
  }

  @Override
  public void calcData(@NotNull DataKey key, @NotNull DataSink sink) {
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
