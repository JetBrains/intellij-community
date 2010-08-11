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

import java.util.*;

public class ListTableModel<Item> extends TableViewModel<Item> implements ItemRemovable {
  private ColumnInfo[] myColumnInfos;
  private List<Item> myItems;
  private int mySortByColumn;
  private int mySortingType = SortableColumnModel.SORT_ASCENDING;

  private boolean myIsSortable = true;

  public ListTableModel(ColumnInfo... columnInfos) {
    this(columnInfos, new ArrayList<Item>(), 0);
  }

  public ListTableModel(ColumnInfo[] columnNames, List<Item> items, int selectedColumn) {
    myColumnInfos = columnNames;
    myItems = items;
    mySortByColumn = selectedColumn;
    setSortable(true);
    resort();
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

  public int getColumnCount() {
    return myColumnInfos.length;
  }

  public void setItems(List<Item> items) {
    myItems = items;
    fireTableDataChanged();
    resort();
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

  public void sortByColumn(int columnIndex) {
    if (mySortByColumn == columnIndex) {
      reverseOrder(columnIndex);
    }
    else {
      mySortByColumn = columnIndex;
      mySortingType = SortableColumnModel.SORT_ASCENDING;
      resort();
    }
  }

  public void sortByColumn(int columnIndex, int sortingType) {
    if (mySortByColumn != columnIndex) {
      mySortByColumn = columnIndex;
      mySortingType = sortingType;
      resort();
    }
    else if (mySortingType != sortingType) {
      reverseOrder(columnIndex);
    }
  }

  private void reverseOrder(final int columnIndex) {
    if (mySortingType == SortableColumnModel.SORT_ASCENDING) {
      mySortingType = SortableColumnModel.SORT_DESCENDING;
    }
    else {
      mySortingType = SortableColumnModel.SORT_ASCENDING;
    }
    if (myIsSortable && myColumnInfos[columnIndex].isSortable()) {
      reverseModelItems(myItems);
      fireTableDataChanged();
    }
  }  

  protected void reverseModelItems(final List<Item> items) {
    Collections.reverse(items);
  }

  protected Object getAspectOf(int aspectIndex, Object item) {
    return myColumnInfos[aspectIndex].valueOf(item);
  }

  private void resort() {
    if (myIsSortable && mySortByColumn >= 0 && mySortByColumn < myColumnInfos.length) {
      final ColumnInfo columnInfo = myColumnInfos[mySortByColumn];
      if (columnInfo.isSortable()) {
        columnInfo.sort(myItems);
        if (mySortingType == SortableColumnModel.SORT_DESCENDING) {
          reverseModelItems(myItems);
        }
        fireTableDataChanged();
      }
    }
  }

  public int getSortedColumnIndex() {
    return mySortByColumn;
  }

  public int getSortingType() {
    return mySortingType;
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

  public void addRow(Item item) {
    myItems.add(item);
    fireTableRowsInserted(myItems.size() - 1, myItems.size() - 1);
  }

  public void addRows(final Collection<Item> items) {
    myItems.addAll(items);
    fireTableRowsInserted(myItems.size() - items.size(), myItems.size() - 1);
//    resort();
  }

  public Object getItem(final int rowIndex) {
    return getItems().get(rowIndex);
  }
}
