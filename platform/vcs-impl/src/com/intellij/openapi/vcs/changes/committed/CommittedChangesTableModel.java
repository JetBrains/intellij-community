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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 03.10.2006
 * Time: 18:54:43
 */
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public class CommittedChangesTableModel extends ListTableModel<CommittedChangeList> {
  private final boolean myAsynchLoad;
  private static final ChangeListColumn[] ourDefaultColumns = new ChangeListColumn[] { ChangeListColumn.DATE, ChangeListColumn.NAME };

  public CommittedChangesTableModel(final List<CommittedChangeList> changeLists, boolean asynchLoad) {
    super(buildColumnInfos(ourDefaultColumns), changeLists, 0);
    myAsynchLoad = asynchLoad;
  }

  public CommittedChangesTableModel(final List<CommittedChangeList> changeLists, final ChangeListColumn[] columns, boolean asynchLoad) {
    super(buildColumnInfos(columns), changeLists, 0);
    myAsynchLoad = asynchLoad;
  }

  public void sortByChangesColumn(final ChangeListColumn column, int sortingType) {
    if (column == null) {
      return;
    }
    final ColumnInfo[] columnInfos = getColumnInfos();
    for(int i=0; i<columnInfos.length; i++) {
      ColumnInfoAdapter adapter = (ColumnInfoAdapter) columnInfos [i];
      if (adapter.getColumn() == column) {
        sortByColumn(i, sortingType);
        break;
      }
    }
  }

  @Nullable
  public ChangeListColumn getSortColumn() {
    int index = getSortedColumnIndex();
    if (index < 0) return null;
    return ((ColumnInfoAdapter) getColumnInfos() [index]).getColumn();
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
