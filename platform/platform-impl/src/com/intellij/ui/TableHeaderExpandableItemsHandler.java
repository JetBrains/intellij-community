// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class TableHeaderExpandableItemsHandler extends AbstractExpandableItemsHandler<TableColumn, JTableHeader> {
  protected TableHeaderExpandableItemsHandler(@NotNull JTableHeader component) {
    super(component);
  }

  @Override
  protected @Nullable Pair<Component, Rectangle> getCellRendererAndBounds(TableColumn column) {
    TableCellRenderer renderer = column.getHeaderRenderer();
    if (renderer == null) {
      renderer = myComponent.getDefaultRenderer();
    }
    boolean hasFocus = !myComponent.isPaintingForPrint() && myComponent.hasFocus();
    Component comp = renderer.getTableCellRendererComponent(myComponent.getTable(),
                                                            column.getHeaderValue(),
                                                            false, hasFocus,
                                                            -1, column.getModelIndex());
    AppUIUtil.targetToDevice(comp, myComponent);

    Rectangle rect = myComponent.getHeaderRect(column.getModelIndex());
    rect.width = comp.getPreferredSize().width;
    return Pair.create(comp, rect);
  }

  @Override
  protected TableColumn getCellKeyForPoint(Point point) {
    int i = myComponent.columnAtPoint(point);
    if (i >= 0) {
      return myComponent.getColumnModel().getColumn(i);
    }
    return null;
  }
}
