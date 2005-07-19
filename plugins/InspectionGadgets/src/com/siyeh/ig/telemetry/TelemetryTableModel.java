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

import org.jetbrains.annotations.Nullable;

import javax.swing.table.DefaultTableModel;
import java.text.NumberFormat;

class TelemetryTableModel extends DefaultTableModel{
    private final InspectionGadgetsTelemetry telemetry;
    private final NumberFormat format = NumberFormat.getNumberInstance();
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

    public @Nullable Object getValueAt(int row, int column){
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
                final long totalRunTime = telemetry.getRunTimeForInspection(inspection);
                return Long.toString(totalRunTime);
            case 2:
                final double averageRunTime = telemetry.getAverageRunTimeForInspection(inspection);
                return format.format(averageRunTime);
            case 3:
                final int runCount = telemetry.getRunCountForInspection(inspection);
                return Integer.toString(runCount);
            default:
                return null;
        }
    }

    public void setValueAt(Object object, int i,
                           int i1){
        //don't do anything
    }

    public @Nullable String getColumnName(int column){
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
