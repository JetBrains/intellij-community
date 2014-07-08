package com.intellij.coverage.view;

import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.commander.AbstractListBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 1/3/12
 */
class CoverageTableModel extends AbstractTableModel implements AbstractListBuilder.Model, SortableColumnModel {
  private final ColumnInfo[] COLUMN_INFOS;

  final List myElements = new ArrayList();

  public CoverageTableModel(@NotNull CoverageSuitesBundle suitesBundle, CoverageViewManager.StateBean stateBean, Project project) {
    final CoverageEngine coverageEngine = suitesBundle.getCoverageEngine();
    COLUMN_INFOS = coverageEngine.createCoverageViewExtension(project, suitesBundle, stateBean).createColumnInfos();
  }

  public void removeAllElements() {
    myElements.clear();
    fireTableDataChanged();
  }

  public void addElement(final Object obj) {
    myElements.add(obj);
    fireTableDataChanged();
  }

  public void replaceElements(final List newElements) {
    removeAllElements();
    myElements.addAll(newElements);
    fireTableDataChanged();
  }

  public Object[] toArray() {
    return ArrayUtil.toObjectArray(myElements);
  }

  public int indexOf(final Object o) {
    return myElements.indexOf(o);
  }

  public int getSize() {
    return myElements.size();
  }

  public Object getElementAt(final int index) {
    return myElements.get(index);
  }

  public int getRowCount() {
    return myElements.size();
  }

  public int getColumnCount() {
    return COLUMN_INFOS.length;
  }

  @Override
  public String getColumnName(int column) {
    return COLUMN_INFOS[column].getName();
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    final Object element = getElementAt(rowIndex);
    if (columnIndex == 0) {
      return element;
    }
    else if (element instanceof CoverageListNode) {
      return COLUMN_INFOS[columnIndex].valueOf(element);
    }
    return element;
  }

  public ColumnInfo[] getColumnInfos() {
    return COLUMN_INFOS;
  }

  public void setSortable(boolean aBoolean) {
  }

  public boolean isSortable() {
    return true;
  }

  public Object getRowValue(int row) {
    return getElementAt(row);
  }

  public RowSorter.SortKey getDefaultSortKey() {
    return null;
  }
}
