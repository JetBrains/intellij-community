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
package com.intellij.util.ui.table;

import com.intellij.util.ui.EditableModel;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBListTableModel extends AbstractTableModel implements EditableModel {
  private final TableModel myModel;

  public JBListTableModel(TableModel model) {
    myModel = model;
  }

  @Override
  public int getRowCount() {
    return myModel.getRowCount();
  }

  @Override
  public final int getColumnCount() {
    return 1;
  }

  @Override
  public String getColumnName(int columnIndex) {
    return null;
  }


  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return true;
  }

  public abstract JBTableRow getRow(int index);

  @Override
  public final JBTableRow getValueAt(int rowIndex, int columnIndex) {
    return getRow(rowIndex);
  }

  @Override
  public void setValueAt(Object value, int row, int column) {
    for (int i = 0; i < myModel.getColumnCount(); i++) {
      myModel.setValueAt(((JBTableRow)value).getValueAt(i), row, i);
    }
    fireTableCellUpdated(row, column);
  }

  @Override
  public void addRow() {
    final int count = myModel.getRowCount();
    if (myModel instanceof EditableModel) {
      ((EditableModel)myModel).addRow();
    }
    if (count < myModel.getRowCount()) {
      fireTableRowsInserted(count, myModel.getRowCount() - 1);
    }
  }

  @Override
  public void removeRow(int index) {
    if (myModel instanceof EditableModel) {
      ((EditableModel)myModel).removeRow(index);
    }
    fireTableRowsDeleted(index, index);
  }

  @Override
  public boolean canExchangeRows(int oldIndex, int newIndex) {
    if (myModel instanceof EditableModel) {
      return ((EditableModel)myModel).canExchangeRows(oldIndex, newIndex);
    }
    return false;
  }

  @Override
  public void exchangeRows(int oldIndex, int newIndex) {
    if (myModel instanceof EditableModel) {
      ((EditableModel)myModel).exchangeRows(oldIndex, newIndex);
    }
    fireTableRowsUpdated(Math.min(oldIndex, newIndex), Math.max(oldIndex, newIndex));
  }
}
