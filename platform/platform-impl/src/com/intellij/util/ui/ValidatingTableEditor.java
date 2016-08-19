/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.HoverHyperlinkLabel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ValidatingTableEditor<Item> implements ComponentWithEmptyText {

  private static final Icon WARNING_ICON = UIUtil.getBalloonWarningIcon();
  private static final Icon EMPTY_ICON = EmptyIcon.create(WARNING_ICON);
  @NonNls private static final String REMOVE_KEY = "REMOVE_SELECTED";

  public interface RowHeightProvider {
    int getRowHeight();
  }

  public interface Fix extends Runnable {
    String getTitle();
  }

  private class ColumnInfoWrapper extends ColumnInfo<Item, Object> {
    private final ColumnInfo<Item, Object> myDelegate;

    public ColumnInfoWrapper(ColumnInfo<Item, Object> delegate) {
      super(delegate.getName());
      myDelegate = delegate;
    }

    @Override
    public Object valueOf(Item item) {
      return myDelegate.valueOf(item);
    }

    @Override
    public boolean isCellEditable(Item item) {
      return myDelegate.isCellEditable(item);
    }

    @Override
    public void setValue(Item item, Object value) {
      myDelegate.setValue(item, value);
      updateMessage(-1, null);
    }

    @Override
    public TableCellEditor getEditor(Item item) {
      return myDelegate.getEditor(item);
    }

    @Override
    public int getWidth(JTable table) {
      return myDelegate.getWidth(table);
    }

    @Override
    public Class getColumnClass() {
      return myDelegate.getColumnClass();
    }

    @Override
    public TableCellRenderer getRenderer(Item item) {
      return myDelegate.getRenderer(item);
    }
  }

  private JPanel myContentPane;
  private TableView<Item> myTable;
  private AnActionButton myRemoveButton;
  private JLabel myMessageLabel;
  private HoverHyperlinkLabel myFixLink;
  private JPanel myTablePanel;
  private final List<String> myWarnings = new ArrayList<>();
  private Fix myFixRunnable;

  protected abstract Item cloneOf(Item item);

  @Nullable
  protected Pair<String, Fix> validate(List<Item> current, List<String> warnings) {
    String error = null;
    for (int i = 0; i < current.size(); i++) {
      Item item = current.get(i);
      String s = validate(item);
      warnings.set(i, s);
      if (error == null) {
        error = s;
      }
    }
    return error != null ? Pair.create(error, (Fix)null) : null;
  }

  @Nullable
  protected String validate(Item item) {
    return null;
  }

  @Nullable
  protected abstract Item createItem();

  private class IconColumn extends ColumnInfo<Item, Object> implements RowHeightProvider {
    public IconColumn() {
      super(" ");
    }

    public String valueOf(Item item) {
      return null;
    }

    @Override
    public int getWidth(JTable table) {
      return WARNING_ICON.getIconWidth() + 2;
    }

    public int getRowHeight() {
      return WARNING_ICON.getIconHeight();
    }

    @Override
    public TableCellRenderer getRenderer(final Item item) {
      return new WarningIconCellRenderer(() -> myWarnings.get(doGetItems().indexOf(item)));
    }
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myTable.getEmptyText();
  }

  private void createUIComponents() {
    myTable = new ChangesTrackingTableView<Item>() {
      protected void onCellValueChanged(int row, int column, Object value) {
        final Item original = getItems().get(row);
        Item override = cloneOf(original);
        final ColumnInfo<Item, Object> columnInfo = getTableModel().getColumnInfos()[column];
        columnInfo.setValue(override, value);
        updateMessage(row, override);
      }

      @Override
      protected void onEditingStopped() {
        updateMessage(-1, null);
      }
    };

    myFixLink = new HoverHyperlinkLabel(null);
  }

  protected ValidatingTableEditor(@Nullable AnActionButton ... extraButtons) {
    ToolbarDecorator decorator =
      ToolbarDecorator.createDecorator(myTable).disableRemoveAction().disableUpAction().disableDownAction();
    decorator.setAddAction(new AnActionButtonRunnable() {

      @Override
      public void run(AnActionButton anActionButton) {
        addItem();
      }
    });


    myRemoveButton = new AnActionButton(ApplicationBundle.message("button.remove"), IconUtil.getRemoveIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        removeSelected();
      }
    };
    myRemoveButton.setShortcut(CustomShortcutSet.fromString("alt DELETE")); //NON-NLS
    decorator.addExtraAction(myRemoveButton);

    if (extraButtons != null &&  extraButtons.length != 0) {
      for (AnActionButton extraButton : extraButtons) {
        decorator.addExtraAction(extraButton);
      }
    }

    myTablePanel.add(decorator.createPanel(), BorderLayout.CENTER);

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });

    myTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), REMOVE_KEY);
    myTable.getActionMap().put(REMOVE_KEY, new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        removeSelected();
      }
    });

    myFixLink.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && myFixRunnable != null) {
          myFixRunnable.run();
        }
      }
    });
  }

  protected ValidatingTableEditor() {
    //noinspection NullArgumentToVariableArgMethod
    this(null);
  }

  @Nullable
  public List<Item> getSelectedItems() {
    return myTable.getSelectedObjects();
  }

  private void removeSelected() {
    myTable.stopEditing();
    List<Item> items = new ArrayList<>(doGetItems());
    final int[] rows = myTable.getSelectedRows();
    for (int i = rows.length - 1; i >= 0; i--) {
      items.remove(rows[i]);
    }

    setItems(items);
    updateMessage(-1, null);

    if (!items.isEmpty()) {
      int index = Math.min(rows[0], items.size() - 1);
      myTable.getSelectionModel().addSelectionInterval(index, index);
    }
  }

  protected void addItem() {
    Item newItem = createItem();
    if (newItem == null) {
      return;
    }
    List<Item> items = new ArrayList<>(doGetItems());
    items.add(newItem);

    setItems(items);

    final int row = items.size() - 1;
    myTable.getSelectionModel().setSelectionInterval(row, row);
    myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));
    if (getTableModel().getColumnInfos()[1].isCellEditable(items.get(row))) {
      myTable.editCellAt(row, 1);
      IdeFocusManager.findInstanceByComponent(myContentPane).requestFocus(myTable.getEditorComponent(), true);
    }
    updateMessage(-1, null);
  }

  private ListTableModel<Item> getTableModel() {
    return (ListTableModel<Item>)myTable.getModel();
  }

  public void setModel(ColumnInfo<Item, Object>[] valueColumns, List<Item> items) {
    ColumnInfo[] columns = new ColumnInfo[valueColumns.length + 1];
    IconColumn iconColumn = new IconColumn();
    int maxHeight = iconColumn.getRowHeight();

    columns[0] = iconColumn;
    for (int i = 0; i < valueColumns.length; i++) {
      columns[i + 1] = new ColumnInfoWrapper(valueColumns[i]);
      if (valueColumns[i] instanceof RowHeightProvider) {
        maxHeight = Math.max(maxHeight, ((RowHeightProvider)valueColumns[i]).getRowHeight());
      }
    }

    myTable.stopEditing();
    myTable.setModelAndUpdateColumns(new ListTableModel<>(columns));
    if (maxHeight > 0) {
      myTable.setRowHeight(maxHeight);
    }

    setItems(items);
    updateMessage(-1, null);
  }

  public List<Item> getItems() {
    return Collections.unmodifiableList(doGetItems());
  }

  private List<Item> doGetItems() {
    List<Item> items = new ArrayList<>(getTableModel().getItems());
    if (myTable.isEditing()) {
      Object value = ChangesTrackingTableView.getValue(myTable.getEditorComponent());
      ColumnInfo column = ((ListTableModel)myTable.getModel()).getColumnInfos()[myTable.getEditingColumn()];
      ((ColumnInfoWrapper)column).myDelegate.setValue(items.get(myTable.getEditingRow()), value);
    }
    return items;
  }

  private void setItems(List<Item> items) {
    if (items.isEmpty()) {
      getTableModel().setItems(Collections.<Item>emptyList());
      myWarnings.clear();
    }
    else {
      myWarnings.clear();
      for (Item item : items) {
        myWarnings.add(null);
      }
      getTableModel().setItems(new ArrayList<>(items));
    }
    updateButtons();
  }

  public void setTableHeader(JTableHeader header) {
    myTable.setTableHeader(header);
  }

  private void updateButtons() {
    myRemoveButton.setEnabled(myTable.getSelectedRow() != -1);
  }

  public void updateMessage(int index, @Nullable Item override) {
    List<Item> current = new ArrayList<>(doGetItems());
    if (override != null) {
      current.set(index, override);
    }

    displayMessageAndFix(validate(current, myWarnings));
    myTable.repaint();
  }

  protected void displayMessageAndFix(@Nullable Pair<String, Fix> messageAndFix) {
    if (messageAndFix != null) {
      myMessageLabel.setText(messageAndFix.first);
      myMessageLabel.setIcon(WARNING_ICON);
      myMessageLabel.setVisible(true);
      myFixRunnable = messageAndFix.second;
      myFixLink.setVisible(myFixRunnable != null);
      myFixLink.setText(myFixRunnable != null ? myFixRunnable.getTitle() : null);
    }
    else {
      myMessageLabel.setText(" ");
      myMessageLabel.setIcon(EMPTY_ICON);
      myFixLink.setVisible(false);
      myFixRunnable = null;
    }
  }

  public void hideMessageLabel() {
    myMessageLabel.setVisible(false);
    myFixLink.setVisible(false);
  }

  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }


  private static class WarningIconCellRenderer extends DefaultTableCellRenderer {
    private final NullableComputable<String> myWarningProvider;

    public WarningIconCellRenderer(NullableComputable<String> warningProvider) {
      myWarningProvider = warningProvider;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      String message = myWarningProvider.compute();
      label.setIcon(message != null ? WARNING_ICON : null);
      label.setToolTipText(message);
      label.setHorizontalAlignment(CENTER);
      label.setVerticalAlignment(CENTER);
      return label;
    }
  }

  public Component getContentPane() {
    return myContentPane;
  }

  public void setColumnReorderingAllowed(boolean value) {
    JTableHeader header = myTable.getTableHeader();
    if (header != null) {
      header.setReorderingAllowed(value);
    }
  }
}
