/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class CommonUiProperties {
  public static final VcsLogUiProperty<Boolean> SHOW_DETAILS = new VcsLogUiProperty<>("Window.ShowDetails");
  public static final VcsLogUiProperty<Boolean> SHOW_DIFF_PREVIEW = new VcsLogUiProperty<>("Window.ShowDiffPreview");
  public static final Map<Integer, VcsLogUiProperty<Integer>> COLUMN_WIDTH = ContainerUtil.newHashMap();
  public static final VcsLogUiProperty<List<Integer>> COLUMN_ORDER = new VcsLogUiProperty<>("Table.ColumnOrder");
  public static final VcsLogUiProperty<Boolean> SHOW_ROOT_NAMES = new VcsLogUiProperty<>("Table.ShowRootNames");

  static {
    COLUMN_WIDTH.put(GraphTableModel.AUTHOR_COLUMN, new TableColumnProperty("Author", GraphTableModel.AUTHOR_COLUMN));
    COLUMN_WIDTH.put(GraphTableModel.DATE_COLUMN, new TableColumnProperty("Date", GraphTableModel.DATE_COLUMN));
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
