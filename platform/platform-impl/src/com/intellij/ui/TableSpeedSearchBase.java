// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 * @author gregsh
 */
public abstract class TableSpeedSearchBase<Comp extends JTable> extends SpeedSearchBase<Comp> {

  private static final Key<Cell> SELECTION_BEFORE_KEY = Key.create("SpeedSearch.selectionBeforeSearch");

  private boolean myFilteringMode;

  public TableSpeedSearchBase(Comp component) {
    super(component);
  }

  public void setFilteringMode(boolean filteringMode) {
    myFilteringMode = filteringMode;
  }

  @Override
  protected void onSearchFieldUpdated(String pattern) {
    if (!myFilteringMode) return;
    RowSorter<? extends TableModel> sorter0 = myComponent.getRowSorter();
    if (!(sorter0 instanceof TableRowSorter)) return;
    //noinspection unchecked
    TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>)sorter0;
    if (StringUtil.isNotEmpty(pattern)) {
      if (sorter.getRowFilter() == null) {
        UIUtil.putClientProperty(myComponent, SELECTION_BEFORE_KEY, new Cell(myComponent.getSelectedRow(), myComponent.getSelectedColumn()));
      }
      sorter.setRowFilter(new RowFilter<>() {
        @Override
        public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
          return isMatchingRow(entry.getIdentifier(), pattern);
        }
      });
    }
    else {
      UIUtil.putClientProperty(myComponent, SELECTION_BEFORE_KEY, null);
      sorter.setRowFilter(null);
    }
  }

  @Override
  protected boolean isStickySearch() {
    return myFilteringMode;
  }

  @Override
  public void hidePopup() {
    super.hidePopup();
    if (!myFilteringMode) return;
    onSearchFieldUpdated("");
    Cell prev = UIUtil.getClientProperty(myComponent, SELECTION_BEFORE_KEY);
    int viewRow = myComponent.getSelectedRow(); // will be -1 if there is no matching elements (not filtered by rowFilter)
    if (viewRow > -1) {
      // keep selection as is
    }
    else if (prev != null && prev.row > -1) {
      myComponent.setRowSelectionInterval(prev.row, prev.row);
      myComponent.setColumnSelectionInterval(prev.column, prev.column);
    }
    else if (myComponent.getRowCount() > 0) {
      myComponent.setRowSelectionInterval(0, 0);
      myComponent.setColumnSelectionInterval(0, 0);
    }
    TableUtil.scrollSelectionToVisible(myComponent);
  }

  protected boolean isMatchingRow(int modelRow, String pattern) {
    return true;
  }
}
