/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.table.JTableHeader;

public class TelemetryDisplayImpl implements TelemetryDisplay{

    private JTable table;
    private JBScrollPane scrollPane;
    private final TableSorter model;

    public TelemetryDisplayImpl(InspectionGadgetsTelemetry telemetry){
        super();
        model = new TableSorter(new TelemetryTableModel(telemetry));
        table.setModel(model);
		final JTableHeader tableHeader = table.getTableHeader();
		model.setTableHeader(tableHeader);
    }

    public JComponent getContentPane(){
        return scrollPane;
    }

    public void update(){
        table.setModel(model);
        table.repaint();
    }
}
