// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.ui.dualView.DualView;
import com.intellij.ui.dualView.DualViewColumnInfo;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.Comparator;
import java.util.Enumeration;

abstract class FileHistoryColumnWrapper<T> extends DualViewColumnInfo<TreeNodeOnVcsRevision, T> {
  @NotNull private final ColumnInfo<VcsFileRevision, T> myBaseColumn;

  FileHistoryColumnWrapper(@NotNull ColumnInfo<VcsFileRevision, T> additionalColumn) {
    super(additionalColumn.getName());
    myBaseColumn = additionalColumn;
  }

  @Override
  public Comparator<TreeNodeOnVcsRevision> getComparator() {
    final Comparator<VcsFileRevision> comparator = myBaseColumn.getComparator();
    if (comparator == null) return null;
    return (o1, o2) -> {
      if (o1 == null) return -1;
      if (o2 == null) return 1;
      VcsFileRevision revision1 = o1.getRevision();
      VcsFileRevision revision2 = o2.getRevision();
      return comparator.compare(revision1, revision2);
    };
  }

  @Override
  public String getName() {
    return myBaseColumn.getName();
  }

  @Override
  public void setName(String s) {
    myBaseColumn.setName(s);
  }

  @Override
  public Class getColumnClass() {
    return myBaseColumn.getColumnClass();
  }

  @Override
  public boolean isCellEditable(TreeNodeOnVcsRevision o) {
    return myBaseColumn.isCellEditable(o.getRevision());
  }

  @Override
  public void setValue(TreeNodeOnVcsRevision o, T aValue) {
    myBaseColumn.setValue(o.getRevision(), aValue);
  }

  @Override
  public TableCellRenderer getRenderer(TreeNodeOnVcsRevision p0) {
    return myBaseColumn.getRenderer(p0.getRevision());
  }

  @Override
  public TableCellEditor getEditor(TreeNodeOnVcsRevision item) {
    return myBaseColumn.getEditor(item.getRevision());
  }

  @Override
  public String getMaxStringValue() {
    final String superValue = myBaseColumn.getMaxStringValue();
    if (superValue != null) return superValue;
    return getMaxValue(myBaseColumn.getName());
  }

  @Nullable
  private String getMaxValue(@NotNull String columnHeader) {
    TableView table = getDualView().getFlatView();
    if (table.getRowCount() == 0) return null;
    final Enumeration<TableColumn> columns = table.getColumnModel().getColumns();
    int idx = 0;
    while (columns.hasMoreElements()) {
      TableColumn column = columns.nextElement();
      if (columnHeader.equals(column.getHeaderValue())) {
        break;
      }
      ++idx;
    }
    if (idx >= table.getColumnModel().getColumnCount() - 1) return null;
    final FontMetrics fm = table.getFontMetrics(table.getFont().deriveFont(Font.BOLD));
    final Object header = table.getColumnModel().getColumn(idx).getHeaderValue();
    double maxValue = fm.stringWidth((String)header);
    String value = (String)header;
    for (int i = 0; i < table.getRowCount(); i++) {
      final Object at = table.getValueAt(i, idx);
      if (at instanceof String) {
        final int newWidth = fm.stringWidth((String)at);
        if (newWidth > maxValue) {
          maxValue = newWidth;
          value = (String)at;
        }
      }
    }
    return value + "ww"; // NON-NLS
  }

  @Override
  public int getAdditionalWidth() {
    return myBaseColumn.getAdditionalWidth();
  }

  @Override
  public int getWidth(JTable table) {
    return myBaseColumn.getWidth(table);
  }

  @Override
  public boolean shouldBeShownIsTheTree() {
    return true;
  }

  @Override
  public boolean shouldBeShownIsTheTable() {
    return true;
  }

  @Override
  public T valueOf(TreeNodeOnVcsRevision o) {
    return myBaseColumn.valueOf(o.getRevision());
  }

  protected abstract DualView getDualView();
}
