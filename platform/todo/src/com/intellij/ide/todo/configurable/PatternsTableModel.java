// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.configurable;

import com.intellij.ide.IdeBundle;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.ui.ItemRemovable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.List;

final class PatternsTableModel extends AbstractTableModel implements ItemRemovable {
  private final String[] myColumnNames = {IdeBundle.message("column.todo.patterns.icon"), IdeBundle.message("column.todo.patterns.case.sensitive"), IdeBundle.message("column.todo.patterns.pattern")};
  private final List<TodoPattern> myPatterns;

  PatternsTableModel(List<TodoPattern> patterns) {
    myPatterns = patterns;
  }

  @Override
  public String getColumnName(int column) {
    return myColumnNames[column];
  }

  @Override
  public Class<?> getColumnClass(int column) {
    return switch (column) {
      case 0 -> Icon.class;
      case 1 -> Boolean.class;
      case 2 -> String.class;
      default -> throw new IllegalArgumentException();
    };
  }

  @Override
  public int getColumnCount() {
    return 3;
  }

  @Override
  public int getRowCount() {
    return myPatterns.size();
  }

  @Override
  public Object getValueAt(int row, int column) {
    var pattern = myPatterns.get(row);
    return switch (column) {
      case 0 -> pattern.getAttributes().getIcon();
      case 1 -> Boolean.valueOf(pattern.isCaseSensitive());
      case 2 -> pattern.getPatternString();
      default -> throw new IllegalArgumentException();
    };
  }

  @Override
  public void setValueAt(Object value, int row, int column) {
    var pattern = myPatterns.get(row);
    switch (column) {
      case 0 -> pattern.getAttributes().setIcon((Icon)value);
      case 1 -> pattern.setCaseSensitive(((Boolean)value).booleanValue());
      case 2 -> pattern.setPatternString(((String)value).trim());
      default -> throw new IllegalArgumentException();
    }
  }

  @Override
  public void removeRow(int index) {
    myPatterns.remove(index);
    fireTableRowsDeleted(index, index);
  }
}
