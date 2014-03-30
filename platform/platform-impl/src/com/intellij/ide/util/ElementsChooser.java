/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.util;

import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * @see ChooseElementsDialog
 */
public class ElementsChooser<T> extends JPanel implements ComponentWithEmptyText, ComponentWithExpandableItems<TableCell> {
  private JBTable myTable = null;
  private MyTableModel myTableModel = null;
  private boolean myColorUnmarkedElements = true;
  private final List<ElementsMarkListener<T>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Map<T,ElementProperties> myElementToPropertiesMap = new HashMap<T, ElementProperties>();
  private final Map<T, Boolean> myDisabledMap = new HashMap<T, Boolean>();

  public interface ElementsMarkListener<T> {
    void elementMarkChanged(T element, boolean isMarked);
  }

  public ElementsChooser(final boolean elementsCanBeMarked) {
    this(null, false, elementsCanBeMarked);
  }

  public ElementsChooser(List<T> elements, boolean marked) {
    this(elements, marked, true);
  }

  private ElementsChooser(@Nullable List<T> elements, boolean marked, boolean elementsCanBeMarked) {
    super(new BorderLayout());

    myTableModel = new MyTableModel(elementsCanBeMarked);
    myTable = new Table(myTableModel);
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setTableHeader(null);
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTable);
    pane.setPreferredSize(new Dimension(100, 155));
    TableColumnModel columnModel = myTable.getColumnModel();

    if (elementsCanBeMarked) {
      TableColumn checkMarkColumn = columnModel.getColumn(myTableModel.CHECK_MARK_COLUM_INDEX);
      TableUtil.setupCheckboxColumn(checkMarkColumn);
      checkMarkColumn.setCellRenderer(new CheckMarkColumnCellRenderer(myTable.getDefaultRenderer(Boolean.class)));
    }
    columnModel.getColumn(myTableModel.ELEMENT_COLUMN_INDEX).setCellRenderer(new MyElementColumnCellRenderer());

    add(pane, BorderLayout.CENTER);
    myTable.registerKeyboardAction(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final int[] selectedRows = myTable.getSelectedRows();
          boolean currentlyMarked = true;
          for (int selectedRow : selectedRows) {
            currentlyMarked = myTableModel.isElementMarked(selectedRow);
            if (!currentlyMarked) {
              break;
            }
          }
          myTableModel.setMarked(selectedRows, !currentlyMarked);
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
      JComponent.WHEN_FOCUSED
    );

