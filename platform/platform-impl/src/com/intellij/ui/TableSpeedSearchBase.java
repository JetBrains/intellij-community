// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.KeyEvent;

/**
 * @author gregsh
 */
public abstract class TableSpeedSearchBase<Comp extends JTable> extends SpeedSearchBase<Comp> {

  private static final Key<Cell> SELECTION_BEFORE_KEY = Key.create("SpeedSearch.selectionBeforeSearch");

  private boolean myFilteringMode;

  public TableSpeedSearchBase(Comp component) {
    super(component);
  }

  /**
   * Turns on filtering the table when searching
   * Do not forget to configure a row sorter, e.g. {@code table.setRowSorter(new TableRowSorter<>(table.getModel()))},
   * make sure all the code uses view and model rows correctly using
   * {@link JTable#convertRowIndexToModel(int)} and {@link JTable#convertRowIndexToView(int)},
   * and note that {@link JTable#getRowCount()} will return the number of visible rows.
   * */
  public void setFilteringMode(boolean filteringMode) {
    myFilteringMode = filteringMode;
  }

  @Override
  protected void onSearchFieldUpdated(String pattern) {
    super.onSearchFieldUpdated(pattern);
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
    Cell prev = UIUtil.getClientProperty(myComponent, SELECTION_BEFORE_KEY);
    int viewRow = myComponent.getSelectedRow(); // will be -1 if there is no matching elements (not filtered by rowFilter)
    if (viewRow > -1) {
      // keep selection as is
    }
    else if (prev != null && prev.row > -1 && prev.row < myComponent.getRowCount()) {
      int col = Math.min(prev.column, myComponent.getColumnCount());
      myComponent.setRowSelectionInterval(prev.row, prev.row);
      myComponent.setColumnSelectionInterval(col, col);
    }
    else if (myComponent.getRowCount() > 0) {
      myComponent.setRowSelectionInterval(0, 0);
      myComponent.setColumnSelectionInterval(0, 0);
    }
    TableUtil.scrollSelectionToVisible(myComponent);
  }

  @Override
  protected @NotNull SearchPopup createPopup(String s) {
    // table with checkboxes may use SPACE-bound action to toggle checkboxes
    boolean ignoreSpaceTyped = myComponent.getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)) != null;
    return new SearchPopup(s) {
      @Override
      public void processKeyEvent(KeyEvent e) {
        if (ignoreSpaceTyped && e.getModifiersEx() == 0 &&
            e.getID() == KeyEvent.KEY_TYPED && e.getKeyChar() == ' ') {
          return;
        }
        super.processKeyEvent(e);
      }
    };
  }

  protected boolean isMatchingRow(int modelRow, String pattern) {
    return true;
  }
}
