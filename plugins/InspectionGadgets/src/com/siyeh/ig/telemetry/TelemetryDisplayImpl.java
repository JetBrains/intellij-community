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
