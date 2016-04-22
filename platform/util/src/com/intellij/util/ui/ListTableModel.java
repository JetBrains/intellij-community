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
package com.intellij.util.ui;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class ListTableModel<Item> extends TableViewModel<Item> implements EditableModel {
  private ColumnInfo[] myColumnInfos;
  private List<Item> myItems;
  private int mySortByColumn;

  private boolean myIsSortable = false;
  private SortOrder mySortOrder = SortOrder.ASCENDING;

  public ListTableModel(@NotNull ColumnInfo... columnInfos) {
    this(columnInfos, new ArrayList<Item>(), 0, SortOrder.ASCENDING);
  }

  public ListTableModel(@NotNull ColumnInfo[] columnNames, @NotNull List<Item> items, int selectedColumn) {
    this(columnNames, items, selectedColumn, SortOrder.ASCENDING);
  }

  public ListTableModel(@NotNull ColumnInfo[] columnNames, @NotNull List<Item> items) {
    this(columnNames, items, 0);
  }

  public ListTableModel(@NotNull ColumnInfo[] columnNames, @NotNull List<Item> items, int selectedColumn, @NotNull SortOrder order) {
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

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return myColumnInfos[columnIndex].isCellEditable(myItems.get(rowIndex));
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return myColumnInfos[columnIndex].getColumnClass();
  }

  @Override
  public ColumnInfo[] getColumnInfos() {
    return myColumnInfos;
  }

  @Override
  public String getColumnName(int column) {
    return myColumnInfos[column].getName();
  }

  @Override
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
  public Item getRowValue(int row) {
    return myItems.get(row);
  }

  @Override
  public int getColumnCount() {
    return myColumnInfos.length;
  }

  @Override
  public void setItems(@NotNull List<Item> items) {
    myItems = items;
    fireTableDataChanged();
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return myColumnInfos[columnIndex].valueOf(getItem(rowIndex));
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    setValueAt(aValue, rowIndex, columnIndex, true);
  }

  /**
   * Sets the value in the cell at {@code columnIndex} and {@code rowIndex} to {@code aValue}.
   * This method allows to choose will the model listeners notified or not.
   *
   * @param aValue          the new value
   * @param rowIndex        the row whose value is to be changed
   * @param columnIndex     the column whose value is to be changed
   * @param notifyListeners indicates whether the model listeners are notified
   */
  public void setValueAt(Object aValue, int rowIndex, int columnIndex, boolean notifyListeners) {
    if (rowIndex < myItems.size()) {
      myColumnInfos[columnIndex].setValue(getItem(rowIndex), aValue);
    }
    if (notifyListeners) fireTableCellUpdated(rowIndex, columnIndex);
  }

  /**
   * true if changed
   */
  public boolean setColumnInfos(ColumnInfo[] columnInfos) {
    if (myColumnInfos != null && Arrays.equals(columnInfos, myColumnInfos)) {
      return false;
    }
    // clear sort by column without resorting
    mySortByColumn = -1;
    myColumnInfos = columnInfos;
    fireTableStructureChanged();
    return true;
  }

  @NotNull
  @Override
  public List<Item> getItems() {
    return Collections.unmodifiableList(myItems);
  }

  protected Object getAspectOf(int aspectIndex, Object item) {
    return myColumnInfos[aspectIndex].valueOf(item);
  }

  @Override
  public void setSortable(boolean aBoolean) {
    myIsSortable = aBoolean;
  }

  @Override
  public boolean isSortable() {
    return myIsSortable;
  }

  public int indexOf(Item item) {
    return myItems.indexOf(item);
  }

  @Override
  public void addRow() {
  }

  @Override
  public void removeRow(int idx) {
    myItems.remove(idx);
    fireTableRowsDeleted(idx, idx);
  }

  @Override
  public void exchangeRows(int idx1, int idx2) {
    Collections.swap(myItems, idx1, idx2);
    if (idx1 < idx2) {
      fireTableRowsUpdated(idx1, idx2);
    }
    else {
      fireTableRowsUpdated(idx2, idx1);
    }
  }

  @Override
  public boolean canExchangeRows(int oldIndex, int newIndex) {
    return true;
  }

  public void addRow(Item item) {
    myItems.add(item);
    fireTableRowsInserted(myItems.size() - 1, myItems.size() - 1);
  }

  public void insertRow(int index, Item item) {
    myItems.add(index, item);
    fireTableRowsInserted(index, index);
  }

  public void addRows(@NotNull Collection<Item> items) {
    myItems.addAll(items);
    if (!myItems.isEmpty()) {
      fireTableRowsInserted(myItems.size() - items.size(), myItems.size() - 1);
    }
  }

  public Item getItem(final int rowIndex) {
    return myItems.get(rowIndex);
  }
}
