// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.Convertor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.ListIterator;

import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;

public class TableSpeedSearch extends TableSpeedSearchBase<JTable> {
  private static final PairFunction<Object, Cell, String> TO_STRING = (o, cell) -> o == null || o instanceof Boolean ? "" : o.toString();
  private final PairFunction<Object, ? super Cell, String> myToStringConvertor;

  public TableSpeedSearch(JTable table) {
    this(table, TO_STRING);
  }

  public TableSpeedSearch(JTable table, final Convertor<Object, String> toStringConvertor) {
    this(table, (o, c) -> toStringConvertor.convert(o));
  }

  public TableSpeedSearch(JTable table, final PairFunction<Object, ? super Cell, String> toStringConvertor) {
    super(table);

    myToStringConvertor = toStringConvertor;
    // edit on F2 & double click, do not interfere with quick search
    table.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);

    new MySelectAllAction(table, this).registerCustomShortcutSet(table, null);
  }

  @Override
  protected boolean isSpeedSearchEnabled() {
    boolean tableIsNotEmpty = myComponent.getRowCount() != 0 && myComponent.getColumnCount() != 0;
    return tableIsNotEmpty && !myComponent.isEditing() && super.isSpeedSearchEnabled();
  }

  @NotNull
  @Override
  protected ListIterator<Object> getElementIterator(int startingViewIndex) {
    int count = getElementCount();
    return new AbstractList<>() {
      @Override
      public Object get(int index) {
        return index;
      }

      @Override
      public int size() {
        return count;
      }
    }.listIterator(startingViewIndex);
  }

  @Override
  protected int getElementCount() {
    return myComponent.getRowCount() * myComponent.getColumnCount();
  }

  @Override
  protected void selectElement(Object element, String selectedText) {
    if (element instanceof Integer) {
      final int index = ((Integer)element).intValue();
      final int row = index / myComponent.getColumnCount();
      final int col = index % myComponent.getColumnCount();
      myComponent.getSelectionModel().setSelectionInterval(row, row);
      myComponent.getColumnModel().getSelectionModel().setSelectionInterval(col, col);
      TableUtil.scrollSelectionToVisible(myComponent);
    }
    else {
      myComponent.getSelectionModel().clearSelection();
      myComponent.getColumnModel().getSelectionModel().clearSelection();
    }
  }

  @Override
  protected int getSelectedIndex() {
    final int row = myComponent.getSelectedRow();
    final int col = myComponent.getSelectedColumn();
    // selected row is not enough as we want to select specific cell in a large multi-column table
    return row > -1 && col > -1 ? row * myComponent.getColumnCount() + col : -1;
  }

  @Override
  protected String getElementText(Object element) {
    final int index = ((Integer)element).intValue();
    int row = index / myComponent.getColumnCount();
    int col = index % myComponent.getColumnCount();
    Object value = myComponent.getValueAt(row, col);
    return myToStringConvertor.fun(value, new Cell(row, col));
  }

  @NotNull
  private IntList findAllFilteredRows(String s) {
    IntList rows = new IntArrayList();
    String _s = s.trim();

    for (int row = 0; row < myComponent.getRowCount(); row++) {
      for (int col = 0; col < myComponent.getColumnCount(); col++) {
        int index = row * myComponent.getColumnCount() + col;
        if (isMatchingElement(index, _s)) {
          rows.add(row);
          break;
        }
      }
    }
    return rows;
  }

  @Override
  protected boolean isMatchingRow(int modelRow, String pattern) {
    int columns = myComponent.getColumnCount();
    for (int col = 0; col < columns; col ++) {
      Object value = myComponent.getModel().getValueAt(modelRow, col);
      String str = myToStringConvertor.fun(value, new Cell(modelRow, col));
      if (str != null && compare(str, pattern)) {
        return true;
      }
    }
    return false;
  }

  private static class MySelectAllAction extends DumbAwareAction {
    @NotNull private final JTable myTable;
    @NotNull private final TableSpeedSearch mySearch;

    MySelectAllAction(@NotNull JTable table, @NotNull TableSpeedSearch search) {
      myTable = table;
      mySearch = search;
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_SELECT_ALL));
      setEnabledInModalContext(true);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(mySearch.isPopupActive() &&
                                     myTable.getRowSelectionAllowed() &&
                                     myTable.getSelectionModel().getSelectionMode() == MULTIPLE_INTERVAL_SELECTION);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ListSelectionModel sm = myTable.getSelectionModel();

      String query = mySearch.getEnteredPrefix();
      if (query == null) return;

      IntList filtered = mySearch.findAllFilteredRows(query);
      if (filtered.isEmpty()) {
        return;
      }

      boolean alreadySelected = Arrays.equals(filtered.toIntArray(), myTable.getSelectedRows());

      if (alreadySelected) {
        int anchor = sm.getAnchorSelectionIndex();

        sm.setSelectionInterval(anchor, anchor);
        sm.setAnchorSelectionIndex(anchor);

        mySearch.findAndSelectElement(query);
      }
      else {
        int anchor = -1;
        Object currentElement = mySearch.findElement(query);
        if (currentElement instanceof Integer) {
          int index = (Integer)currentElement;
          anchor = index / myTable.getColumnCount();
        }
        if (anchor == -1) {
          anchor = filtered.getInt(0);
        }

        sm.clearSelection();
        for (int i = 0; i < filtered.size(); i++) {
          int value = filtered.getInt(i);
          sm.addSelectionInterval(value, value);
        }
        sm.setAnchorSelectionIndex(anchor);
      }
    }
  }
}
