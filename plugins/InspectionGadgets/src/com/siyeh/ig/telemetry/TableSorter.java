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
package com.siyeh.ig.telemetry;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.*;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;

/**
 * TableSorter is a decorator for TableModels; adding sorting
 * functionality to a supplied TableModel. TableSorter does
 * not store or copy the data in its TableModel; instead it maintains
 * a map from the row indexes of the view to the row indexes of the
 * model. As requests are made of the sorter (like getValueAt(row, col))
 * they are passed to the underlying model after the row numbers
 * have been translated via the internal mapping array. This way,
 * the TableSorter appears to hold another copy of the table
 * with the rows in a different order.
 * <p/>
 * TableSorter registers itself as a listener to the underlying model,
 * just as the JTable itself would. Events recieved from the model
 * are examined, sometimes manipulated (typically widened), and then
 * passed on to the TableSorter's listeners (typically the JTable).
 * If a change to the model has invalidated the order of TableSorter's
 * rows, a note of this is made and the sorter will resort the
 * rows the next time a value is requested.
 * <p/>
 * When the tableHeader property is set, either by using the
 * setTableHeader() method or the two argument constructor, the
 * table header may be used as a complete UI for TableSorter.
 * The default renderer of the tableHeader is decorated with a renderer
 * that indicates the sorting status of each column. In addition,
 * a mouse listener is installed with the following behavior:
 * <ul>
 * <li>
 * Mouse-click: Clears the sorting status of all other columns
 * and advances the sorting status of that column through three
 * values: {NOT_SORTED, ASCENDING, DESCENDING} (then back to
 * NOT_SORTED again).
 * <li>
 * SHIFT-mouse-click: Clears the sorting status of all other columns
 * and cycles the sorting status of the column through the same
 * three values, in the opposite order: {NOT_SORTED, DESCENDING, ASCENDING}.
 * <li>
 * CONTROL-mouse-click and CONTROL-SHIFT-mouse-click: as above except
 * that the changes to the column do not cancel the statuses of columns
 * that are already sorting - giving a way to initiate a compound
 * sort.
 * </ul>
 * <p/>
 * This is a long overdue rewrite of a class of the same name that
 * first appeared in the swing table demos in 1997.
 *
 * @author Philip Milne
 * @author Brendon McLean
 * @author Dan van Enckevort
 * @author Parwinder Sekhon
 * @version 2.0 02/27/04
 */

public class TableSorter extends AbstractTableModel {

    protected TableModel tableModel = null;

    public static final int DESCENDING = -1;
    public static final int NOT_SORTED = 0;
    public static final int ASCENDING = 1;

    private static final Directive EMPTY_DIRECTIVE = new Directive(-1, NOT_SORTED);

    public static final Comparator<Comparable> COMPARABLE_COMPARATOR = new Comparator<Comparable>() {
        public int compare(Comparable c1, Comparable c2) {
            return c1.compareTo(c2);
        }
    };
    public static final Comparator LEXICAL_COMPARATOR = new Comparator() {
        public int compare(Object o1, Object o2) {
            return o1.toString().compareTo(o2.toString());
        }
    };

    private Row[] viewToModel = null;
    int[] modelToView = null;

    private JTableHeader tableHeader = null;
    private MouseListener mouseListener;
    private TableModelListener tableModelListener;
    private final Map<Class, Comparator> columnComparators = new HashMap();
    List<Directive> sortingColumns = new ArrayList();

    public TableSorter() {
        mouseListener = new MouseHandler();
        tableModelListener = new TableModelHandler();
    }

    public TableSorter(TableModel tableModel) {
        this();
        setTableModel(tableModel);
    }

    public TableSorter(TableModel tableModel, JTableHeader tableHeader) {
        this();
        setTableHeader(tableHeader);
        setTableModel(tableModel);
    }

    void clearSortingState() {
        viewToModel = null;
        modelToView = null;
    }

    public TableModel getTableModel() {
        return tableModel;
    }

    public void setTableModel(TableModel tableModel) {
        if (this.tableModel != null) {
            this.tableModel.removeTableModelListener(tableModelListener);
        }

        this.tableModel = tableModel;
        if (this.tableModel != null) {
            this.tableModel.addTableModelListener(tableModelListener);
        }

        clearSortingState();
        fireTableStructureChanged();
    }

    public JTableHeader getTableHeader() {
        return tableHeader;
    }

