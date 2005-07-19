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

import javax.swing.*;
import javax.swing.event.TableModelEvent;

public class TelemetryDisplayImpl implements TelemetryDisplay{
    private JTable table;
    private JScrollPane scrollPane;
    private final TelemetryTableModel model;

    public TelemetryDisplayImpl(InspectionGadgetsTelemetry telemetry){
        super();
        model = new TelemetryTableModel(telemetry);
        table.setModel(model);
    }

    public JComponent getContentPane(){
        return scrollPane;
    }

    public void update(){
        table.tableChanged(new TableModelEvent(model,
                                                    TableModelEvent.HEADER_ROW));
        table.tableChanged(new TableModelEvent(model));
        table.repaint();
    }

}
