// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty;
import com.intellij.vcs.log.ui.table.LogTableColumn;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommonUiProperties {
  public static final VcsLogUiProperty<Boolean> SHOW_DETAILS = new VcsLogUiProperty<>("Window.ShowDetails");
  public static final VcsLogUiProperty<Boolean> SHOW_DIFF_PREVIEW = new VcsLogUiProperty<>("Window.ShowDiffPreview");
  public static final Map<LogTableColumn, VcsLogUiProperty<Integer>> COLUMN_WIDTH = new HashMap<>();
  public static final VcsLogUiProperty<List<Integer>> COLUMN_ORDER = new VcsLogUiProperty<>("Table.ColumnOrder");
  public static final VcsLogUiProperty<Boolean> SHOW_ROOT_NAMES = new VcsLogUiProperty<>("Table.ShowRootNames");

  static {
    for (LogTableColumn column : LogTableColumn.DYNAMIC_COLUMNS) {
      COLUMN_WIDTH.put(column, new TableColumnProperty(column));
    }
  }

  public static void saveColumnWidth(@NotNull VcsLogUiProperties properties, LogTableColumn column, int width) {
    VcsLogUiProperty<Integer> property = COLUMN_WIDTH.get(column);
    if (properties.exists(property)) {
      if (properties.get(property) != width) {
        properties.set(property, width);
      }
    }
  }

  public static int getColumnWidth(@NotNull VcsLogUiProperties properties, LogTableColumn column) {
    VcsLogUiProperty<Integer> property = COLUMN_WIDTH.get(column);
    if (properties.exists(property)) {
      return properties.get(property);
    }
    return -1;
  }

  public static class TableColumnProperty extends VcsLogUiProperty<Integer> {
    private final LogTableColumn myColumn;

    public TableColumnProperty(@NotNull LogTableColumn column) {
      super("Table." + column.getName() + "ColumnWidth");
      myColumn = column;
    }

    public int getColumnIndex() {
      return myColumn.ordinal();
    }

    public LogTableColumn getColumn() {
      return myColumn;
    }
  }
}
