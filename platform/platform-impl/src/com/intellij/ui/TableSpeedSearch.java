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

package com.intellij.ui;

import com.intellij.util.containers.Convertor;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.util.ListIterator;

public class TableSpeedSearch extends SpeedSearchBase<JTable> {
  private static final Convertor<Object, String> TO_STRING = new Convertor<Object, String>() {
    public String convert(Object object) {
      return object == null? "" : object.toString();
    }
  };
  private final Convertor<Object, String> myToStringConvertor;

  public TableSpeedSearch(JTable table, Convertor<Object, String> toStringConvertor) {
    super(table);
    myToStringConvertor = toStringConvertor;
  }

  public TableSpeedSearch(JTable table) {
    this(table, TO_STRING);
  }


  protected boolean isSpeedSearchEnabled() {
    return !getComponent().isEditing() && super.isSpeedSearchEnabled();
  }

  @Override
  protected ListIterator<Object> getElementIterator(int startingIndex) {
    return new MyListIterator(startingIndex);
  }

  protected void selectElement(Object element, String selectedText) {
    final int index = ((Integer)element).intValue();
    final TableModel model = myComponent.getModel();
    final int row = index / model.getColumnCount();
    final int col = index % model.getColumnCount();
    myComponent.getSelectionModel().setSelectionInterval(row, row);
    myComponent.getColumnModel().getSelectionModel().setSelectionInterval(col, col);
    TableUtil.scrollSelectionToVisible(myComponent);
  }

  protected int getSelectedIndex() {
    final int row = myComponent.getSelectedRow();
    final int col = myComponent.getSelectedColumn();
    // TODO: WTF?! getComponent().getSelectedRow() should be enough for everyone.
    return row > -1 && col > -1? row * myComponent.getModel().getColumnCount() + col : -1;
  }

  protected Object[] getAllElements() {
    throw new AssertionError("Not Implemented");
  }

  protected String getElementText(Object element) {
    final int index = ((Integer)element).intValue();
    final TableModel model = myComponent.getModel();
    final Object value = model.getValueAt(index / model.getColumnCount(), index % model.getColumnCount());
    String string = myToStringConvertor.convert(value);
    if (string == null) return TO_STRING.convert(value);
    return string;
  }

  private class MyListIterator implements ListIterator<Object> {

    private int myCursor;

    public MyListIterator(int startingIndex) {
      final int total = getTotal();
      myCursor = startingIndex < 0? total : startingIndex;
    }

    private int getTotal() {
      final TableModel tableModel = myComponent.getModel();
      return tableModel.getRowCount() * tableModel.getColumnCount();
    }

    public boolean hasNext() {
      return myCursor < getTotal();
    }

    public Object next() {
      return myCursor++;
    }

    public boolean hasPrevious() {
      return myCursor > 0;
    }

    public Object previous() {
      return (myCursor--) - 1;
    }

    public int nextIndex() {
      return myCursor;
    }

    public int previousIndex() {
      return myCursor - 1;
    }

    public void remove() {
      throw new AssertionError("Not Implemented");
    }

    public void set(Object o) {
      throw new AssertionError("Not Implemented");
    }

    public void add(Object o) {
      throw new AssertionError("Not Implemented");
    }
  }
}
