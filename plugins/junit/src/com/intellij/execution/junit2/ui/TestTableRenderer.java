package com.intellij.execution.junit2.ui;

import com.intellij.util.ui.ColumnInfo;
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
    if (row == 0) {
      final Component component = ((modelColumn == 0) ? myLeftTopRenderer : renderer).
          getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      component.setFont(component.getFont().deriveFont(Font.BOLD));
      return component;
    }
    return renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
  }

  public static void installOn(final JTable table, final ColumnInfo[] columns) {
    table.setDefaultRenderer(Object.class, new TestTableRenderer(columns));
  }
}
