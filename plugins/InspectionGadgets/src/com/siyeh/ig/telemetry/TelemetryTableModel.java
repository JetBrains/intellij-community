package com.siyeh.ig.telemetry;

import javax.swing.table.DefaultTableModel;
import java.text.NumberFormat;

public class TelemetryTableModel extends DefaultTableModel{
    private final InspectionGadgetsTelemetry telemetry;
    private NumberFormat format = NumberFormat.getNumberInstance();
    {
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(2);
    }

    public TelemetryTableModel(InspectionGadgetsTelemetry telemetry){
        super();
        this.telemetry = telemetry;
    }

    public int getColumnCount(){
        return 4;
    }

    public int getRowCount(){
        if(telemetry== null)
        {
            return 0;
        }
        return telemetry.getInspections().length;
    }

    public Object getValueAt(int row, int column){
        if(telemetry == null)
        {
            return null;
        }
        final String[] inspections = telemetry.getInspections();
        final String inspection = inspections[row];
        switch(column){
            case 0:
                return inspection;
            case 1:
                return Long.toString(telemetry.getRunTimeForInspection(inspection));
            case 2:
                return format.format(telemetry.getAverageRunTimeForInspection(inspection));
            case 3:
                return Integer.toString(telemetry.getRunCountForInspection(inspection));
            default:
                return null;
        }
    }

    public void setValueAt(Object object, int i,
                           int i1){
        //don't do anything
    }

    public String getColumnName(int column){
        switch(column){
            case 0:
                return "Inspection Name";
            case 1:
                return "Total Run Time (msecs)";
            case 2:
                return "Average Run Time (msecs)";
            case 3:
                return "Total Run Count";
            default:
                return null;
        }
    }
}
