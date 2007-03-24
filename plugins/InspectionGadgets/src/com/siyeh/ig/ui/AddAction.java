/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ig.ui;

import com.siyeh.InspectionGadgetsBundle;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.table.TableCellEditor;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;

public class AddAction extends AbstractAction {

    final IGTable table;

    public AddAction(IGTable table) {
        this.table = table;
        putValue(Action.NAME,
                InspectionGadgetsBundle.message("button.add"));
    }

    public void actionPerformed(ActionEvent e) {
        final ListWrappingTableModel tableModel = table.getModel();
        tableModel.addRow();
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                final int size = tableModel.getRowCount();
                final Rectangle rect =
                        table.getCellRect(size, 0, true);
                table.scrollRectToVisible(rect);
                table.editCellAt(size, 0);
                final TableCellEditor editor =
                        table.getCellEditor();
                final Component component =
                        editor.getTableCellEditorComponent(table,
                                null, true, size, 0);
                component.requestFocus();
            }
        });
    }
}