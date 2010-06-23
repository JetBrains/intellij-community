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
package com.intellij.ui;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableModel;
import java.awt.*;

public class TableToolTipHandler extends AbstractToolTipHandler<TableCellKey, JTable> {
  protected TableToolTipHandler(JTable table) {
    super(table);

    ListSelectionListener l = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        repaintHint();
      }
    };
    table.getSelectionModel().addListSelectionListener(l);
    table.getColumnModel().getSelectionModel().addListSelectionListener(l);
  }

  public static void install(JTable table) {
    new TableToolTipHandler(table);
  }

  public Rectangle getCellBounds(TableCellKey tableCellKey, Component rendererComponent) {
    Rectangle cellRect = getCellRect(tableCellKey);
    cellRect.width = rendererComponent.getPreferredSize().width;
    return cellRect;
  }

  private Rectangle getCellRect(TableCellKey tableCellKey) {
    return myComponent.getCellRect(tableCellKey.myRowIndex, tableCellKey.myColumnIndex, false);
  }

  public Component getRendererComponent(TableCellKey key) {
    int modelColumnIndex = myComponent.convertColumnIndexToModel(key.myColumnIndex);
    final TableModel model = myComponent.getModel();
    if (key.myRowIndex < 0 || key.myRowIndex >= model.getRowCount()
        || key.myColumnIndex < 0 || key.myColumnIndex >= model.getColumnCount()) return null;

    return myComponent.getCellRenderer(key.myRowIndex, key.myColumnIndex).
      getTableCellRendererComponent(myComponent,
                                    myComponent.getModel().getValueAt(key.myRowIndex, modelColumnIndex),
                                    myComponent.getSelectionModel().isSelectedIndex(key.myRowIndex),
                                    myComponent.hasFocus(),
                                    key.myRowIndex,key.myColumnIndex
      );
  }

  public Rectangle getVisibleRect(TableCellKey key) {
    Rectangle columnVisibleRect = myComponent.getVisibleRect();
    Rectangle cellRect = getCellRect(key);
    int visibleRight = Math.min(columnVisibleRect.x + columnVisibleRect.width, cellRect.x + cellRect.width);
    columnVisibleRect.x = Math.min(columnVisibleRect.x, cellRect.x);
    columnVisibleRect.width = Math.max(0, visibleRight - columnVisibleRect.x);
    return columnVisibleRect;
  }

  //private Rectangle getColumnRectangle(int columnIndex) {
  //  TableColumnModel cm = getColumnModel();
  //  if (getComponentOrientation().isLeftToRight()) {
  //    for (int i = 0; i < column; i++) {
  //      r.x += cm.getColumn(i).getWidth();
  //    }
  //  }
  //  else {
  //    for (int i = cm.getColumnCount() - 1; i > column; i--) {
  //      r.x += cm.getColumn(i).getWidth();
  //    }
  //  }
  //  r.width = cm.getColumn(column).getWidth();
  //}

  public TableCellKey getCellKeyForPoint(Point point) {
    int rowIndex = myComponent.rowAtPoint(point);
    if (rowIndex == -1) {
      return null;
    }

    int columnIndex = myComponent.columnAtPoint(point); // column index in visible coordinates
    if (columnIndex == -1) {
      return null;
    }

    return new TableCellKey(rowIndex, columnIndex);
  }
}
