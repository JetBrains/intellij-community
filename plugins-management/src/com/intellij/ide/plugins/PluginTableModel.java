package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 26, 2003
 * Time: 4:16:50 PM
 * To change this template use Options | File Templates.
 */
abstract public class PluginTableModel extends AbstractTableModel implements SortableColumnModel {
  protected PluginManagerColumnInfo[] columns;
  protected SortableProvider sortableProvider;
  protected List<IdeaPluginDescriptor> view;

  public PluginTableModel(SortableProvider sortableProvider, PluginManagerColumnInfo... columns) {
    this.columns = columns;
    this.sortableProvider = sortableProvider;
  }

  public int getColumnCount() {
    return columns.length;
  }

  public ColumnInfo<IdeaPluginDescriptor, String>[] getColumnInfos() {
    return columns;
  }

  public boolean isSortable() {
    return true;
  }

  public void setSortable(boolean aBoolean) {
    // do nothing cause it's always sortable
  }

  public String getColumnName(int column) {
    return columns[column].getName();
  }

  public int getSortedColumnIndex() {
    return sortableProvider.getSortColumn();
  }

  public int getSortingType() {
    return sortableProvider.getSortOrder();
  }

  public IdeaPluginDescriptor getObjectAt (int row) {
    return view.get(row);
  }

  public int getRowCount() {
    return view.size();
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    return columns[columnIndex].valueOf(getObjectAt(rowIndex));
  }

  public void sortByColumn(int columnIndex) {
    Collections.sort(view, columns[columnIndex].getComparator());
    fireTableDataChanged();
  }

  public void sortByColumn(int columnIndex, int sortingType) {
    sortByColumn(columnIndex);
  }


  public ArrayList<IdeaPluginDescriptorImpl> dependent(IdeaPluginDescriptorImpl plugin) {
    ArrayList<IdeaPluginDescriptorImpl> list = new ArrayList<IdeaPluginDescriptorImpl>();
    for (IdeaPluginDescriptor any : view) {
      if (any instanceof IdeaPluginDescriptorImpl) {
        PluginId[] dep = any.getDependentPluginIds();
        for (PluginId id : dep) {
          if (id == plugin.getPluginId()) {
            list.add((IdeaPluginDescriptorImpl)any);
            break;
          }
        }
      }
    }
    return list;
  }

  public abstract void addData(ArrayList<IdeaPluginDescriptor> list);

  public abstract void modifyData(ArrayList<IdeaPluginDescriptor> list);

  protected void safeSort() {
    final int sortColumn = sortableProvider.getSortColumn();
    sortByColumn(-1 < sortColumn && sortColumn < getColumnCount() ? sortColumn : 0);
  }
}
