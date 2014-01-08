/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.ui.table;

import com.intellij.openapi.util.Comparing;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ElementProducer;
import com.intellij.util.ui.ListTableModel;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TableModelEditor<T> implements ElementProducer<T> {
  private final TableView<T> table;

  private final Function<T, T> mutableFactory;
  private final Class<T> itemClass;

  private final MyListTableModel<T> model;

  /**
   * source will be copied, passed list will not be used directly
   * itemClass must has empty constructor
   */
  public TableModelEditor(@NotNull List<T> items, @NotNull ColumnInfo[] columns, @NotNull Function<T, T> mutableFactory, Class<T> itemClass) {
    this.itemClass = itemClass;
    this.mutableFactory = mutableFactory;

    model = new MyListTableModel<T>(columns, new ArrayList<T>(items), this);
    table = new TableView<T>(model);
    table.setStriped(true);
    new TableSpeedSearch(table);
    if (columns[0].getColumnClass() == Boolean.class && columns[0].getName().isEmpty()) {
      TableUtil.setupCheckboxColumn(table.getColumnModel().getColumn(0));
    }
  }

  private static final class MyListTableModel<T> extends ListTableModel<T> {
    private List<T> items;
    private final TableModelEditor<T> editor;
    private final THashMap<T, T> modifiedToOriginal = new THashMap<T, T>();

    public MyListTableModel(ColumnInfo[] columns, List<T> items, TableModelEditor<T> editor) {
      super(columns, items);

      this.items = items;
      this.editor = editor;
    }

    @Override
    public void setItems(@NotNull List<T> items) {
      modifiedToOriginal.clear();
      this.items = items;
      super.setItems(items);
    }

    @Override
    public void setValueAt(Object newValue, int rowIndex, int columnIndex) {
      if (rowIndex < getRowCount()) {
        @SuppressWarnings("unchecked")
        ColumnInfo<T, Object> column = (ColumnInfo<T, Object>)getColumnInfos()[columnIndex];
        T item = getItem(rowIndex);
        Object oldValue = column.valueOf(item);
        if (column.getColumnClass() == String.class
            ? !Comparing.strEqual(((String)oldValue), ((String)newValue))
            : !Comparing.equal(oldValue, newValue)) {

          T mutable;
          if (modifiedToOriginal.containsKey(item)) {
            mutable = item;
          }
          else {
            mutable = editor.mutableFactory.fun(item);
            modifiedToOriginal.put(mutable, item);
            items.set(rowIndex, mutable);
          }

          column.setValue(mutable, newValue);
        }
      }
    }

    public boolean isModified(@NotNull List<T> oldItems) {
      if (items.size() == oldItems.size()) {
        for (int i = 0, size = items.size(); i < size; i++) {
          if (!items.get(i).equals(oldItems.get(i))) {
            return true;
          }
        }
      }
      else {
        return true;
      }

      return false;
    }

    @NotNull
    public List<T> apply() {
      if (modifiedToOriginal.isEmpty()) {
        return items;
      }

      @SuppressWarnings("unchecked")
      final ColumnInfo<T, Object>[] columns = getColumnInfos();
      modifiedToOriginal.forEachEntry(new TObjectObjectProcedure<T, T>() {
        @Override
        public boolean execute(T newItem, @Nullable T item) {
          if (item == null) {
            // it is added item, we don't need to sync
            return true;
          }

          for (ColumnInfo<T, Object> column : columns) {
            if (column.isCellEditable(newItem)) {
              column.setValue(item, column.valueOf(newItem));
            }
          }
          items.set(ContainerUtil.indexOfIdentity(items, newItem), item);
          return true;
        }
      });

      modifiedToOriginal.clear();
      return items;
    }
  }

  public abstract static class EditableColumnInfo<Item, Aspect> extends ColumnInfo<Item, Aspect> {
    public EditableColumnInfo(@NotNull String name) {
      super(name);
    }

    public EditableColumnInfo() {
      super("");
    }

    @Override
    public boolean isCellEditable(Item item) {
      return true;
    }
  }

  @NotNull
  public JComponent createComponent() {
    return ToolbarDecorator.createDecorator(table, this).createPanel();
  }

  @Override
  public T createElement() {
    try {
      T item = itemClass.newInstance();
      model.modifiedToOriginal.put(item, null);
      return item;
    }
    catch (InstantiationException e) {
      throw new RuntimeException(e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean canCreateElement() {
    return true;
  }

  public boolean isModified(@NotNull List<T> oldItems) {
    return model.isModified(oldItems);
  }

  @NotNull
  public List<T> apply() {
    return model.apply();
  }

  public void reset(@NotNull List<T> items) {
    model.setItems(items);
  }
}