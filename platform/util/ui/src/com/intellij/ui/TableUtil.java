// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ItemRemovable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class TableUtil {
  private TableUtil() {
  }

  public interface ItemChecker {
    boolean isOperationApplyable(@NotNull TableModel model, int row);
  }

  @NotNull
  public static List<Object[]> removeSelectedItems(@NotNull JTable table) {
    return removeSelectedItems(table, null);
  }

  public static void selectRows(@NotNull JTable table, int @NotNull [] viewRows) {
    ListSelectionModel selectionModel = table.getSelectionModel();
    selectionModel.clearSelection();
    int count = table.getRowCount();
    for (int row : viewRows) {
      if (row >= 0 && row < count) {
        selectionModel.addSelectionInterval(row, row);
      }
    }
  }

  public static void scrollSelectionToVisible(@NotNull JTable table){
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
    allHeight = Math.min(allHeight, table.getHeight());
    table.scrollRectToVisible(new Rectangle(selectPoint, new Dimension(minCellRect.width / 2,allHeight)));
  }

  @NotNull
  public static List<Object[]> removeSelectedItems(@NotNull JTable table, @Nullable ItemChecker applyable) {
    final TableModel model = table.getModel();
    if (!(model instanceof ItemRemovable itemRemovable)) {
      throw new RuntimeException("model must be instance of ItemRemovable");
    }

    if (table.getSelectionModel().isSelectionEmpty()) {
      return new ArrayList<>(0);
    }

    final List<Object[]> removedItems = new SmartList<>();
    final int columnCount = model.getColumnCount();
    doRemoveSelectedItems(table, new ItemRemovable() {
      @Override
      public void removeRow(int index) {
        Object[] row = new Object[columnCount];
        for (int column = 0; column < columnCount; column++) {
          row[column] = model.getValueAt(index, column);
        }
        removedItems.add(row);
        itemRemovable.removeRow(index);
      }
    }, applyable);
    return ContainerUtil.reverse(removedItems);
  }

  public static boolean doRemoveSelectedItems(@NotNull JTable table, @NotNull ItemRemovable itemRemovable, @Nullable ItemChecker applyable) {
    if (table.isEditing()) {
      table.getCellEditor().stopCellEditing();
    }

    ListSelectionModel selectionModel = table.getSelectionModel();
    int minSelectionIndex = selectionModel.getMinSelectionIndex();
    int maxSelectionIndex = selectionModel.getMaxSelectionIndex();
    if (minSelectionIndex == -1 || maxSelectionIndex == -1) {
      return false;
    }

    TableModel model = table.getModel();
    boolean removed = false;
    for (int index = maxSelectionIndex; index >= 0; index--) {
      int modelIndex = table.convertRowIndexToModel(index);
      if (selectionModel.isSelectedIndex(index) && (applyable == null || applyable.isOperationApplyable(model, modelIndex))) {
        itemRemovable.removeRow(modelIndex);
        removed = true;
      }
    }

    if (!removed) {
      return false;
    }

    int count = model.getRowCount();
    if (count == 0) {
      table.clearSelection();
    }
    else if (selectionModel.getMinSelectionIndex() == -1) {
      if (minSelectionIndex >= model.getRowCount()) {
        selectionModel.setSelectionInterval(model.getRowCount() - 1, model.getRowCount() - 1);
      }
      else {
        selectionModel.setSelectionInterval(minSelectionIndex, minSelectionIndex);
      }
    }
    return true;
  }

  public static int moveSelectedItemsUp(@NotNull JTable table) {
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
    table.scrollRectToVisible(cellRect);
    table.repaint();
    return counter;
  }

  public static int moveSelectedItemsDown(@NotNull JTable table) {
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
    table.scrollRectToVisible(cellRect);
    table.repaint();
    return counter;
  }

  public static void editCellAt(@NotNull JTable table, int row, int column) {
    if (table.editCellAt(row, column)) {
      final Component component = table.getEditorComponent();
      if (component != null) {
        component.requestFocus();
      }
    }
  }

  public static void stopEditing(@NotNull JTable table) {
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

  public static void ensureSelectionExists(@NotNull JTable table) {
    if (table.getSelectedRow() != -1 || table.getRowCount() == 0) return;
    table.setRowSelectionInterval(0, 0);
  }

  public static void setupCheckboxColumn(@NotNull JTable table, int columnIndex) {
    TableColumnModel cModel = table.getColumnModel();
    setupCheckboxColumn(cModel.getColumn(columnIndex), cModel.getColumnMargin());
  }

  /**
   * @deprecated doesn't take into account column margin.
   * Use {@link #setupCheckboxColumn(JTable, int)} instead.
   * Or use {@link #setupCheckboxColumn(TableColumn, int)} with {@link TableColumnModel#getColumnMargin()} accounted for.
   */
  @Deprecated
  public static void setupCheckboxColumn(@NotNull TableColumn column) {
    setupCheckboxColumn(column, 0);
  }

  public static void setupCheckboxColumn(@NotNull TableColumn column, int additionalWidth) {
    int checkboxWidth = new JCheckBox().getPreferredSize().width + additionalWidth;
    column.setResizable(false);
    column.setPreferredWidth(checkboxWidth);
    column.setMaxWidth(checkboxWidth);
    column.setMinWidth(checkboxWidth);
  }

  public static void updateScroller(@NotNull JTable table) {
    JScrollPane scrollPane = ComponentUtil.getParentOfType((Class<? extends JScrollPane>)JScrollPane.class, (Component)table);
    if (scrollPane != null) {
      scrollPane.revalidate();
      scrollPane.repaint();
    }
  }


  public static Rectangle getColumnBounds(@NotNull JTableHeader header, int index) {
    return header.getHeaderRect(index);
  }

  public static Rectangle getColumnBounds(@NotNull JTableHeader header, @Nullable TableColumn column) {
    return getColumnBounds(header, getColumnIndex(header, column));
  }

  public static int getColumnIndex(@NotNull JTableHeader header, @Nullable TableColumn column) {
    if (column == null) return -1;
    TableColumnModel model = header.getColumnModel();
    if (model == null) return -1;
    int index = model.getColumnCount();
    while (0 < index--) {
      if (column == model.getColumn(index)) break;
    }
    return index;
  }

  public static boolean isFocused(@NotNull JTableHeader header) {
    return !header.isPaintingForPrint() && header.hasFocus();
  }

  public static TableCellRenderer getRenderer(@NotNull JTableHeader header, @Nullable TableColumn column) {
    TableCellRenderer renderer = column == null ? null : column.getHeaderRenderer();
    return renderer != null ? renderer : header.getDefaultRenderer();
  }

  public static Component getRendererComponent(@NotNull JTableHeader header, @Nullable TableColumn column, int index, boolean focused) {
    TableCellRenderer renderer = column == null ? null : getRenderer(header, column);
    return renderer == null ? null : renderer.getTableCellRendererComponent(
      header.getTable(), column.getHeaderValue(), false, focused, -1, index);
  }
}