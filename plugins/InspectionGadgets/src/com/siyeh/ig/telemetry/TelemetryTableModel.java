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

import com.siyeh.InspectionGadgetsBundle;

class TelemetryTableModel extends DefaultTableModel {

    private final InspectionGadgetsTelemetry telemetry;
    private final NumberFormat format = NumberFormat.getNumberInstance();

    TelemetryTableModel(InspectionGadgetsTelemetry telemetry) {
        super();
        this.telemetry = telemetry;
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(2);
    }

    public int getColumnCount() {
        return 4;
    }

    @Nullable
    public Class getColumnClass(int column) {
        switch (column) {
            case 0:
                return String.class;
            case 1:
                return Long.class;
            case 2:
                return String.class;
            case 3:
                return Integer.class;
            default:
                return null;
        }
    }

    public int getRowCount() {
        if (telemetry == null) {
            return 0;
        }
        return telemetry.getInspections().length;
    }

    @Nullable
    public Object getValueAt(int row, int column) {
        if (telemetry == null) {
            return null;
        }
        final String[] inspections = telemetry.getInspections();
        final String inspection = inspections[row];
        switch (column) {
            case 0:
                return inspection;
            case 1:
                final long totalRunTime =
                        telemetry.getRunTimeForInspection(inspection);
                return Long.valueOf(totalRunTime);
            case 2:
                final double averageRunTime =
                        telemetry.getAverageRunTimeForInspection(inspection);
                return format.format(averageRunTime);
            case 3:
                final int runCount =
                        telemetry.getRunCountForInspection(inspection);
                return Integer.valueOf(runCount);
            default:
                return null;
        }
    }

    public void setValueAt(Object object, int i,
                           int i1) {
        //don't do anything
    }

    @Nullable
    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return InspectionGadgetsBundle.message(
                        "telemetry.table.column.inspection.name");
            case 1:
                return InspectionGadgetsBundle.message(
                        "telemetry.table.column.total.time");
            case 2:
                return InspectionGadgetsBundle.message(
                        "telemetry.table.column.average.time");
            case 3:
                return InspectionGadgetsBundle.message(
                        "telemetry.table.column.run.count");
            default:
                return null;
        }
    }
}