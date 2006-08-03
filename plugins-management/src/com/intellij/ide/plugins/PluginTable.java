package com.intellij.ide.plugins;

import com.intellij.ui.table.TableHeaderRenderer;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 11, 2003
 * Time: 4:19:20 PM
 * To change this template use Options | File Templates.
 */
public class PluginTable extends JTable {
  public PluginTable(final PluginTableModel model) {
    super(model);

    initializeHeader(model);

    for (int i = 0; i < model.getColumnCount(); i++) {
      TableColumn column = getColumnModel().getColumn(i);
      column.setCellRenderer(model.getColumnInfos()[i].getRenderer(null));
    }

    //  Specify columns widths for particular columns:
    //  Icon/Status
    TableColumn column = getColumnModel().getColumn(0);
    column.setMinWidth(30);
    column.setMaxWidth(30);

    //  Downloads
    column = getColumnModel().getColumn(2);
    column.setMinWidth(70);
    column.setMaxWidth(70);

    //  Date:
    column = getColumnModel().getColumn(3);
    column.setMaxWidth(95);

    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    setShowGrid(false);
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

        model.sortByColumn(column);

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
    public PluginTableHeaderRenderer(final PluginTableModel model) {
      super(model);
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      JTableHeader header = table.getTableHeader();
      myLabel.setForeground(column == 0 ? header.getBackground() : header.getForeground());
      return this;
    }
  }
}
