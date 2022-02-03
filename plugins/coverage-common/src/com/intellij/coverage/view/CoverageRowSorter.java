// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CoverageRowSorter extends RowSorter<TableModel> {
  private final TreeTable myTreeTable;
  private final CoverageTableModel myModel;
  private RowSorter.SortKey mySortKey;

  public CoverageRowSorter(TreeTable table, CoverageTableModel model) {
    myTreeTable = table;
    myModel = model;
  }

  @Override
  public TableModel getModel() {
    return myTreeTable.getModel();
  }

  @Override
  public void toggleSortOrder(int column) {
    final SortOrder sortOrder = mySortKey != null && mySortKey.getColumn() == column && mySortKey.getSortOrder() == SortOrder.ASCENDING
                                ? SortOrder.DESCENDING : SortOrder.ASCENDING;
    setSortKeys(Collections.singletonList(new SortKey(column, sortOrder)));
  }

  @Override
  public int convertRowIndexToModel(int index) {
    return index;
  }

  @Override
  public int convertRowIndexToView(int index) {
    return index;
  }

  @Override
  public List<? extends SortKey> getSortKeys() {
    return mySortKey == null ? Collections.emptyList() : Collections.singletonList(mySortKey);
  }

  @Override
  public void setSortKeys(List<? extends SortKey> keys) {
    if (keys == null || keys.isEmpty()) return;
    final SortKey key = keys.get(0);
    if (key.getSortOrder() == SortOrder.UNSORTED) return;
    mySortKey = key;
    final ColumnInfo columnInfo = myModel.getColumnInfos()[key.getColumn()];
    final Comparator<? super NodeDescriptor<?>> comparator = columnInfo.getComparator();
    if (comparator != null) {
      fireSortOrderChanged();
      myModel.setComparator(reverseComparator(comparator, key.getSortOrder()));
    }
  }

  @Override
  public int getViewRowCount() {
    return getModel().getRowCount();
  }

  @Override
  public int getModelRowCount() {
    return getModel().getRowCount();
  }

  @Override
  public void modelStructureChanged() {
  }

  @Override
  public void allRowsChanged() {
  }

  @Override
  public void rowsInserted(int firstRow, int endRow) {
  }

  @Override
  public void rowsDeleted(int firstRow, int endRow) {
  }

  @Override
  public void rowsUpdated(int firstRow, int endRow) {
  }

  @Override
  public void rowsUpdated(int firstRow, int endRow, int column) {
  }

  @NotNull
  private static <T> Comparator<T> reverseComparator(@NotNull Comparator<T> comparator, SortOrder order) {
    if (order != SortOrder.DESCENDING) return comparator;
    return (o1, o2) -> -comparator.compare(o1, o2);
  }
}
