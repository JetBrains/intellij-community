// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommonUiProperties {
  public static final VcsLogUiProperty<Boolean> SHOW_DETAILS = new VcsLogUiProperty<>("Window.ShowDetails");
  public static final VcsLogUiProperty<Boolean> SHOW_DIFF_PREVIEW = new VcsLogUiProperty<>("Window.ShowDiffPreview");
  public static final Map<Integer, VcsLogUiProperty<Integer>> COLUMN_WIDTH = new HashMap<>();
  public static final VcsLogUiProperty<List<Integer>> COLUMN_ORDER = new VcsLogUiProperty<>("Table.ColumnOrder");
  public static final VcsLogUiProperty<Boolean> SHOW_ROOT_NAMES = new VcsLogUiProperty<>("Table.ShowRootNames");

  static {
    for (int columnIndex : GraphTableModel.DYNAMIC_COLUMNS) {
      COLUMN_WIDTH.put(columnIndex, new TableColumnProperty(GraphTableModel.COLUMN_NAMES[columnIndex], columnIndex));
    }
  }

  public static void saveColumnWidth(@NotNull VcsLogUiProperties properties, int column, int width) {
    if (properties.exists(COLUMN_WIDTH.get(column))) {
      if (properties.get(COLUMN_WIDTH.get(column)) != width) {
        properties.set(COLUMN_WIDTH.get(column), width);
      }
    }
  }

  public static int getColumnWidth(@NotNull VcsLogUiProperties properties, int column) {
    if (properties.exists(COLUMN_WIDTH.get(column))) {
      return properties.get(COLUMN_WIDTH.get(column));
    }
    return -1;
  }

  public static class TableColumnProperty extends VcsLogUiProperty<Integer> {
    private final int myColumn;

    public TableColumnProperty(@NotNull String name, int column) {
      super("Table." + name + "ColumnWidth");
      myColumn = column;
    }

    public int getColumn() {
      return myColumn;
    }
  }
}
