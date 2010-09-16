/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.util.containers.ReadonlyList;
import com.intellij.util.ui.ColumnInfo;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * @author irengrig
 */
public class BigTableTableModel<T> extends AbstractTableModel {
  private final List<ColumnInfo> myColumns;
  private final ReadonlyList<T> myLines;

  public BigTableTableModel(final List<ColumnInfo> columns, final ReadonlyList<T> lines) {
    myColumns = columns;
    myLines = lines;
  }

  @Override
  public String getColumnName(int column) {
    return myColumns.get(column).getName();
  }

  @Override
  public int getColumnCount() {
    return myColumns.size();
  }

  @Override
  public int getRowCount() {
    return myLines.getSize();
  }

  // todo 7-8 - what about decoration?
  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    final T item = myLines.get(rowIndex);
    final ColumnInfo column = myColumns.get(columnIndex);
    return item == null ? column.getPreferredStringValue() : column.valueOf(item);
  }
}