    final SpeedSearchBase<JBTable> speedSearch = new SpeedSearchBase<JBTable>(myTable) {
      @Override
      public int getSelectedIndex() {
        return myTable.getSelectedRow();
      }

      @Override
      protected int convertIndexToModel(int viewIndex) {
        return myTable.convertRowIndexToModel(viewIndex);
      }

      @Override
      public Object[] getAllElements() {
        final int count = myTableModel.getRowCount();
        Object[] elements = new Object[count];
        for (int idx = 0; idx < count; idx++) {
          elements[idx] = myTableModel.getElementAt(idx);
        }
        return elements;
      }

      @Override
      public String getElementText(Object element) {
        return getItemText((T)element);
      }

      @Override
      public void selectElement(Object element, String selectedText) {
        final int count = myTableModel.getRowCount();
        for (int row = 0; row < count; row++) {
          if (element.equals(myTableModel.getElementAt(row))) {
            final int viewRow = myTable.convertRowIndexToView(row);
            myTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            TableUtil.scrollSelectionToVisible(myTable);
            break;
          }
        }
      }
    };
    speedSearch.setComparator(new SpeedSearchComparator(false));
    setElements(elements, marked);
    installActions(myTable);
  }

  private static void installActions(JTable table) {
    InputMap inputMap = table.getInputMap(WHEN_FOCUSED);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), "selectLastRow");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "selectFirstRow");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, InputEvent.SHIFT_DOWN_MASK), "selectFirstRowExtendSelection");
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, InputEvent.SHIFT_DOWN_MASK), "selectLastRowExtendSelection");
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myTable.getEmptyText();
  }

  @NotNull
  @Override
  public ExpandableItemsHandler<TableCell> getExpandableItemsHandler() {
    return myTable.getExpandableItemsHandler();
  }

  @Override
  public void setExpandableItemsEnabled(boolean enabled) {
    myTable.setExpandableItemsEnabled(enabled);
  }

  public void setSingleSelectionMode() {
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  public void refresh() {
    myTableModel.fireTableDataChanged();
  }

  public void refresh(T element) {
    final int row = myTableModel.getElementRow(element);
    if (row >= 0) {
      myTableModel.fireTableRowsUpdated(row, row);
    }
  }

  private int[] mySavedSelection = null;
  public void saveSelection() {
    mySavedSelection = myTable.getSelectedRows();
  }

  public void restoreSelection() {
    if (mySavedSelection != null) {
      TableUtil.selectRows(myTable, mySavedSelection);
      mySavedSelection = null;
    }
  }

  public boolean isColorUnmarkedElements() {
    return myColorUnmarkedElements;
  }

  public void setColorUnmarkedElements(boolean colorUnmarkedElements) {
    myColorUnmarkedElements = colorUnmarkedElements;
  }

  public void addElementsMarkListener(ElementsMarkListener<T> listener) {
    myListeners.add(listener);
  }

  public void removeElementsMarkListener(ElementsMarkListener<T> listener) {
    myListeners.remove(listener);
  }

  public void addListSelectionListener(ListSelectionListener listener) {
    myTable.getSelectionModel().addListSelectionListener(listener);
  }
  public void removeListSelectionListener(ListSelectionListener listener) {
    myTable.getSelectionModel().removeListSelectionListener(listener);
  }

  public void addElement(T element, final boolean isMarked) {
    addElement(element, isMarked, element instanceof ElementProperties ? (ElementProperties)element : null);
  }

  /**
   * Check if element is marked
   * @param element an element to test
   * @return true if element is marked
   */
  public boolean isElementMarked(T element) {
    final int elementRow = myTableModel.getElementRow(element);
    return myTableModel.isElementMarked(elementRow);
  }

  /**
   * Check if element is marked
   * @param element an element to test
   * @param marked a new value of mark.
   */
  public void setElementMarked(T element, boolean marked) {
    final int elementRow = myTableModel.getElementRow(element);
    myTableModel.setMarked(elementRow, marked);
  }


  public void removeElement(T element) {
    final int elementRow = myTableModel.getElementRow(element);
    if (elementRow < 0) {
      return; // no such element
    }
    final boolean wasSelected = myTable.getSelectionModel().isSelectedIndex(elementRow);

    myTableModel.removeElement(element);
    myElementToPropertiesMap.remove(element);

    if (wasSelected) {
      final int rowCount = myTableModel.getRowCount();
      if (rowCount > 0) {
        selectRow(elementRow % rowCount);
      }
      else {
        myTable.getSelectionModel().clearSelection();
      }
    }
    myTable.requestFocus();
  }

  public void removeAllElements() {
    myTableModel.removeAllElements();
    myTable.getSelectionModel().clearSelection();
  }

  private void selectRow(final int row) {
    myTable.getSelectionModel().setSelectionInterval(row, row);
    myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));
  }

  public void moveElement(T element, int newRow) {
    final int elementRow = myTableModel.getElementRow(element);
    if (elementRow < 0 || elementRow == newRow || newRow < 0 || newRow >= myTableModel.getRowCount()) {
      return;
    }
    final boolean wasSelected = myTable.getSelectionModel().isSelectedIndex(elementRow);
    myTableModel.changeElementRow(element, newRow);
    if (wasSelected) {
      selectRow(newRow);
    }
  }

  public interface ElementProperties {
    @Nullable
    Icon getIcon();
    @Nullable
    Color getColor();
  }
  public void addElement(T element, final boolean isMarked, ElementProperties elementProperties) {
    myTableModel.addElement(element, isMarked);
    myElementToPropertiesMap.put(element, elementProperties);
    selectRow(myTableModel.getRowCount() - 1);
    myTable.requestFocus();
  }

  public void setElementProperties(T element, ElementProperties properties) {
    myElementToPropertiesMap.put(element, properties);
  }

  public void setElements(List<T> elements, boolean marked) {
    myTableModel.clear();
    myTableModel.addElements(elements, marked);
  }

  @Nullable
  public T getSelectedElement() {
    final int selectedRow = getSelectedElementRow();
    return selectedRow < 0? null : myTableModel.getElementAt(selectedRow);
  }

  public int getSelectedElementRow() {
    return myTable.getSelectedRow();
  }

  @NotNull
  public List<T> getSelectedElements() {
    final List<T> elements = new ArrayList<T>();
    final int[] selectedRows = myTable.getSelectedRows();
    for (int selectedRow : selectedRows) {
      if (selectedRow < 0) {
        continue;
      }
      elements.add(myTableModel.getElementAt(selectedRow));
    }
    return elements;
  }

  public void selectElements(Collection<? extends T> elements) {
    if (elements.isEmpty()) {
      myTable.clearSelection();
      return;
    }
    final int[] rows = getElementsRows(elements);
    TableUtil.selectRows(myTable, rows);
    TableUtil.scrollSelectionToVisible(myTable);
    myTable.requestFocus();
  }

  private int[] getElementsRows(final Collection<? extends T> elements) {
    final int[] rows = new int[elements.size()];
    int index = 0;
    for (final T element : elements) {
      rows[index++] = myTable.convertRowIndexToView(myTableModel.getElementRow(element));
    }
    return rows;
  }

  public void markElements(Collection<T> elements) {
    myTableModel.setMarked(getElementsRows(elements), true);
  }

  @NotNull
  public List<T> getMarkedElements() {
    final int count = myTableModel.getRowCount();
    List<T> elements = new ArrayList<T>();
    for (int idx = 0; idx < count; idx++) {
      final T element = myTableModel.getElementAt(idx);
      if (myTableModel.isElementMarked(idx)) {
        elements.add(element);
      }
    }
    return elements;
  }

  public void sort(Comparator<T> comparator) {
    myTableModel.sort(comparator);
  }
  
  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myTable.setRowSelectionAllowed(enabled);
    myTableModel.fireTableDataChanged();
  }

  public void stopEditing() {
    TableCellEditor editor = myTable.getCellEditor();
    if (editor != null) {
      editor.stopCellEditing();
    }
  }

  public JComponent getComponent() {
    return myTable;
  }

  public void invertSelection() {
    final int count = getElementCount();
    for (int i = 0; i < count; i++) {
      T type = getElementAt(i);
      setElementMarked(type, !isElementMarked(type));
    }
  }

  public void setAllElementsMarked(boolean marked) {
    final int[] rows = new int[myTableModel.getRowCount()];
    for (int idx = 0; idx < rows.length; idx++) {
      rows[idx] = idx;
    }
    myTableModel.setMarked(rows, marked);
  }

  private void notifyElementMarked(T element, boolean isMarked) {
    for (ElementsMarkListener<T> listener : myListeners) {
      listener.elementMarkChanged(element, isMarked);
    }
  }

  public void clear() {
    myTableModel.clear();
    myElementToPropertiesMap.clear();
  }

  public int getElementCount() {
    return myTableModel.getRowCount();
  }

  public T getElementAt(int row) {
    return myTableModel.getElementAt(row);
  }

  public void disableElement(T element) {
    myDisabledMap.put(element, Boolean.TRUE);
  }
  
  private final class MyTableModel extends AbstractTableModel {
    private final List<T> myElements = new ArrayList<T>();
    private final Map<T, Boolean> myMarkedMap = new HashMap<T, Boolean>();
    public final int CHECK_MARK_COLUM_INDEX;
    public final int ELEMENT_COLUMN_INDEX;
    private final boolean myElementsCanBeMarked;

    public MyTableModel(final boolean elementsCanBeMarked) {
      myElementsCanBeMarked = elementsCanBeMarked;
      if (elementsCanBeMarked) {
        CHECK_MARK_COLUM_INDEX = 0;
        ELEMENT_COLUMN_INDEX = 1;
      }
      else {
        CHECK_MARK_COLUM_INDEX = -1;
        ELEMENT_COLUMN_INDEX = 0;
      }
    }
    
    public void sort(Comparator<T> comparator) {
      Collections.sort(myElements, comparator);
      fireTableDataChanged();
    }
    
    public T getElementAt(int index) {
      return myElements.get(index);
    }

    public boolean isElementMarked(int index) {
      final T element = myElements.get(index);
      final Boolean isMarked = myMarkedMap.get(element);
      return isMarked.booleanValue();
    }

    private void addElement(T element, boolean isMarked) {
      myElements.add(element);
      myMarkedMap.put(element, isMarked? Boolean.TRUE : Boolean.FALSE);
      int row = myElements.size() - 1;
      fireTableRowsInserted(row, row);
    }

    private void addElements(@Nullable List<T> elements, boolean isMarked) {
      if (elements == null || elements.isEmpty()) {
        return;
      }
      for (final T element : elements) {
        myElements.add(element);
        myMarkedMap.put(element, isMarked ? Boolean.TRUE : Boolean.FALSE);
      }
      fireTableRowsInserted(myElements.size() - elements.size(), myElements.size() - 1);
    }

    public void removeElement(T element) {
      final boolean reallyRemoved = myElements.remove(element);
      if (reallyRemoved) {
        myMarkedMap.remove(element);
        fireTableDataChanged();
      }
    }

    public void changeElementRow(T element, int row) {
      final boolean reallyRemoved = myElements.remove(element);
      if (reallyRemoved) {
        myElements.add(row, element);
        fireTableDataChanged();
      }
    }

    public int getElementRow(T element) {
      return myElements.indexOf(element);
    }

    public void removeAllElements() {
      myElements.clear();
      fireTableDataChanged();
    }

    public void removeRows(int[] rows) {
      final List<T> toRemove = new ArrayList<T>();
      for (int row : rows) {
        final T element = myElements.get(row);
        toRemove.add(element);
        myMarkedMap.remove(element);
      }
      myElements.removeAll(toRemove);
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return myElements.size();
    }

    @Override
    public int getColumnCount() {
      return myElementsCanBeMarked? 2 : 1;
    }

    @Override
    @Nullable
    public Object getValueAt(int rowIndex, int columnIndex) {
      T element = myElements.get(rowIndex);
      if (columnIndex == ELEMENT_COLUMN_INDEX) {
        return element;
      }
      if (columnIndex == CHECK_MARK_COLUM_INDEX) {
        return myMarkedMap.get(element);
      }
      return null;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex == CHECK_MARK_COLUM_INDEX) {
        setMarked(rowIndex, ((Boolean)aValue).booleanValue());
      }
    }

    private void setMarked(int rowIndex, final boolean marked) {
      final T element = myElements.get(rowIndex);
      final Boolean newValue = marked? Boolean.TRUE : Boolean.FALSE;
      final Boolean prevValue = myMarkedMap.put(element, newValue);
      fireTableRowsUpdated(rowIndex, rowIndex);
      if (!newValue.equals(prevValue)) {
        notifyElementMarked(element, marked);
      }
    }

    private void setMarked(int[] rows, final boolean marked) {
      if (rows == null || rows.length == 0) {
        return;
      }
      int firstRow = Integer.MAX_VALUE;
      int lastRow = Integer.MIN_VALUE;
      final Boolean newValue = marked? Boolean.TRUE : Boolean.FALSE;
      for (final int row : rows) {
        final T element = myElements.get(row);
        final Boolean prevValue = myMarkedMap.put(element, newValue);
        if (!newValue.equals(prevValue)) {
          notifyElementMarked(element, newValue.booleanValue());
        }
        firstRow = Math.min(firstRow, row);
        lastRow = Math.max(lastRow, row);
      }
      fireTableRowsUpdated(firstRow, lastRow);
    }

    @Override
    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECK_MARK_COLUM_INDEX) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (!isEnabled() || columnIndex != CHECK_MARK_COLUM_INDEX) {
        return false;
      }
      final T o = (T)getValueAt(rowIndex, ELEMENT_COLUMN_INDEX);
      return myDisabledMap.get(o) == null; 
    }

    public void clear() {
      myElements.clear();
      myMarkedMap.clear();
      fireTableDataChanged();
    }
  }

  protected String getItemText(@NotNull T value) {
    return value.toString();
  }

  @Nullable
  protected Icon getItemIcon(@NotNull T value) {
    return null;
  }

  private class MyElementColumnCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      final Color color = UIUtil.getTableFocusCellBackground();
      Component component;
      T t = (T)value;
      try {
        UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, table.getSelectionBackground());
        component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setText(t != null ? getItemText(t) : "");
        if (component instanceof JLabel) {
          ((JLabel)component).setBorder(noFocusBorder);
        }
      }
      finally {
        UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, color);
      }
      final MyTableModel model = (MyTableModel)table.getModel();
      component.setEnabled(ElementsChooser.this.isEnabled() && (!myColorUnmarkedElements || model.isElementMarked(row)));
      final ElementProperties properties = myElementToPropertiesMap.get(t);
      if (component instanceof JLabel) {
        final Icon icon = properties != null ? properties.getIcon() : t != null ? getItemIcon(t) : null;
        JLabel label = (JLabel)component;
        label.setIcon(icon);
        label.setDisabledIcon(icon);
      }
      component.setForeground(properties != null && properties.getColor() != null ?
                              properties.getColor() :
                              isSelected ? table.getSelectionForeground() : table.getForeground());
      return component;
    }
  }

  private class CheckMarkColumnCellRenderer implements TableCellRenderer {
    private final TableCellRenderer myDelegate;

    public CheckMarkColumnCellRenderer(TableCellRenderer delegate) {
      myDelegate = delegate;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = myDelegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      component.setEnabled(isEnabled());
      if (component instanceof JComponent) {
        ((JComponent)component).setBorder(null);
      }
      return component;
    }
  }
}
