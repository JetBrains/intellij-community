/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.cvsWatch.ui;

import com.intellij.cvsSupport2.cvsoperations.cvsWatch.WatcherInfo;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.CvsBundle;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;

/**
 * author: lesya
 */
public class WatchersPanel extends JPanel{
  private final ListTableModel myModel = new ListTableModel(COLUMNS);
  private final TableView myTable = new TableView(myModel);

  private final static ColumnInfo USER = new ColumnInfo(CvsBundle.message("view.watchers.user.column.name")){
    public Object valueOf(Object object) {
      return ((WatcherInfo)object).getUser();
    }

    public Comparator getComparator() {
      return new Comparator(){
        public int compare(Object o, Object o1) {
          return ((WatcherInfo)o).getUser().
            compareTo(((WatcherInfo)o1).getUser());
        }
      };
    }
  };

  private final static ColumnInfo ACTIONS = new ColumnInfo(CvsBundle.message("view.watchers.actions.column.name")){
    public Object valueOf(Object object) {
      return ((WatcherInfo)object).getActions();
    }

    public Comparator getComparator() {
      return new Comparator(){
        public int compare(Object o, Object o1) {
          return ((WatcherInfo)o).getActions().
            compareTo(((WatcherInfo)o1).getActions());
        }
      };
    }
  };

  private final static ColumnInfo FILE = new ColumnInfo(CvsBundle.message("view.watchers.file.column.name")){
    public Object valueOf(Object object) {
      return ((WatcherInfo)object).getFile();
    }

    public Comparator getComparator() {
      return new Comparator(){
        public int compare(Object o, Object o1) {
          return ((WatcherInfo)o).getFile().
            compareTo(((WatcherInfo)o1).getFile());
        }
      };
    }
  };

  private final static ColumnInfo[] COLUMNS = new ColumnInfo[]{
    FILE, USER, ACTIONS
  };


  public WatchersPanel(java.util.List<WatcherInfo> watchers) {
    super(new BorderLayout());
    myModel.setItems(watchers);
    add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
  }

}
