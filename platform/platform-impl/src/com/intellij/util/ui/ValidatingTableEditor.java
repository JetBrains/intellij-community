// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
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
import java.util.function.Supplier;

public abstract class ValidatingTableEditor<Item> implements ComponentWithEmptyText {
  private static final Icon WARNING_ICON = UIUtil.getBalloonWarningIcon();
  private static final Icon EMPTY_ICON = IconManager.getInstance().createEmptyIcon(WARNING_ICON);
  private static final @NonNls String REMOVE_KEY = "REMOVE_SELECTED";

  public interface RowHeightProvider {
    int getRowHeight();
  }

  public interface Fix extends Runnable {
    @NlsContexts.LinkLabel String getTitle();
  }

  private final class ColumnInfoWrapper extends ColumnInfo<Item, Object> {
    private final ColumnInfo<Item, Object> myDelegate;

    ColumnInfoWrapper(ColumnInfo<Item, Object> delegate) {
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
  private final AnAction myRemoveButton;
  private JLabel myMessageLabel;
  private HoverHyperlinkLabel myFixLink;
  private JPanel myTablePanel;
  private final List<String> myWarnings = new ArrayList<>();
  private Fix myFixRunnable;

  protected abstract Item cloneOf(Item item);

  protected @Nullable Pair<String, Fix> validate(List<? extends Item> current, List<? super String> warnings) {
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

  protected @Nullable String validate(Item item) {
    return null;
  }

  protected abstract @Nullable Item createItem();

  private final class IconColumn extends ColumnInfo<Item, Object> implements RowHeightProvider {
    IconColumn() {
      super(" ");
    }

    @Override
    public String valueOf(Item item) {
      return null;
    }

    @Override
    public int getWidth(JTable table) {
      return WARNING_ICON.getIconWidth() + 2;
    }

    @Override
    public int getRowHeight() {
      return WARNING_ICON.getIconHeight();
    }

    @Override
    public TableCellRenderer getRenderer(final Item item) {
      return new WarningIconCellRenderer(() -> myWarnings.get(doGetItems().indexOf(item)));
    }
  }

  @Override
  public @NotNull StatusText getEmptyText() {
    return myTable.getEmptyText();
  }

  public void setShowGrid(boolean v) {
    myTable.setShowGrid(v);
  }

  private void createUIComponents() {
    myTable = new ChangesTrackingTableView<>() {
      @Override
      protected void onCellValueChanged(int row, int column, Object value) {
        Item original = getItems().get(row);
        Item override = cloneOf(original);
        ColumnInfo<Item, Object> columnInfo = getTableModel().getColumnInfos()[column];
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

  protected ValidatingTableEditor(AnAction @Nullable ... extraButtons) {
    ToolbarDecorator decorator =
      ToolbarDecorator.createDecorator(myTable).disableRemoveAction().disableUpAction().disableDownAction();
    decorator.setAddAction(new AnActionButtonRunnable() {

      @Override
      public void run(AnActionButton anActionButton) {
        addItem();
      }
    });


    myRemoveButton = new DumbAwareAction(ApplicationBundle.message("button.remove"), null, IconUtil.getRemoveIcon()) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(myTable.getSelectedRow() != -1);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        removeSelected();
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    };
    myRemoveButton.setShortcutSet(CustomShortcutSet.fromString("alt DELETE"));
    decorator.addExtraAction(myRemoveButton);

    if (extraButtons != null) {
      for (AnAction extraButton : extraButtons) {
        decorator.addExtraAction(extraButton);
      }
    }

    myTablePanel.add(decorator.createPanel(), BorderLayout.CENTER);

    myTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), REMOVE_KEY);
    myTable.getActionMap().put(REMOVE_KEY, new AbstractAction() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        removeSelected();
      }
    });

    myFixLink.addHyperlinkListener(new HyperlinkListener() {
      @Override
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

  public @Nullable List<Item> getSelectedItems() {
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

  public void setModel(ColumnInfo<Item, Object>[] valueColumns, List<? extends Item> items) {
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
      ColumnInfo column = ((ListTableModel<?>)myTable.getModel()).getColumnInfos()[myTable.getEditingColumn()];
      ((ColumnInfoWrapper)column).myDelegate.setValue(items.get(myTable.getEditingRow()), value);
    }
    return items;
  }

  private void setItems(List<? extends Item> items) {
    if (items.isEmpty()) {
      getTableModel().setItems(new ArrayList<>());
      myWarnings.clear();
    }
    else {
      myWarnings.clear();
      for (Item item : items) {
        myWarnings.add(null);
      }
      getTableModel().setItems(new ArrayList<>(items));
    }
  }

  public void setTableHeader(JTableHeader header) {
    myTable.setTableHeader(header);
  }

  public void updateMessage(int index, @Nullable Item override) {
    List<Item> current = new ArrayList<>(doGetItems());
    if (override != null) {
      current.set(index, override);
    }

    displayMessageAndFix(validate(current, myWarnings));
    myTable.repaint();
  }

  protected void displayMessageAndFix(@Nullable Pair<@NlsContexts.DialogMessage String, Fix> messageAndFix) {
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


  private static final class WarningIconCellRenderer extends DefaultTableCellRenderer {
    private final Supplier<@Nullable @NlsContexts.HintText String> warningProvider;

    WarningIconCellRenderer(Supplier<@Nullable @NlsContexts.HintText String> warningProvider) {
      this.warningProvider = warningProvider;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      String message = warningProvider.get();
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
