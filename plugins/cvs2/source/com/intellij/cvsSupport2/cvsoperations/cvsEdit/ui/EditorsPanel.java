package com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.EditorInfo;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.List;


/**
 * author: lesya
 */
public class EditorsPanel extends JPanel implements TypeSafeDataProvider {

  public static final DateFormat DATE_FORMAT =
      SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT);

  private final ListTableModel<EditorInfo> myModel = new ListTableModel<EditorInfo>(COLUMNS);
  private final TableView<EditorInfo> myTable = new TableView<EditorInfo>(myModel);

  private final static ColumnInfo<EditorInfo, String> USER = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.user.column.name")){
    public String valueOf(EditorInfo object) {
      return object.getUserName();
    }

    public Comparator<EditorInfo> getComparator() {
      return new Comparator<EditorInfo>(){
        public int compare(EditorInfo o, EditorInfo o1) {
          return o.getUserName().compareTo(o1.getUserName());
        }
      };
    }
  };

  private final static ColumnInfo<EditorInfo, String> HOST = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.host.column.name")){
    public String valueOf(EditorInfo object) {
      return object.getHostHame();
    }

    public Comparator<EditorInfo> getComparator() {
      return new Comparator<EditorInfo>(){
        public int compare(EditorInfo o, EditorInfo o1) {
          return o.getHostHame().compareTo(o1.getHostHame());
        }
      };
    }
  };

  private final static ColumnInfo<EditorInfo, String> DATE = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.date.column.name")){
    public String valueOf(EditorInfo object) {
      return DATE_FORMAT.format(object.getEditDate());
    }

    public Comparator<EditorInfo> getComparator() {
      return new Comparator<EditorInfo>(){
        public int compare(EditorInfo o, EditorInfo o1) {
          return o.getEditDate().compareTo(o1.getEditDate());
        }
      };
    }
  };

  private final static ColumnInfo<EditorInfo, String> DIR = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.directory.column.name")){
    public String valueOf(EditorInfo object) {
      return object.getPath();
    }

    public Comparator<EditorInfo> getComparator() {
      return new Comparator<EditorInfo>(){
        public int compare(EditorInfo o, EditorInfo o1) {
          return o.getPath().compareTo(o1.getPath());
        }
      };
    }
  };

  private final static ColumnInfo<EditorInfo, String> FILE = new ColumnInfo<EditorInfo, String>(CvsBundle.message("view.editors.file.column.name")){
    public String valueOf(EditorInfo object) {
      return object.getFilePath();
    }

    public Comparator<EditorInfo> getComparator() {
      return new Comparator<EditorInfo>(){
        public int compare(EditorInfo o, EditorInfo o1) {
          return o.getFilePath().compareTo(o1.getFilePath());
        }
      };
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
    if (key.equals(PlatformDataKeys.PROJECT)) {
      sink.put(PlatformDataKeys.PROJECT, myProject);
    }
    else if (key.equals(DataKeys.NAVIGATABLE)) {
      final EditorInfo editorInfo = myTable.getSelectedObject();
      if (editorInfo == null) {
        return;
      }
      String filePath = editorInfo.getFilePath();
      int pos = filePath.lastIndexOf('/');
      if (pos >= 0) {
        filePath = filePath.substring(pos+1);
      }
      File file = new File(editorInfo.getPath(), filePath);
      VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(file);
      if (vf != null) {
        sink.put(DataKeys.NAVIGATABLE, new OpenFileDescriptor(myProject, vf));
      }
    }
  }
}
