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

package com.intellij.execution.junit2.ui;

import com.intellij.util.ui.ColumnInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

class TestTableRenderer implements TableCellRenderer {
  private final TableCellRenderer[] myRenderers;
  private final TableCellRenderer myLeftTopRenderer = new DefaultTableCellRenderer();

  public TestTableRenderer(final ColumnInfo[] columns) {
    myRenderers = new TableCellRenderer[columns.length];
    for (int i = 0; i < columns.length; i++) {
      final ColumnInfo column = columns[i];
      myRenderers[i] = column.getRenderer(null);
    }
  }

  public Component getTableCellRendererComponent(final JTable table, final Object value,
                                                 final boolean isSelected, final boolean hasFocus,
                                                 final int row, final int column) {
    final int modelColumn = table.convertColumnIndexToModel(column);
    final TableCellRenderer renderer = myRenderers[modelColumn];
    return renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
  }

}