    public void setTableHeader(JTableHeader tableHeader) {
        if (this.tableHeader != null) {
            this.tableHeader.removeMouseListener(mouseListener);
            final TableCellRenderer defaultRenderer =
                    this.tableHeader.getDefaultRenderer();
            if (defaultRenderer instanceof SortableHeaderRenderer) {
                final TableCellRenderer tableCellRenderer =
                        ((SortableHeaderRenderer)defaultRenderer).tableCellRenderer;
                this.tableHeader.setDefaultRenderer(tableCellRenderer);
            }
        }
        this.tableHeader = tableHeader;
        if (this.tableHeader != null) {
            this.tableHeader.addMouseListener(mouseListener);
            this.tableHeader.setDefaultRenderer(
                    new SortableHeaderRenderer(this.tableHeader.getDefaultRenderer()));
        }
    }

    public boolean isSorting() {
        return sortingColumns.size() != 0;
    }

    private Directive getDirective(int column) {
        for (Directive directive : sortingColumns) {
            if (directive.column == column) {
                return directive;
            }
        }
        return EMPTY_DIRECTIVE;
    }

    public int getSortingStatus(int column) {
        return getDirective(column).direction;
    }

    private void sortingStatusChanged() {
        clearSortingState();
        fireTableDataChanged();
        if (tableHeader != null) {
            tableHeader.repaint();
        }
    }

    public void setSortingStatus(int column, int status) {
        final Directive directive = getDirective(column);
        if (directive != EMPTY_DIRECTIVE) {
            sortingColumns.remove(directive);
        }
        if (status != NOT_SORTED) {
            sortingColumns.add(new Directive(column, status));
        }
        sortingStatusChanged();
    }

    protected Icon getHeaderRendererIcon(int column, int size) {
        final Directive directive = getDirective(column);
        final int index = sortingColumns.indexOf(directive);
        if (index >= 0 && index == sortingColumns.size() - 1) {
            return new Arrow(directive.direction == DESCENDING, size);
        } else {
            return null;
        }
    }

    void cancelSorting() {
        sortingColumns.clear();
        sortingStatusChanged();
    }

    public void setColumnComparator(Class type, Comparator comparator) {
        if (comparator == null) {
            columnComparators.remove(type);
        } else {
            columnComparators.put(type, comparator);
        }
    }

    protected Comparator getComparator(int column) {
        final Class columnType = tableModel.getColumnClass(column);
        final Comparator comparator = columnComparators.get(columnType);
        if (comparator != null) {
            return comparator;
        }
        if (Comparable.class.isAssignableFrom(columnType)) {
            return COMPARABLE_COMPARATOR;
        }
        return LEXICAL_COMPARATOR;
    }

    private Row[] getViewToModel() {
        if (viewToModel == null) {
            final int tableModelRowCount = tableModel.getRowCount();
            viewToModel = new Row[tableModelRowCount];
            for (int row = 0; row < tableModelRowCount; row++) {
                viewToModel[row] = new Row(row);
            }
            if (isSorting()) {
                Arrays.sort(viewToModel);
            }
        }
        return viewToModel;
    }

    public int modelIndex(int viewIndex) {
        return getViewToModel()[viewIndex].modelIndex;
    }

    int[] getModelToView() {
        if (modelToView == null) {
            final int n = getViewToModel().length;
            modelToView = new int[n];
            for (int i = 0; i < n; i++) {
                modelToView[modelIndex(i)] = i;
            }
        }
        return modelToView;
    }

    // TableModel interface methods

    public int getRowCount() {
        if (tableModel == null) {
            return 0;
        } else {
            return tableModel.getRowCount();
        }
    }

    public int getColumnCount() {
        if (tableModel == null) {
            return 0;
        } else {
            return tableModel.getColumnCount();
        }
    }

    public String getColumnName(int column) {
        return tableModel.getColumnName(column);
    }

    public Class getColumnClass(int column) {
        return tableModel.getColumnClass(column);
    }

    public boolean isCellEditable(int row, int column) {
        return tableModel.isCellEditable(modelIndex(row), column);
    }

    public Object getValueAt(int row, int column) {
        return tableModel.getValueAt(modelIndex(row), column);
    }

    public void setValueAt(Object aValue, int row, int column) {
        tableModel.setValueAt(aValue, modelIndex(row), column);
    }

    // Helper classes

    private class Row implements Comparable {

