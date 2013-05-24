/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.siyeh.InspectionGadgetsBundle;

import javax.swing.*;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;

class TelemetryDisplay {

  private final JScrollPane scrollPane;
  private final ListTableModel<InspectionRunTime> tableModel;

  public TelemetryDisplay(InspectionGadgetsTelemetry telemetry) {
    tableModel = new ListTableModel<InspectionRunTime>(createColumns(),
                                                       telemetry.buildList(), 0);
    final JTable table = new JBTable(tableModel);
    new TableSpeedSearch(table);
    scrollPane = ScrollPaneFactory.createScrollPane(table);
  }

  private static ColumnInfo[] createColumns() {
    final Comparator<InspectionRunTime> nameComparator =
      new Comparator<InspectionRunTime>() {

        @Override
        public int compare(InspectionRunTime runTime1,
                           InspectionRunTime runTime2) {
          return runTime1.getInspectionName().compareToIgnoreCase(
            runTime2.getInspectionName());
        }
      };
    final Comparator<InspectionRunTime> runCountComparator =
      new Comparator<InspectionRunTime>() {

        @Override
        public int compare(InspectionRunTime runTime1,
                           InspectionRunTime runTime2) {
          return runTime1.getRunCount() - runTime2.getRunCount();
        }
      };
    final Comparator<InspectionRunTime> totalRunTimeComparator =
      new Comparator<InspectionRunTime>() {

        @Override
        public int compare(InspectionRunTime runTime1,
                           InspectionRunTime runTime2) {
          final long totalRunTime1 = runTime1.getTotalRunTime();
          final long totalRunTime2 = runTime2.getTotalRunTime();
          if (totalRunTime1 < totalRunTime2) {
            return -1;
          }
          else if (totalRunTime1 > totalRunTime2) {
            return 1;
          }
          else {
            return 0;
          }
        }
      };
    final Comparator<InspectionRunTime> averageRunTimeComparator =
      new Comparator<InspectionRunTime>() {

        @Override
        public int compare(InspectionRunTime runTime1,
                           InspectionRunTime runTime2) {
          final double averageRunTime1 =
            runTime1.getAverageRunTime();
          final double averageRunTime2 =
            runTime2.getAverageRunTime();
          if (averageRunTime1 < averageRunTime2) {
            return -1;
          }
          else if (averageRunTime1 > averageRunTime2) {
            return 1;
          }
          else {
            return 0;
          }
        }
      };
    return new ColumnInfo[]{
      new ColumnInfo<InspectionRunTime, String>(
        InspectionGadgetsBundle.message(
          "telemetry.table.column.inspection.name")) {
        @Override
        public String valueOf(InspectionRunTime inspectionRunTime) {
          return inspectionRunTime.getInspectionName();
        }

        @Override
        public Comparator<InspectionRunTime> getComparator() {
          return nameComparator;
        }
      },
      new ColumnInfo<InspectionRunTime, Integer>(
        InspectionGadgetsBundle.message(
          "telemetry.table.column.run.count")) {
        @Override
        public Integer valueOf(
          InspectionRunTime inspectionRunTime) {
          return Integer.valueOf(inspectionRunTime.getRunCount());
        }

        @Override
        public Comparator<InspectionRunTime> getComparator() {
          return runCountComparator;
        }
      },
      new ColumnInfo<InspectionRunTime, Long>(
        InspectionGadgetsBundle.message(
          "telemetry.table.column.total.time")) {
        @Override
        public Long valueOf(InspectionRunTime inspectionRunTime) {
          return Long.valueOf(
            inspectionRunTime.getTotalRunTime());
        }

        @Override
        public Comparator<InspectionRunTime> getComparator() {
          return totalRunTimeComparator;
        }
      },
      new ColumnInfo<InspectionRunTime, String>(
        InspectionGadgetsBundle.message(
          "telemetry.table.column.average.time")) {

        private final NumberFormat format =
          NumberFormat.getNumberInstance();

        {
          format.setMaximumFractionDigits(2);
          format.setMinimumFractionDigits(2);
        }

        @Override
        public String valueOf(InspectionRunTime inspectionRunTime) {
          return format.format(inspectionRunTime.getAverageRunTime());
        }

        @Override
        public Comparator<InspectionRunTime> getComparator() {
          return averageRunTimeComparator;
        }
      }
    };
  }

  public JComponent getContentPane() {
    return scrollPane;
  }

  public void update(List<InspectionRunTime> inspectionRunTimes) {
    tableModel.setItems(inspectionRunTimes);
  }
}
