// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import java.util.Comparator;
import java.util.List;

public class CommittedChangesTableModel extends ListTableModel<CommittedChangeList> {
  private final boolean myAsynchLoad;
  private static final ChangeListColumn[] ourDefaultColumns = new ChangeListColumn[] { ChangeListColumn.DATE, ChangeListColumn.NAME };
  private RowSorter.SortKey mySortKey;

  public CommittedChangesTableModel(final List<CommittedChangeList> changeLists, boolean asynchLoad) {
    super(buildColumnInfos(ourDefaultColumns), changeLists, 0);
    myAsynchLoad = asynchLoad;
  }

  public CommittedChangesTableModel(final List<CommittedChangeList> changeLists, final ChangeListColumn[] columns, boolean asynchLoad) {
    super(buildColumnInfos(columns), changeLists, 0);
    myAsynchLoad = asynchLoad;
  }

  protected void setSortKey(final RowSorter.SortKey sortKey) {
    mySortKey = sortKey;
  }

  @Override
  public RowSorter.SortKey getDefaultSortKey() {
    return mySortKey;
  }

  private static ColumnInfo[] buildColumnInfos(final ChangeListColumn[] columns) {
    ColumnInfo[] result = new ColumnInfo[columns.length];
    for(int i=0; i<columns.length; i++) {
      result [i] = new ColumnInfoAdapter(columns [i]);
    }
    return result;
  }

  private static class ColumnInfoAdapter extends ColumnInfo {
    private final ChangeListColumn myColumn;

    public ColumnInfoAdapter(ChangeListColumn column) {
      super(column.getTitle());
      myColumn = column;
    }

    @Override
    public Object valueOf(final Object o) {
      //noinspection unchecked
      return myColumn.getValue((ChangeList)o);
    }

    @Override
    public Comparator getComparator() {
      return myColumn.getComparator();
    }

    public ChangeListColumn getColumn() {
      return myColumn;
    }
  }

  public boolean isAsynchLoad() {
    return myAsynchLoad;
  }
}
