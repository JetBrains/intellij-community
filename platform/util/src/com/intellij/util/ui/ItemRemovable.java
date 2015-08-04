/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
