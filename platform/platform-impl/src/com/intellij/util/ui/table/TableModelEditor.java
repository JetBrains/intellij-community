// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.table;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.table.TableView;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TableModelEditor<T> extends CollectionModelEditor<T, CollectionItemEditor<T>> {
  private final TableView<T> table;
  private final ToolbarDecorator toolbarDecorator;

  private final MyListTableModel model;

  public TableModelEditor(ColumnInfo @NotNull [] columns,
                          @NotNull CollectionItemEditor<T> itemEditor,
                          @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String emptyText) {
    this(Collections.emptyList(), columns, itemEditor, emptyText);
  }

  /**
   * source will be copied, passed list will not be used directly
   *
   * Implement {@link DialogItemEditor} instead of {@link CollectionItemEditor} if you want provide dialog to edit.
   */
  public TableModelEditor(@NotNull List<T> items, ColumnInfo @NotNull [] columns, @NotNull CollectionItemEditor<T> itemEditor,
                          @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String emptyText) {
    super(itemEditor);

    model = new MyListTableModel(columns, new ArrayList<>(items));
    table = new TableView<>(model);
    table.setShowGrid(false);
    table.setDefaultEditor(Enum.class, ComboBoxTableCellEditor.INSTANCE);
    table.setEnableAntialiasing(true);
    table.setPreferredScrollableViewportSize(JBUI.size(200, -1));
    table.setVisibleRowCount(JBTable.PREFERRED_SCROLLABLE_VIEWPORT_HEIGHT_IN_ROWS);
    TableSpeedSearch.installOn(table);
    ColumnInfo firstColumn = columns[0];
    if ((firstColumn.getColumnClass() == boolean.class || firstColumn.getColumnClass() == Boolean.class) && firstColumn.getName().isEmpty()) {
      TableUtil.setupCheckboxColumn(table.getColumnModel().getColumn(0), 0);
      JBTable.setupCheckboxShortcut(table, 0);
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

  private void addDialogActions() {
    toolbarDecorator.setEditAction(button -> {
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
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(table, true));
      }
    }).setEditActionUpdater(e -> {
      T item = table.getSelectedObject();
      return item != null && ((DialogItemEditor<T>)itemEditor).isEditable(item);
    });

    if (((DialogItemEditor<?>)itemEditor).isUseDialogToAdd()) {
      toolbarDecorator.setAddAction(button -> {
        T item = createElement();
        ((DialogItemEditor<T>)itemEditor).edit(item, item1 -> {
          model.addRow(item1);
          return item1;
        }, true);
      });
    }
  }

  public @NotNull TableModelEditor<T> disableUpDownActions() {
    toolbarDecorator.disableUpDownActions();
    return this;
  }

  public void setShowGrid(boolean v) {
    table.setShowGrid(v);
  }

  public @NotNull TableModelEditor<T> enabled(boolean value) {
    table.setEnabled(value);
    return this;
  }

  public abstract static class DataChangedListener<T> implements TableModelListener {
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

  public @NotNull ListTableModel<T> getModel() {
    return model;
  }

  public interface DialogItemEditor<T> extends CollectionItemEditor<T> {
    void edit(@NotNull T item, @NotNull Function<? super T, ? extends T> mutator, boolean isAdd);

    void applyEdited(@NotNull T oldItem, @NotNull T newItem);

    default boolean isEditable(@NotNull T item) {
      return true;
    }

    default boolean isUseDialogToAdd() {
      return false;
    }
  }

  public static <T> void cloneUsingXmlSerialization(@NotNull T oldItem, @NotNull T newItem) {
    Element serialized = com.intellij.configurationStore.XmlSerializer.serialize(oldItem);
    if (serialized != null) {
      XmlSerializer.deserializeInto(newItem, serialized);
    }
  }

  private final class MyListTableModel extends ListTableModel<T> {
    private List<T> items;
    private DataChangedListener<T> dataChangedListener;

    MyListTableModel(ColumnInfo @NotNull [] columns, @NotNull List<T> items) {
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
    public EditableColumnInfo(@NotNull @NlsContexts.ColumnName String name) {
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

  public @NotNull JComponent createComponent() {
    return toolbarDecorator.addExtraAction(
      new DumbAwareAction(IdeBundle.message("button.copy"), null, PlatformIcons.COPY_ICON) {
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

          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(table, true));
          TableUtil.updateScroller(table);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(!table.getSelectedObjects().isEmpty());
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }
      }
    ).createPanel();
  }

  @Override
  protected @NotNull List<T> getItems() {
    return model.items;
  }

  public void selectItem(final @NotNull T item) {
    table.clearSelection();

    Ref<T> ref;
    if (helper.hasModifiedItems()) {
      ref = Ref.create();
      helper.process((modified, original) -> {
        if (item == original) {
          ref.set(modified);
        }
        return ref.isNull();
      });
    }
    else {
      ref = null;
    }

    table.addSelection(ref == null || ref.isNull() ? item : ref.get());
  }

  public @NotNull List<T> apply() {
    if (helper.hasModifiedItems()) {
      @SuppressWarnings("unchecked")
      final ColumnInfo<T, Object>[] columns = model.getColumnInfos();
      helper.process((newItem, oldItem) -> {
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
      });
    }

    helper.reset(model.items);
    return model.items;
  }

  @Override
  public void reset(@NotNull List<? extends T> items) {
    super.reset(items);
    model.setItems(new ArrayList<>(items));
  }

  private final class MyRemoveAction implements AnActionButtonRunnable, AnActionButtonUpdater, TableUtil.ItemChecker {
    @Override
    public void run(AnActionButton button) {
      if (TableUtil.doRemoveSelectedItems(table, model, this)) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(table, true));
        TableUtil.updateScroller(table);
      }
    }

    @Override
    public boolean isOperationApplyable(@NotNull TableModel ignored, int row) {
      T item = model.getItem(row);
      return item != null && itemEditor.isRemovable(item);
    }

    @Override
    public boolean isEnabled(@NotNull AnActionEvent e) {
      return areSelectedItemsRemovable(table.getSelectionModel());
    }
  }
}