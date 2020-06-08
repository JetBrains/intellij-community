// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty;
import com.intellij.vcs.log.ui.table.VcsLogColumn;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CommonUiProperties {
  public static final VcsLogUiProperty<Boolean> SHOW_DETAILS = new VcsLogUiProperty<>("Window.ShowDetails");
  public static final VcsLogUiProperty<Boolean> SHOW_DIFF_PREVIEW = new VcsLogUiProperty<>("Window.ShowDiffPreview");
  public static final Map<VcsLogColumn, VcsLogUiProperty<Integer>> COLUMN_WIDTH = new HashMap<>();
  public static final VcsLogUiProperty<List<Integer>> COLUMN_ORDER = new VcsLogUiProperty<>("Table.ColumnOrder");
  public static final VcsLogUiProperty<Boolean> SHOW_ROOT_NAMES = new VcsLogUiProperty<>("Table.ShowRootNames");
  public static final VcsLogUiProperty<Boolean> PREFER_COMMIT_DATE = new VcsLogUiProperty<>("Table.PreferCommitDate");

  static {
    for (VcsLogColumn column : VcsLogColumn.DYNAMIC_COLUMNS) {
      COLUMN_WIDTH.put(column, new TableColumnProperty(column));
    }
  }

  public static void saveColumnWidth(@NotNull VcsLogUiProperties properties, @NotNull VcsLogColumn column, int width) {
    VcsLogUiProperty<Integer> property = COLUMN_WIDTH.get(column);
    if (properties.exists(property)) {
      if (properties.get(property) != width) {
        properties.set(property, width);
      }
    }
  }

  public static int getColumnWidth(@NotNull VcsLogUiProperties properties, @NotNull VcsLogColumn column) {
    VcsLogUiProperty<Integer> property = COLUMN_WIDTH.get(column);
    if (properties.exists(property)) {
      return properties.get(property);
    }
    return -1;
  }

  public static class TableColumnProperty extends VcsLogUiProperty<Integer> {
    private final VcsLogColumn myColumn;

    public TableColumnProperty(@NotNull VcsLogColumn column) {
      super("Table." + column.getName() + "ColumnWidth");
      myColumn = column;
    }

    public int getColumnIndex() {
      return myColumn.ordinal();
    }

    public VcsLogColumn getColumn() {
      return myColumn;
    }
  }
}
