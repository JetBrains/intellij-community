// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.JTableHeader;
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
    int index = TableUtil.getColumnIndex(myComponent, column);
    Component comp = TableUtil.getRendererComponent(myComponent, column, index, TableUtil.isFocused(myComponent));
    if (comp == null) return null;

    AppUIUtil.targetToDevice(comp, myComponent);

    Rectangle rect = TableUtil.getColumnBounds(myComponent, index);
    rect.width = comp.getPreferredSize().width + JBUI.scale(5);
    if (rect.height > 0) rect.height--;
    return Pair.create(comp, rect);
  }

  @Override
  protected Rectangle getVisibleRect(TableColumn column) {
    return myComponent.getVisibleRect().intersection(TableUtil.getColumnBounds(myComponent,column));
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
