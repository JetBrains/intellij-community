package com.intellij.coverage.view;

import com.intellij.coverage.CoverageAnnotator;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.JavaCoverageAnnotator;
import com.intellij.ide.commander.AbstractListBuilder;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * User: anna
 * Date: 1/3/12
 */
class CoverageTableModel extends AbstractTableModel implements AbstractListBuilder.Model, SortableColumnModel {
  private final ColumnInfo[] COLUMN_INFOS = new ColumnInfo[]{new ElementColumnInfo(), new ClassColumnInfo(), new LineColumnInfo()};

  final List myElements = new ArrayList();

  private final JavaCoverageAnnotator myAnnotator;

  CoverageTableModel(CoverageAnnotator annotator) {
    myAnnotator = (JavaCoverageAnnotator)annotator;
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
    return 3;
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
      final Object value = ((CoverageListNode)element).getValue();
      if (value instanceof PsiClass) {
        final String qualifiedName = ((PsiClass)value).getQualifiedName();
        if (columnIndex == 1) {
          return myAnnotator.getClassMethodPercentage(qualifiedName);
        }
        return myAnnotator.getClassLinePercentage(qualifiedName);
      }
      if (columnIndex == 1) {
        return myAnnotator.getPackageClassPercentage((PsiPackage)value);
      }
      return myAnnotator.getPackageLinePercentage((PsiPackage)value);
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

  private static class ClassColumnInfo extends CoverageColumnInfo {
    public ClassColumnInfo() {
      super("Class, %");
    }
  }

  private static class LineColumnInfo extends CoverageColumnInfo {
    public LineColumnInfo() {
      super("Line, %");
    }
  }

  private static class ElementColumnInfo extends CoverageColumnInfo {
    public ElementColumnInfo() {
      super("Element");
    }
  }

  private static class CoverageColumnInfo extends ColumnInfo<NodeDescriptor, String> {
    public CoverageColumnInfo(String name) {
      super(name);
    }

    @Override
    public String valueOf(NodeDescriptor node) {
      return node.toString();
    }

    @Override
    public Comparator<NodeDescriptor> getComparator() {
      return AlphaComparator.INSTANCE;
    }
  }
}
