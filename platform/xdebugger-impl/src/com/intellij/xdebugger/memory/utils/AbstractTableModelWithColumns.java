// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.memory.utils;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.table.AbstractTableModel;

@ApiStatus.Internal
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

  @ApiStatus.Internal
  public interface TableColumnDescriptor {
    Class<?> getColumnClass();
    Object getValue(int ix);
    @NlsContexts.ColumnName String getName();
  }
}
