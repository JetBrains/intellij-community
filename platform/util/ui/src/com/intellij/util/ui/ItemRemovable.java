// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

/**
 * Implement this interface in {@link javax.swing.table.TableModel}'s implementation if it supports removal of items. This will allow using
 * utility methods to remove items from the table.
 *
 * @see com.intellij.ui.TableUtil#removeSelectedItems(javax.swing.JTable)
 */
public interface ItemRemovable {
  /**
   * Remove row with index {@code idx} and fire {@link javax.swing.event.TableModelEvent#DELETE DELETE} event (e.g. by calling
   * {@link javax.swing.table.AbstractTableModel#fireTableRowsDeleted(int, int)})
   */
  void removeRow(int idx);
}
