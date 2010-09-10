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
package com.intellij.util.ui;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.util.*;

public class ListTableModel<Item> extends TableViewModel<Item> implements ItemRemovable {
  private ColumnInfo[] myColumnInfos;
  private List<Item> myItems;
  private int mySortByColumn;

  private boolean myIsSortable = false;
  private SortOrder mySortOrder = SortOrder.ASCENDING;

  public ListTableModel(ColumnInfo... columnInfos) {
    this(columnInfos, new ArrayList<Item>(), 0, SortOrder.ASCENDING);
  }

  public ListTableModel(ColumnInfo[] columnNames, List<Item> items, int selectedColumn) {
    this(columnNames, items, selectedColumn, SortOrder.ASCENDING);
  }

  public ListTableModel(ColumnInfo[] columnNames, List<Item> items, int selectedColumn, final SortOrder order) {
    myColumnInfos = columnNames;
    myItems = items;
    mySortByColumn = selectedColumn;
    mySortOrder = order;

    setSortable(ContainerUtil.find(columnNames, new Condition<ColumnInfo>() {
      @Override
      public boolean value(ColumnInfo columnInfo) {
        return columnInfo.isSortable();
      }
    }) != null);
  }

  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return myColumnInfos[columnIndex].isCellEditable(myItems.get(rowIndex));
  }

  public Class getColumnClass(int columnIndex) {
    return myColumnInfos[columnIndex].getColumnClass();
  }

  public ColumnInfo[] getColumnInfos() {
    return myColumnInfos;
  }

  public String getColumnName(int column) {
    return myColumnInfos[column].getName();
  }

  public int getRowCount() {
    return myItems.size();
  }

  @Override
  public RowSorter.SortKey getDefaultSortKey() {
    if (mySortByColumn != -1) {
      return new RowSorter.SortKey(mySortByColumn, mySortOrder);
    }

    return null;
  }

  @Override
  public Object getRowValue(int row) {
    return myItems.get(row);
  }

  public int getColumnCount() {
    return myColumnInfos.length;
  }

  public void setItems(List<Item> items) {
    myItems = items;
    fireTableDataChanged();
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    return myColumnInfos[columnIndex].valueOf(myItems.get(rowIndex));
  }

  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (rowIndex < myItems.size()) {
      myColumnInfos[columnIndex].setValue(myItems.get(rowIndex), aValue);
    }
  }

  /**
   * true if changed
   *
   * @param columnInfos
   * @return
   */
  public boolean setColumnInfos(final ColumnInfo[] columnInfos) {
    if (myColumnInfos != null && Arrays.equals(columnInfos, myColumnInfos)) {
      return false;
    }
    // clear sort by column without resorting
    mySortByColumn = -1;
    myColumnInfos = columnInfos;
    fireTableStructureChanged();
    return true;
  }

  public List<Item> getItems() {
    return Collections.unmodifiableList(myItems);
  }

  protected Object getAspectOf(int aspectIndex, Object item) {
    return myColumnInfos[aspectIndex].valueOf(item);
  }

  public void setSortable(boolean aBoolean) {
    myIsSortable = aBoolean;
  }

  public boolean isSortable() {
    return myIsSortable;
  }

  public int indexOf(Item item) {
    return myItems.indexOf(item);
  }

  public void removeRow(int idx) {
    myItems.remove(idx);
    fireTableRowsDeleted(idx, idx);
  }

  public void exchangeRows(int idx1, int idx2) {
    Collections.swap(myItems, idx1, idx2);
    if (idx1 < idx2) {
      fireTableRowsUpdated(idx1, idx2);
    }
    else {
      fireTableRowsUpdated(idx2, idx1);
    }
  }

  public void addRow(Item item) {
    myItems.add(item);
    fireTableRowsInserted(myItems.size() - 1, myItems.size() - 1);
  }

  public void addRows(final Collection<Item> items) {
    myItems.addAll(items);
    fireTableRowsInserted(myItems.size() - items.size(), myItems.size() - 1);
  }

  public Object getItem(final int rowIndex) {
    return getItems().get(rowIndex);
  }
}
