/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.table.TableView;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.CollectionItemEditor;
import com.intellij.util.ui.CollectionModelEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.TObjectObjectProcedure;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TableModelEditor<T> extends CollectionModelEditor<T, CollectionItemEditor<T>> {
  private final TableView<T> table;
  private final ToolbarDecorator toolbarDecorator;

  private final MyListTableModel model;

  public TableModelEditor(@NotNull ColumnInfo[] columns, @NotNull CollectionItemEditor<T> itemEditor, @NotNull String emptyText) {
    this(Collections.emptyList(), columns, itemEditor, emptyText);
  }

  /**
   * source will be copied, passed list will not be used directly
   *
   * Implement {@link DialogItemEditor} instead of {@link CollectionItemEditor} if you want provide dialog to edit.
   */
  public TableModelEditor(@NotNull List<T> items, @NotNull ColumnInfo[] columns, @NotNull CollectionItemEditor<T> itemEditor, @NotNull String emptyText) {
    super(itemEditor);

    model = new MyListTableModel(columns, new ArrayList<>(items));
    table = new TableView<>(model);
    table.setDefaultEditor(Enum.class, ComboBoxTableCellEditor.INSTANCE);
    table.setStriped(true);
    table.setEnableAntialiasing(true);
    preferredScrollableViewportHeightInRows(JBTable.PREFERRED_SCROLLABLE_VIEWPORT_HEIGHT_IN_ROWS);
    new TableSpeedSearch(table);
    ColumnInfo firstColumn = columns[0];
    if ((firstColumn.getColumnClass() == boolean.class || firstColumn.getColumnClass() == Boolean.class) && firstColumn.getName().isEmpty()) {
      TableUtil.setupCheckboxColumn(table.getColumnModel().getColumn(0));
    }

   boolean needTableHeader = false;
    for (ColumnInfo column : columns) {
      if (!StringUtil.isEmpty(column.getName())) {
        needTableHeader = true;
        break;
      }
    }

    if (!needTableHeader) {
      table.setTableHeader(null);
    }

    table.getEmptyText().setText(emptyText);
    MyRemoveAction removeAction = new MyRemoveAction();
    toolbarDecorator = ToolbarDecorator.createDecorator(table, this).setRemoveAction(removeAction).setRemoveActionUpdater(removeAction);

    if (itemEditor instanceof DialogItemEditor) {
      addDialogActions();
    }
  }

  @NotNull
  public TableModelEditor<T> preferredScrollableViewportHeightInRows(int rows) {
    table.setPreferredScrollableViewportSize(new Dimension(200, table.getRowHeight() * rows));
    return this;
  }

  private void addDialogActions() {
    toolbarDecorator.setEditAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        T item = table.getSelectedObject();
        if (item != null) {
          Function<T, T> mutator;
          if (helper.isMutable(item)) {
            mutator = FunctionUtil.id();
          }
          else {
            final int selectedRow = table.getSelectedRow();
            mutator = item12 -> helper.getMutable(item12, selectedRow);
          }
          ((DialogItemEditor<T>)itemEditor).edit(item, mutator, false);
          table.requestFocus();
        }
      }
    }).setEditActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        T item = table.getSelectedObject();
        return item != null && ((DialogItemEditor<T>)itemEditor).isEditable(item);
      }
    });

    if (((DialogItemEditor)itemEditor).isUseDialogToAdd()) {
      toolbarDecorator.setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          T item = createElement();
          ((DialogItemEditor<T>)itemEditor).edit(item, item1 -> {
            model.addRow(item1);
            return item1;
          }, true);
        }
      });
    }
  }

  @NotNull
  public TableModelEditor<T> disableUpDownActions() {
    toolbarDecorator.disableUpDownActions();
    return this;
  }

  @NotNull
  public TableModelEditor<T> enabled(boolean value) {
    table.setEnabled(value);
    return this;
  }

  public static abstract class DataChangedListener<T> implements TableModelListener {
    public abstract void dataChanged(@NotNull ColumnInfo<T, ?> columnInfo, int rowIndex);

    @Override
    public void tableChanged(@NotNull TableModelEvent e) {
    }
  }

  public TableModelEditor<T> modelListener(@NotNull DataChangedListener<T> listener) {
    model.dataChangedListener = listener;
    model.addTableModelListener(listener);
    return this;
  }

  @NotNull
  public ListTableModel<T> getModel() {
    return model;
  }

  public interface DialogItemEditor<T> extends CollectionItemEditor<T> {
    void edit(@NotNull T item, @NotNull Function<T, T> mutator, boolean isAdd);

    void applyEdited(@NotNull T oldItem, @NotNull T newItem);

    default boolean isEditable(@NotNull T item) {
      return true;
    }

    default boolean isUseDialogToAdd() {
      return false;
    }
  }

  @NotNull
  public static <T> T cloneUsingXmlSerialization(@NotNull T oldItem, @NotNull T newItem) {
    Element serialized = XmlSerializer.serialize(oldItem, new SkipDefaultValuesSerializationFilters());
    if (!JDOMUtil.isEmpty(serialized)) {
      XmlSerializer.deserializeInto(newItem, serialized);
    }
    return newItem;
  }

  private final class MyListTableModel extends ListTableModel<T> {
    private List<T> items;
    private DataChangedListener<T> dataChangedListener;

    public MyListTableModel(@NotNull ColumnInfo[] columns, @NotNull List<T> items) {
      super(columns, items);

      this.items = items;
    }

    @Override
    public void setItems(@NotNull List<T> items) {
      this.items = items;
      super.setItems(items);
    }

    @Override
    public void removeRow(int index) {
      helper.remove(getItem(index));
      super.removeRow(index);
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

          column.setValue(helper.getMutable(item, rowIndex), newValue);
          if (dataChangedListener != null) {
            dataChangedListener.dataChanged(column, rowIndex);
          }
        }
      }
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
    return toolbarDecorator.addExtraAction(
      new ToolbarDecorator.ElementActionButton(IdeBundle.message("button.copy"), PlatformIcons.COPY_ICON) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          TableUtil.stopEditing(table);

          List<T> selectedItems = table.getSelectedObjects();
          if (selectedItems.isEmpty()) {
            return;
          }

          for (T item : selectedItems) {
            model.addRow(itemEditor.clone(item, false));
          }

          table.requestFocus();
          TableUtil.updateScroller(table);
        }
      }
    ).createPanel();
  }

  @NotNull
  @Override
  protected List<T> getItems() {
    return model.items;
  }

  public void selectItem(@NotNull final T item) {
    table.clearSelection();

    final Ref<T> ref;
    if (helper.hasModifiedItems()) {
      ref = Ref.create();
      helper.process(new TObjectObjectProcedure<T, T>() {
        @Override
        public boolean execute(T modified, T original) {
          if (item == original) {
            ref.set(modified);
          }
          return ref.isNull();
        }
      });
    }
    else {
      ref = null;
    }

    table.addSelection(ref == null || ref.isNull() ? item : ref.get());
  }

  @NotNull
  public List<T> apply() {
    if (helper.hasModifiedItems()) {
      @SuppressWarnings("unchecked")
      final ColumnInfo<T, Object>[] columns = model.getColumnInfos();
      helper.process(new TObjectObjectProcedure<T, T>() {
        @Override
        public boolean execute(T newItem, @NotNull T oldItem) {
          for (ColumnInfo<T, Object> column : columns) {
            if (column.isCellEditable(newItem)) {
              column.setValue(oldItem, column.valueOf(newItem));
            }
          }

          if (itemEditor instanceof DialogItemEditor) {
            ((DialogItemEditor<T>)itemEditor).applyEdited(oldItem, newItem);
          }

          model.items.set(ContainerUtil.indexOfIdentity(model.items, newItem), oldItem);
          return true;
        }
      });
    }

    helper.reset(model.items);
    return model.items;
  }

  public void reset(@NotNull List<T> items) {
    super.reset(items);
    model.setItems(new ArrayList<>(items));
  }

  private class MyRemoveAction implements AnActionButtonRunnable, AnActionButtonUpdater, TableUtil.ItemChecker {
    @Override
    public void run(AnActionButton button) {
      if (TableUtil.doRemoveSelectedItems(table, model, this)) {
        table.requestFocus();
        TableUtil.updateScroller(table);
      }
    }

    @Override
    public boolean isOperationApplyable(@NotNull TableModel ignored, int row) {
      T item = model.getItem(row);
      return item != null && itemEditor.isRemovable(item);
    }

    @Override
    public boolean isEnabled(AnActionEvent e) {
      return areSelectedItemsRemovable(table.getSelectionModel());
    }
  }
}