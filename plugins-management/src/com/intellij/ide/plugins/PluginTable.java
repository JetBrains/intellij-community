package com.intellij.ide.plugins;

import com.intellij.ui.TableUtil;
import com.intellij.ui.table.TableHeaderRenderer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.ColumnInfo;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 11, 2003
 * Time: 4:19:20 PM
 * To change this template use Options | File Templates.
 */
public class PluginTable extends Table {
  public PluginTable(final PluginTableModel model) {
    super(model);

    initializeHeader(model);

    for (int i = 0; i < model.getColumnCount(); i++) {
      TableColumn column = getColumnModel().getColumn(i);
      final ColumnInfo columnInfo = model.getColumnInfos()[i];
      column.setCellEditor(columnInfo.getEditor(null));
      if (columnInfo.getColumnClass() == Boolean.class) {
        String name = columnInfo.getName();
        final int width;
        final FontMetrics fontMetrics = getFontMetrics(getFont());
        width = fontMetrics.stringWidth(" " + name + " ") + 10;

        column.setWidth(width);
        column.setPreferredWidth(width);
        column.setMaxWidth(width);
        column.setMinWidth(width);
      }
    }

    if (getColumnCount() > 2) {
      //  Specify columns widths for particular columns:
      //  Icon/Status
      TableColumn column;/* = getColumnModel().getColumn(0);
      column.setMinWidth(30);
      column.setMaxWidth(30);*/

      //  Downloads
      column = getColumnModel().getColumn(1);
      column.setMinWidth(70);
      column.setMaxWidth(70);

      //  Date:
      column = getColumnModel().getColumn(2);
      column.setMaxWidth(95);
    }

    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    setShowGrid(false);
  }

  public void setValueAt(final Object aValue, final int row, final int column) {
    super.setValueAt(aValue, row, column);
    repaint(); //in order to update invalid plugins
  }

  public TableCellRenderer getCellRenderer(final int row, final int column) {
    final ColumnInfo columnInfo = ((PluginTableModel)getModel()).getColumnInfos()[column];
    return columnInfo.getRenderer(((PluginTableModel)getModel()).getObjectAt(row));
  }

  private void initializeHeader(final PluginTableModel model) {
    final JTableHeader header = getTableHeader();
    header.setDefaultRenderer(new PluginTableHeaderRenderer(model));

    header.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        int column = getTableHeader().getColumnModel().getColumnIndexAtX(e.getX());

        if (model.sortableProvider.getSortColumn() == column) {
          if (model.sortableProvider.getSortOrder() == SortableColumnModel.SORT_DESCENDING) {
            model.sortableProvider.setSortOrder(SortableColumnModel.SORT_ASCENDING);
          }
          else {
            model.sortableProvider.setSortOrder(SortableColumnModel.SORT_DESCENDING);
          }
        }
        else {
          model.sortableProvider.setSortOrder(SortableColumnModel.SORT_ASCENDING);
          model.sortableProvider.setSortColumn(column);
        }

        final IdeaPluginDescriptor[] selectedObjects = getSelectedObjects();
        model.sortByColumn(column);
        if (selectedObjects != null){
          select(selectedObjects);
        }

        header.repaint();
      }
    });
    header.setReorderingAllowed(false);
  }

  public Object[] getElements() {
    return ((PluginTableModel)getModel()).view.toArray();
  }

  public IdeaPluginDescriptor getObjectAt(int row) {
    return ((PluginTableModel)getModel()).getObjectAt(row);
  }
  public void select(IdeaPluginDescriptor... descriptors) {
    PluginTableModel tableModel = (PluginTableModel)getModel();
    getSelectionModel().clearSelection();
    for (int i=0; i<tableModel.getRowCount();i++) {
      IdeaPluginDescriptor descriptorAt = tableModel.getObjectAt(i);
      if (ArrayUtil.find(descriptors,descriptorAt) != -1) {
        getSelectionModel().addSelectionInterval(i, i);
      }
    }
    TableUtil.scrollSelectionToVisible(this);
  }

  public IdeaPluginDescriptor getSelectedObject() {                      
    IdeaPluginDescriptor selected = null;
    if (getSelectedRowCount() > 0) {
      selected = getObjectAt(getSelectedRow());
    }
    return selected;
  }

  public IdeaPluginDescriptor[] getSelectedObjects() {
    IdeaPluginDescriptor[] selection = null;
    if (getSelectedRowCount() > 0) {
      int[] poses = getSelectedRows();
      selection = new IdeaPluginDescriptor[poses.length];
      for (int i = 0; i < poses.length; i++) {
        selection[i] = getObjectAt(poses[i]);
      }
    }
    return selection;
  }

  private static class PluginTableHeaderRenderer extends TableHeaderRenderer {
    private final PluginTableModel myTabelModel;

    public PluginTableHeaderRenderer(final PluginTableModel model) {
      super(model);
      myTabelModel = model;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      final JTableHeader header = table.getTableHeader();
      //hide title for plugin name
      myLabel.setForeground(column == myTabelModel.getNameColumn() ? header.getBackground() : header.getForeground());
      return this;
    }
  }
}
