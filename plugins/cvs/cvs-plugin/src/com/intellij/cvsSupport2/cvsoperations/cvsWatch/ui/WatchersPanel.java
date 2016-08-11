/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsoperations.cvsWatch.WatcherInfo;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

/**
 * author: lesya
 */
public class WatchersPanel extends JPanel{

  private final ListTableModel<WatcherInfo> myModel = new ListTableModel<>(COLUMNS);
  private final TableView<WatcherInfo> myTable = new TableView<>(myModel);

  private final static ColumnInfo<WatcherInfo, String> USER = new ColumnInfo<WatcherInfo, String>(CvsBundle.message("view.watchers.user.column.name")){
    public String valueOf(WatcherInfo object) {
      return object.getUser();
    }

    public Comparator<WatcherInfo> getComparator() {
      return (o, o1) -> o.getUser().compareTo(o1.getUser());
    }
  };

  private final static ColumnInfo<WatcherInfo, String> ACTIONS = new ColumnInfo<WatcherInfo, String>(CvsBundle.message("view.watchers.actions.column.name")){
    public String valueOf(WatcherInfo object) {
      return object.getActions();
    }

    public Comparator<WatcherInfo> getComparator() {
      return (o, o1) -> o.getActions().compareTo(o1.getActions());
    }
  };

  private final static ColumnInfo<WatcherInfo, String> FILE = new ColumnInfo<WatcherInfo, String>(CvsBundle.message("view.watchers.file.column.name")){
    public String valueOf(WatcherInfo object) {
      return object.getFile();
    }

    public Comparator<WatcherInfo> getComparator() {
      return (o, o1) -> o.getFile().compareTo(o1.getFile());
    }
  };

  private final static ColumnInfo[] COLUMNS = new ColumnInfo[]{
    FILE, USER, ACTIONS
  };

  public WatchersPanel(List<WatcherInfo> watchers) {
    super(new BorderLayout());
    myModel.setItems(watchers);
    add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
  }
}
