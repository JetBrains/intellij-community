/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.JTableHeader;

public class TelemetryDisplay {

    private final JTable table;
    private final JScrollPane scrollPane;
    private final TableSorter model;

    public TelemetryDisplay(InspectionGadgetsTelemetry telemetry){
        model = new TableSorter(new TelemetryTableModel(telemetry));
        table = new JBTable(model);
        final JTableHeader tableHeader = table.getTableHeader();
        model.setTableHeader(tableHeader);
        scrollPane = ScrollPaneFactory.createScrollPane(table);
    }

    public JComponent getContentPane(){
        return scrollPane;
    }

    public void update(){
        table.setModel(model);
        table.repaint();
    }
}
