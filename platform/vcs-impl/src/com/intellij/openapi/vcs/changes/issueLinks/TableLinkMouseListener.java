// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.dualView.TableCellRendererWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;


public class TableLinkMouseListener extends AbstractBaseTagMouseListener {
  @Override
  public @Nullable Object getTagAt(final @NotNull MouseEvent e) {
    // TODO[yole]: don't update renderer on every event, like it's done in TreeLinkMouseListener
    Object tag;
    JTable table = (JTable)e.getSource();
    int row = table.rowAtPoint(e.getPoint());
    int column = table.columnAtPoint(e.getPoint());
    if (row == -1 || column == -1) return null;
    TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
    if (cellRenderer instanceof TableCellRendererWrapper) {
      cellRenderer = ((TableCellRendererWrapper)cellRenderer).getBaseRenderer();
    }
    if (cellRenderer instanceof ColoredTableCellRenderer renderer) {
      tag = forColoredRenderer(e, table, row, column, renderer);
    }
    else {
      tag = tryGetTag(e, table, row, column, cellRenderer);
    }
    return tag;
  }

  protected Object tryGetTag(MouseEvent e, JTable table, int row, int column, TableCellRenderer cellRenderer) {
    return null;
  }

  private static Object forColoredRenderer(MouseEvent e, JTable table, int row, int column, ColoredTableCellRenderer renderer) {
    renderer.getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
    final Rectangle rc = table.getCellRect(row, column, false);
    return renderer.getFragmentTagAt(e.getX() - rc.x);
  }
}