        int modelIndex;

        Row(int index) {
            modelIndex = index;
        }

        public int compareTo(Object o) {
            final int row1 = modelIndex;
            final int row2 = ((Row) o).modelIndex;

            for (Directive directive : sortingColumns) {
                final int column = directive.column;
                final Object o1 = tableModel.getValueAt(row1, column);
                final Object o2 = tableModel.getValueAt(row2, column);

                final int comparison;
                // Define null less than everything, except null.
                if (o1 == null && o2 == null) {
                    comparison = 0;
                } else if (o1 == null) {
                    comparison = -1;
                } else if (o2 == null) {
                    comparison = 1;
                } else {
                    final Comparator comparator = getComparator(column);
                    comparison = comparator.compare(o1, o2);
                }
                if (comparison != 0) {
                    if (directive.direction == DESCENDING) {
                        return -comparison;
                    } else {
                        return comparison;
                    }
                }
            }
            return 0;
        }
    }

    private class TableModelHandler implements TableModelListener {
        public void tableChanged(TableModelEvent e) {
            // If we're not sorting by anything, just pass the event along.
            if (!isSorting()) {
                clearSortingState();
                fireTableChanged(e);
                return;
            }

            // If the table structure has changed, cancel the sorting; the
            // sorting columns may have been either moved or deleted from
            // the model.
            if (e.getFirstRow() == TableModelEvent.HEADER_ROW) {
                cancelSorting();
                fireTableChanged(e);
                return;
            }

            // Something has happened to the data that may have invalidated the row order.
            clearSortingState();
            fireTableDataChanged();
        }
    }

    private class MouseHandler extends MouseAdapter {
        public void mouseClicked(MouseEvent event) {
            final JTableHeader header = (JTableHeader) event.getSource();
            final TableColumnModel columnModel = header.getColumnModel();
            final int viewColumn = columnModel.getColumnIndexAtX(event.getX());
            final TableColumn column = columnModel.getColumn(viewColumn);
            final int columnIndex = column.getModelIndex();
            if (columnIndex != -1) {
                int status = getSortingStatus(columnIndex);

                // Cycle the sorting states through {NOT_SORTED, ASCENDING, DESCENDING} or
                // {NOT_SORTED, DESCENDING, ASCENDING} depending on whether shift is pressed.
                if (status == ASCENDING) {
                    status = DESCENDING;
                } else {
                    status = ASCENDING;
                }
                setSortingStatus(columnIndex, status);
            }
        }
    }

    private static class Arrow implements Icon {

        private final boolean descending;
        private final int size;

        Arrow(boolean descending, int size) {
            this.descending = descending;
            this.size = size;
        }

        public void paintIcon(Component component, Graphics g, int x, int y) {
            final Color color;
            if (component == null) {
                color = Color.GRAY;
            } else {
                color = component.getBackground();
            }
            final int dx = size - 2;
            final int dy;
            if (descending) {
                dy = dx - 2;
            } else {
                dy = -(dx - 3);
            }
            // Align icon (roughly) with font baseline.
            y += 4 * size / 6;
            if (descending) {
                y += -dy;
            }
            g.translate(x, y);
            g.setColor(color.darker());
            g.fillPolygon(new int[]{dx >> 1, 0, dx}, new int[]{0, dy, dy}, 3);
            g.setColor(color);
            g.translate(-x, -y);
        }

        public int getIconWidth() {
            return size;
        }

        public int getIconHeight() {
            return size;
        }
    }

    private class SortableHeaderRenderer implements TableCellRenderer {

        TableCellRenderer tableCellRenderer;

        SortableHeaderRenderer(TableCellRenderer tableCellRenderer) {
            this.tableCellRenderer = tableCellRenderer;
        }

        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            final Component component =
                    tableCellRenderer.getTableCellRendererComponent(table,
                    value, isSelected, hasFocus, row, column);
            if (component instanceof JLabel) {
                final JLabel label = (JLabel) component;
                label.setHorizontalTextPosition(JLabel.LEADING);
                final int modelColumn = table.convertColumnIndexToModel(column);
                final Font font = label.getFont();
                label.setIcon(getHeaderRendererIcon(modelColumn, font.getSize()));
            }
            return component;
        }
    }

    private static class Directive {

        int column;
        int direction;

        Directive(int column, int direction) {
            this.column = column;
            this.direction = direction;
        }
    }
}
