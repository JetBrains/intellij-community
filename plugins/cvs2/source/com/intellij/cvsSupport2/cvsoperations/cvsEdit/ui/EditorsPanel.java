package com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui;

import com.intellij.cvsSupport2.cvsoperations.cvsEdit.EditorInfo;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import java.awt.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;


/**
 * author: lesya
 */
public class EditorsPanel extends JPanel{

  public static final DateFormat DATE_FORMAT =
      SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT);

  private final ListTableModel myModel = new ListTableModel(COLUMNS);
  private final TableView myTable = new TableView(myModel);

  private final static ColumnInfo USER = new ColumnInfo("User"){
    public Object valueOf(Object object) {
      return ((EditorInfo)object).getUserName();
    }

    public Comparator getComparator() {
      return new Comparator(){
        public int compare(Object o, Object o1) {
          return ((EditorInfo)o).getUserName().
            compareTo(((EditorInfo)o1).getUserName());
        }
      };
    }
  };

  private final static ColumnInfo HOST = new ColumnInfo("Host"){
    public Object valueOf(Object object) {
      return ((EditorInfo)object).getHostHame();
    }

    public Comparator getComparator() {
      return new Comparator(){
        public int compare(Object o, Object o1) {
          return ((EditorInfo)o).getHostHame().
            compareTo(((EditorInfo)o1).getHostHame());
        }
      };
    }
  };

  private final static ColumnInfo DATE = new ColumnInfo("Date"){
    public Object valueOf(Object object) {
      return DATE_FORMAT.format(((EditorInfo)object).getEditDate());
    }

    public Comparator getComparator() {
      return new Comparator(){
        public int compare(Object o, Object o1) {
          return ((EditorInfo)o).getEditDate().
            compareTo(((EditorInfo)o1).getEditDate());
        }
      };
    }
  };

  private final static ColumnInfo DIR = new ColumnInfo("Directory"){
    public Object valueOf(Object object) {
      return ((EditorInfo)object).getPath();
    }

    public Comparator getComparator() {
      return new Comparator(){
        public int compare(Object o, Object o1) {
          return ((EditorInfo)o).getPath().
            compareTo(((EditorInfo)o1).getPath());
        }
      };
    }
  };

  private final static ColumnInfo FILE = new ColumnInfo("File"){
    public Object valueOf(Object object) {
      return ((EditorInfo)object).getFilePath();
    }

    public Comparator getComparator() {
      return new Comparator(){
        public int compare(Object o, Object o1) {
          return ((EditorInfo)o).getFilePath().
            compareTo(((EditorInfo)o1).getFilePath());
        }
      };
    }
  };

  private final static ColumnInfo[] COLUMNS = new ColumnInfo[]{
    FILE, USER, DATE, HOST, DIR
  };


  public EditorsPanel(java.util.List<EditorInfo> editors) {
    super(new BorderLayout());
    myModel.setItems(editors);
    add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
  }

}
