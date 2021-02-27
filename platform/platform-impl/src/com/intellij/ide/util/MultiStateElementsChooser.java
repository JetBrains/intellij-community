// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.Nls;
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
import java.util.List;
import java.util.*;

public class MultiStateElementsChooser<T, S> extends JPanel implements ComponentWithEmptyText, ComponentWithExpandableItems<TableCell> {
  private final MarkStateDescriptor<T, S> myMarkStateDescriptor;
  private final JBTable myTable;
  private final MyTableModel myTableModel;
  private boolean myColorUnmarkedElements = true;
  private final List<ElementsMarkStateListener<T, S>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Map<T, ElementProperties> myElementToPropertiesMap = new HashMap<>();
  private final Map<T, Boolean> myDisabledMap = new HashMap<>();

  public interface ElementsMarkStateListener<T, S> {
    void elementMarkChanged(T element, S markState);
  }

  public interface MarkStateDescriptor<T, S> {
    @NotNull
    S getDefaultState(@NotNull T element);

    @NotNull
    S getNextState(@NotNull T element, @NotNull S state);

    @Nullable
    S getNextState(@NotNull Map<T, S> elementsWithStates);

    boolean isMarked(@NotNull S state);

    @Nullable
    S getMarkState(@Nullable Object value);

    @Nullable
    TableCellRenderer getMarkRenderer();
  }

  public MultiStateElementsChooser(final boolean elementsCanBeMarked, MarkStateDescriptor<T, S> markStateDescriptor) {
    this(null, null, elementsCanBeMarked, markStateDescriptor);
  }

  public MultiStateElementsChooser(List<T> elements, S markState, MarkStateDescriptor<T, S> markStateDescriptor) {
    this(elements, markState, true, markStateDescriptor);
  }

