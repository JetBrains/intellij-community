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

import com.intellij.openapi.util.Condition;
import com.intellij.util.ui.ItemRemovable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TableUtil {
  private TableUtil() {
  }

  public interface ItemChecker {
    boolean isOperationApplyable(TableModel model, int row);
  }

  public static List<Object[]> removeSelectedItems(JTable table) {
    return removeSelectedItems(table, null);
  }

  public static void selectRows(final JTable table, final int[] viewRows) {
    ListSelectionModel selectionModel = table.getSelectionModel();
    selectionModel.clearSelection();
    int count = table.getRowCount();
    for (int row : viewRows) {
      if (row >= 0 && row < count) {
        selectionModel.addSelectionInterval(row, row);
      }
    }
  }

  public static void scrollSelectionToVisible(JTable table){
    ListSelectionModel selectionModel = table.getSelectionModel();
    int maxSelectionIndex = selectionModel.getMaxSelectionIndex();
    int minSelectionIndex = selectionModel.getMinSelectionIndex();
    final int maxColumnSelectionIndex = Math.max(0, table.getColumnModel().getSelectionModel().getMinSelectionIndex());
    final int minColumnSelectionIndex = Math.max(0, table.getColumnModel().getSelectionModel().getMaxSelectionIndex());
    if(maxSelectionIndex == -1){
      return;
    }
    Rectangle minCellRect = table.getCellRect(minSelectionIndex, minColumnSelectionIndex, false);
    Rectangle maxCellRect = table.getCellRect(maxSelectionIndex, maxColumnSelectionIndex, false);
    Point selectPoint = minCellRect.getLocation();
    int allHeight = maxCellRect.y + maxCellRect.height - minCellRect.y;
    allHeight = Math.min(allHeight, table.getVisibleRect().height);
    table.scrollRectToVisible(new Rectangle(selectPoint, new Dimension(minCellRect.width / 2,allHeight)));
  }

  public static List<Object[]> removeSelectedItems(JTable table, ItemChecker applyable) {
    if (table.isEditing()){
      table.getCellEditor().stopCellEditing();
    }
    TableModel model = table.getModel();
    if (!(model instanceof ItemRemovable)) {
      throw new RuntimeException("model must be instance of ItemRemovable");
    }

    ListSelectionModel selectionModel = table.getSelectionModel();
    int minSelectionIndex = selectionModel.getMinSelectionIndex();
    if (minSelectionIndex == -1) return new ArrayList<Object[]>(0);

    List<Object[]> removedItems = new LinkedList<Object[]>();

    final int columnCount = model.getColumnCount();
    for (int idx = table.getRowCount() - 1; idx >= 0; idx--) {
      if (selectionModel.isSelectedIndex(idx) && (applyable == null || applyable.isOperationApplyable(model, idx))) {
        final Object[] row = new Object[columnCount];
        for(int column = 0; column < columnCount; column++){
          row[column] = model.getValueAt(idx, column);
        }
        removedItems.add(0, row);
        ((ItemRemovable)model).removeRow(idx);
      }
    }
    int count = model.getRowCount();
    if (count == 0) {
      table.clearSelection();
    }
    else if (table.getSelectedRow() == -1) {
      if (minSelectionIndex >= model.getRowCount()) {
        selectionModel.setSelectionInterval(model.getRowCount() - 1, model.getRowCount() - 1);
      } else {
        selectionModel.setSelectionInterval(minSelectionIndex, minSelectionIndex);
      }
    }
    return removedItems;
  }

  public static int moveSelectedItemsUp(JTable table) {
    if (table.isEditing()){
      table.getCellEditor().stopCellEditing();
    }
    TableModel model = table.getModel();
    ListSelectionModel selectionModel = table.getSelectionModel();
    int counter = 0;
    for(int row = 0; row < model.getRowCount(); row++){
      if (selectionModel.isSelectedIndex(row)) {
        counter++;
        for (int column = 0; column < model.getColumnCount(); column++) {
          Object temp = model.getValueAt(row, column);
          model.setValueAt(model.getValueAt(row - 1, column), row, column);
          model.setValueAt(temp, row - 1, column);
        }
        selectionModel.removeSelectionInterval(row, row);
        selectionModel.addSelectionInterval(row - 1, row - 1);
      }
    }
    Rectangle cellRect = table.getCellRect(selectionModel.getMinSelectionIndex(), 0, true);
    if (cellRect != null) {
      table.scrollRectToVisible(cellRect);
    }
    table.repaint();
    return counter;
  }

  public static int moveSelectedItemsDown(JTable table) {
    if (table.isEditing()){
      table.getCellEditor().stopCellEditing();
    }
    TableModel model = table.getModel();
    ListSelectionModel selectionModel = table.getSelectionModel();
    int counter = 0;
    for(int row = model.getRowCount() - 1; row >= 0 ; row--){
      if (selectionModel.isSelectedIndex(row)) {
        counter++;
        for (int column = 0; column < model.getColumnCount(); column++) {
          Object temp = model.getValueAt(row, column);
          model.setValueAt(model.getValueAt(row + 1, column), row, column);
          model.setValueAt(temp, row + 1, column);
        }
        selectionModel.removeSelectionInterval(row, row);
        selectionModel.addSelectionInterval(row + 1, row + 1);
      }
    }
    Rectangle cellRect = table.getCellRect(selectionModel.getMaxSelectionIndex(), 0, true);
    if (cellRect != null) {
      table.scrollRectToVisible(cellRect);
    }
    table.repaint();
    return counter;
  }

  public static void editCellAt(final JTable table, int row, int column) {
    if (table.editCellAt(row, column)) {
      final Component component = table.getEditorComponent();
      if (component != null) {
        component.requestFocus();
      }
    }
  }

  public static void stopEditing(JTable table) {
    if (table.isEditing()) {
      final TableCellEditor cellEditor = table.getCellEditor();
      if (cellEditor != null) {
        cellEditor.stopCellEditing();
      }
      int row = table.getSelectedRow();
      int column = table.getSelectedColumn();
      if (row >= 0 && column >= 0) {
        TableCellEditor editor = table.getCellEditor(row, column);
        if (editor != null) {
          editor.stopCellEditing();
          //Object value = editor.getCellEditorValue();
          //
          //table.setValueAt(value, row, column);
        }
      }
    }
  }

  public static void ensureSelectionExists(JTable table) {
    if (table.getSelectedRow() != -1 || table.getRowCount() == 0) return;
    table.setRowSelectionInterval(0, 0);
  }

  public static void configureAllowedCellSelection(final JTable table, final Condition<Point> cellCondition) {
    for (String keyStroke : new String[]{"ENTER","TAB","shift TAB","RIGHT","LEFT","UP","DOWN"}) {
      final Object key = SwingActionWrapper.getKeyForActionMap(table, KeyStroke.getKeyStroke(keyStroke));
      if (key != null) {
        new MyFocusAction(table, cellCondition, key);
      }
    }
  }

  public static class MyFocusAction extends SwingActionWrapper<JTable> {

    private final Condition<Point> myCellCondition;

    public MyFocusAction(JTable table, Condition<Point> cellCondition, Object key) {
      super(table, key);
      myCellCondition = cellCondition;
    }

    public void actionPerformed(ActionEvent e) {
      final JTable table = getComponent();
      int originalRow = table.getSelectedRow();
      int originalColumn = table.getSelectedColumn();

      getDelegate().actionPerformed(e);

      final Point coord = new Point(table.getSelectedColumn(), table.getSelectedRow());

      while (!myCellCondition.value(coord)) {
        getDelegate().actionPerformed(e);

        if (coord.y == table.getSelectedRow()
            && coord.x == table.getSelectedColumn()) {
          table.changeSelection(originalRow, originalColumn, false, false);
          break;
        }

        coord.y = table.getSelectedRow();
        coord.x = table.getSelectedColumn();

        if (coord.y == originalRow && coord.x == originalColumn) {
          break;
        }
      }
    }
  }
}