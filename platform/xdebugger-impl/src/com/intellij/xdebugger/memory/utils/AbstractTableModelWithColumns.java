// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.utils;

import javax.swing.table.AbstractTableModel;

public abstract class AbstractTableModelWithColumns extends AbstractTableModel {

  private final TableColumnDescriptor[] myColumnDescriptors;
  public AbstractTableModelWithColumns(TableColumnDescriptor[] descriptors) {
    myColumnDescriptors = descriptors;
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return myColumnDescriptors[columnIndex].getColumnClass();
  }

  @Override
  public String getColumnName(int column) {
    return myColumnDescriptors[column].getName();
  }

  @Override
  public int getColumnCount() {
    return myColumnDescriptors.length;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return myColumnDescriptors[columnIndex].getValue(rowIndex);
  }

  interface TableColumnDescriptor {
    Class<?> getColumnClass();
    Object getValue(int ix);
    String getName();
  }
}