  private MultiStateElementsChooser(@Nullable List<T> elements,
                                    S markState,
                                    boolean elementsCanBeMarked,
                                    MarkStateDescriptor<T, S> markStateDescriptor) {
    super(new BorderLayout());

    myMarkStateDescriptor = markStateDescriptor;

    myTableModel = new MyTableModel(elementsCanBeMarked);
    myTable = new JBTable(myTableModel);
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(JBUI.emptySize());
    myTable.setTableHeader(null);
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);
    myTable.resetDefaultFocusTraversalKeys();
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTable);
    pane.setPreferredSize(JBUI.size(100, 155));
    TableColumnModel columnModel = myTable.getColumnModel();

    if (elementsCanBeMarked) {
      TableColumn checkMarkColumn = columnModel.getColumn(myTableModel.CHECK_MARK_COLUM_INDEX);
      TableUtil.setupCheckboxColumn(checkMarkColumn, 0);
      TableCellRenderer checkMarkRenderer = myMarkStateDescriptor.getMarkRenderer();
      if (checkMarkRenderer == null) {
        checkMarkRenderer = new CheckMarkColumnCellRenderer(myTable.getDefaultRenderer(Boolean.class));
      }
      checkMarkColumn.setCellRenderer(checkMarkRenderer);
    }
    columnModel.getColumn(myTableModel.ELEMENT_COLUMN_INDEX).setCellRenderer(new MyElementColumnCellRenderer());
    add(pane, BorderLayout.CENTER);

    TableSpeedSearch speedSearch = new TableSpeedSearch(myTable);
    speedSearch.setFilteringMode(true);
    myTable.setRowSorter(new TableRowSorter<>(myTable.getModel()));
    myTable.registerKeyboardAction(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          int[] viewRows = myTable.getSelectedRows();
          int[] modelRows = new int[viewRows.length];
          Map<T, S> selectedElements = new LinkedHashMap<>(viewRows.length);
          for (int i = 0; i < viewRows.length; i++) {
            int viewRow = viewRows[i];
            int modelRow = myTable.convertRowIndexToModel(viewRow);
            T element = myTableModel.getElementAt(modelRow);
            selectedElements.put(element, myTableModel.getElementMarkState(element));
            modelRows[i] = modelRow;
          }
          S nextState = myMarkStateDescriptor.getNextState(selectedElements);
          if (nextState != null) {
            myTableModel.setMarkState(modelRows, nextState);
          }
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);
    setElements(elements, markState);
    installActions(myTable);
  }

  private static void installActions(JTable table) {
    InputMap inputMap = table.getInputMap(WHEN_FOCUSED);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), TableActions.CtrlEnd.ID);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), TableActions.CtrlHome.ID);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, InputEvent.SHIFT_DOWN_MASK), TableActions.CtrlShiftHome.ID);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, InputEvent.SHIFT_DOWN_MASK), TableActions.CtrlShiftEnd.ID);
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

  public void setColorUnmarkedElements(boolean colorUnmarkedElements) {
    myColorUnmarkedElements = colorUnmarkedElements;
  }

  public void addElementsMarkListener(ElementsMarkStateListener<T, S> listener) {
    myListeners.add(listener);
  }

  public void removeElementsMarkListener(ElementsMarkStateListener<T, S> listener) {
    myListeners.remove(listener);
  }

  public void addListSelectionListener(ListSelectionListener listener) {
    myTable.getSelectionModel().addListSelectionListener(listener);
  }
  public void removeListSelectionListener(ListSelectionListener listener) {
    myTable.getSelectionModel().removeListSelectionListener(listener);
  }

  public void addElement(T element, final S markState) {
    addElement(element, markState, element instanceof ElementProperties ? (ElementProperties)element : null);
  }

  /**
   * Gets element mark state
   * @param element an element to test
   * @return state of element
   */
  public S getElementMarkState(T element) {
    return myTableModel.getElementMarkState(element);
  }

  /**
   * Update element mark state
   * @param element an element to test
   * @param markState a new value of mark state
   */
  public void setElementMarkState(T element, S markState) {
    final int elementRow = myTableModel.getElementRow(element);
    myTableModel.setMarkState(elementRow, markState);
  }


  public void removeElement(T element) {
    int modelRow = myTableModel.getElementRow(element);
    if (modelRow < 0) {
      return; // no such element
    }
    int viewRow = myTable.convertRowIndexToView(modelRow);
    boolean wasSelected = myTable.getSelectionModel().isSelectedIndex(viewRow);

    myTableModel.removeElement(element);
    myElementToPropertiesMap.remove(element);

    if (wasSelected) {
      final int rowCount = myTableModel.getRowCount();
      if (rowCount > 0) {
        selectRow(viewRow % rowCount);
      }
      else {
        myTable.getSelectionModel().clearSelection();
      }
    }
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTable, true));
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
    int modelRow = myTableModel.getElementRow(element);
    if (modelRow < 0 || modelRow == newRow || newRow < 0 || newRow >= myTableModel.getRowCount()) {
      return;
    }
    int viewRow = myTable.convertRowIndexToView(modelRow);
    boolean wasSelected = myTable.getSelectionModel().isSelectedIndex(viewRow);
    myTableModel.changeElementRow(element, newRow);
    if (wasSelected) {
      selectRow(newRow);
    }
  }

  public interface ElementProperties {
    @Nullable
    default Icon getIcon() {
      return null;
    }
    @Nullable
    default Color getColor() {
      return null;
    }
    @Nullable
    default @Nls String getLocation() {
      return null;
    }
  }

  public void addElement(T element, final S markState, ElementProperties elementProperties) {
    myTableModel.addElement(element, markState);
    myElementToPropertiesMap.put(element, elementProperties);
    selectRow(myTableModel.getRowCount() - 1);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTable, true));
  }

  public void setElementProperties(T element, ElementProperties properties) {
    myElementToPropertiesMap.put(element, properties);
  }

  public void setElements(List<? extends T> elements, S markState) {
    myTableModel.clear();
    myTableModel.addElements(elements, markState);
  }

  @Nullable
  public T getSelectedElement() {
    int viewRow = getSelectedElementRow();
    int modelRow = myTable.convertRowIndexToModel(viewRow);
    return modelRow < 0? null : myTableModel.getElementAt(modelRow);
  }

  public int getSelectedElementRow() {
    return myTable.getSelectedRow();
  }

  @NotNull
  public List<T> getSelectedElements() {
    final List<T> elements = new ArrayList<>();
    int[] viewRows = myTable.getSelectedRows();
    for (int viewRow : viewRows) {
      if (viewRow < 0) {
        continue;
      }
      int modelRow = myTable.convertRowIndexToModel(viewRow);
      elements.add(myTableModel.getElementAt(modelRow));
    }
    return elements;
  }

  public void selectElements(Collection<? extends T> elements) {
    if (elements.isEmpty()) {
      myTable.clearSelection();
      return;
    }
    int[] rows = new int[elements.size()];
    int index = 0;
    for (T element : elements) {
      rows[index++] = myTable.convertRowIndexToView(myTableModel.getElementRow(element));
    }
    TableUtil.selectRows(myTable, rows);
    TableUtil.scrollSelectionToVisible(myTable);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTable, true));
  }

  public void markElements(Collection<? extends T> elements, S markState) {
    int[] rows = new int[elements.size()];
    int index = 0;
    for (final T element : elements) {
      rows[index++] = myTableModel.getElementRow(element);
    }
    myTableModel.setMarkState(rows, markState);
  }

  @NotNull
  public Map<T, S> getElementMarkStates() {
    final int count = myTableModel.getRowCount();
    Map<T, S> elements = new LinkedHashMap<>();
    for (int idx = 0; idx < count; idx++) {
      final T element = myTableModel.getElementAt(idx);
      elements.put(element, myTableModel.getElementMarkState(idx));
    }
    return elements;
  }

  public void sort(Comparator<? super T> comparator) {
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

  public void setAllElementsMarked(S markState) {
    final int[] rows = new int[myTableModel.getRowCount()];
    for (int idx = 0; idx < rows.length; idx++) {
      rows[idx] = idx;
    }
    myTableModel.setMarkState(rows, markState);
  }

  private void notifyElementMarked(T element, S markState) {
    for (ElementsMarkStateListener<T, S> listener : myListeners) {
      listener.elementMarkChanged(element, markState);
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
    private final List<T> myElements = new ArrayList<>();
    private final Map<T, S> myMarkedMap = new HashMap<>();
    public final int CHECK_MARK_COLUM_INDEX;
    public final int ELEMENT_COLUMN_INDEX;
    private final boolean myElementsCanBeMarked;

    MyTableModel(final boolean elementsCanBeMarked) {
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

    public void sort(Comparator<? super T> comparator) {
      myElements.sort(comparator);
      fireTableDataChanged();
    }

    public T getElementAt(int index) {
      return myElements.get(index);
    }

    public S getElementMarkState(int index) {
      final T element = myElements.get(index);
      return getElementMarkState(element);
    }

    public S getElementMarkState(T element) {
      return myMarkedMap.get(element);
    }

    private void addElement(T element, S markState) {
      myElements.add(element);
      myMarkedMap.put(element, notNullMarkState(element, markState));
      int row = myElements.size() - 1;
      fireTableRowsInserted(row, row);
    }

    private void addElements(@Nullable List<? extends T> elements, S markState) {
      if (elements == null || elements.isEmpty()) {
        return;
      }
      for (final T element : elements) {
        myElements.add(element);
        myMarkedMap.put(element, notNullMarkState(element, markState));
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
        return getElementMarkState(element);
      }
      return null;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex == CHECK_MARK_COLUM_INDEX) {
        S nextState = myMarkStateDescriptor.getMarkState(aValue);
        if (nextState == null) {
          T element = myTableModel.getElementAt(rowIndex);
          S currentState = myTableModel.getElementMarkState(rowIndex);
          nextState = myMarkStateDescriptor.getNextState(element, currentState);
        }
        setMarkState(rowIndex, nextState);
      }
    }

    private void setMarkState(int rowIndex, final S markState) {
      final T element = myElements.get(rowIndex);
      final S newValue = notNullMarkState(element, markState);
      final S prevValue = myMarkedMap.put(element, newValue);
      fireTableRowsUpdated(rowIndex, rowIndex);
      if (!newValue.equals(prevValue)) {
        notifyElementMarked(element, newValue);
      }
    }

    private void setMarkState(int[] rows, final S markState) {
      if (rows == null || rows.length == 0) {
        return;
      }
      int firstRow = Integer.MAX_VALUE;
      int lastRow = Integer.MIN_VALUE;
      for (final int row : rows) {
        final T element = myElements.get(row);
        final S newValue = notNullMarkState(element, markState);
        final S prevValue = myMarkedMap.put(element, newValue);
        if (!newValue.equals(prevValue)) {
          notifyElementMarked(element, newValue);
        }
        firstRow = Math.min(firstRow, row);
        lastRow = Math.max(lastRow, row);
      }
      fireTableRowsUpdated(firstRow, lastRow);
    }

    @NotNull
    private S notNullMarkState(T element, S markState) {
      return markState != null ? markState : myMarkStateDescriptor.getDefaultState(element);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
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
      @SuppressWarnings("unchecked")
      final T o = (T)getValueAt(rowIndex, ELEMENT_COLUMN_INDEX);
      return myDisabledMap.get(o) == null;
    }

    public void clear() {
      myElements.clear();
      myMarkedMap.clear();
      fireTableDataChanged();
    }
  }

  protected @NlsContexts.ListItem String getItemText(@NotNull T value) {
    @NlsSafe String text = value.toString();
    return text;
  }

  @Nullable
  protected Icon getItemIcon(@NotNull T value) {
    return null;
  }


  private class MyElementColumnCellRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
      @SuppressWarnings("unchecked") T item = (T)value;
      String text = item == null ? "" : getItemText(item);
      SpeedSearchUtil.appendFragmentsForSpeedSearch(
        table, text, SimpleTextAttributes.REGULAR_ATTRIBUTES, selected, this);

      ElementProperties properties = myElementToPropertiesMap.get(item);

      if (properties != null) {
        String location = properties.getLocation();
        if (StringUtil.isNotEmpty(location)) {
          append(" (" + location + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      }

      setTransparentIconBackground(true);
      Icon icon = properties != null ? properties.getIcon() : item != null ? getItemIcon(item) : null;
      if (icon != null) {
        setIcon(icon);
      }

      setForeground(properties != null && properties.getColor() != null ?
                    properties.getColor() :
                    selected ? table.getSelectionForeground() : table.getForeground());

      @SuppressWarnings("unchecked") MyTableModel model = (MyTableModel)table.getModel();
      setEnabled(selected || (MultiStateElementsChooser.this.isEnabled() &&
                              (!myColorUnmarkedElements || myMarkStateDescriptor.isMarked(model.getElementMarkState(item)))));
    }
  }

  private class CheckMarkColumnCellRenderer implements TableCellRenderer {
    private final TableCellRenderer myDelegate;

    CheckMarkColumnCellRenderer(TableCellRenderer delegate) {
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
