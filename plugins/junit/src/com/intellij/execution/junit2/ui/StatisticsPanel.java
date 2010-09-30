/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.events.TestEvent;
import com.intellij.execution.junit2.ui.actions.TestContext;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.*;
import com.intellij.ui.table.BaseTableView;
import com.intellij.ui.table.TableView;
import com.intellij.util.config.Storage;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

class StatisticsPanel extends JPanel implements DataProvider{
  private final MyJUnitListener myListener = new MyJUnitListener();
  private TestProxy myCurrentTest = null;
  private StatisticsTable myChildInfo = null;


//  private TestCaseStatistics myTestCaseInfo = new TestCaseStatistics(TestColumnInfo.COLUMN_NAMES);
  private JUnitRunningModel myModel;
  private final TableView myTable;
  private final Storage.PropertiesComponentStorage myStorage = new Storage.PropertiesComponentStorage("junit_statistics_table_columns");
  private SimpleColoredComponent myTotalLabel;
  private SimpleColoredComponent myTimeLabel;

  public StatisticsPanel() {
    super(new BorderLayout(0, 0));
    myChildInfo = new StatisticsTable(TestColumnInfo.COLUMN_NAMES);
    myTable = new TableView(myChildInfo) {
      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        return new TestTableRenderer(TestColumnInfo.COLUMN_NAMES);
      }
    };
    PopupHandler.installPopupHandler(myTable,
                        IdeActions.GROUP_TESTSTATISTICS_POPUP,
                        ActionPlaces.TESTSTATISTICS_VIEW_POPUP);
//    add(myTestCaseInfo, BorderLayout.NORTH);
    add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    final JPanel eastPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));

    myTotalLabel = new SimpleColoredComponent();
    eastPanel.add(myTotalLabel);

    myTimeLabel = new SimpleColoredComponent();
    eastPanel.add(myTimeLabel);

    add(eastPanel, BorderLayout.SOUTH);
  }

  private void updateStatistics() {
    myTable.setVisible(true);
//    myTestCaseInfo.setVisible(false);
    TestProxy proxy = myCurrentTest;
    if (myCurrentTest.isLeaf() && myCurrentTest.getParent() != null) {
      proxy = myCurrentTest.getParent();
    }
    myChildInfo.updateStatistics(proxy);
    myTotalLabel.clear();
    myTotalLabel.append(TestsUIUtil.getTestSummary(proxy), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    myTimeLabel.clear();
    myTimeLabel.append("Total time: " + Formatters.statisticsFor(proxy).getTime(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    final int idx = myChildInfo.getIndexOf(myCurrentTest);
    if (idx >= 0) TableUtil.selectRows(myTable, new int[]{myTable.convertRowIndexToView(idx)});
    TableUtil.scrollSelectionToVisible(myTable);
  }

  public void attachTo(final JUnitRunningModel model) {
    myModel = model;
    myModel.addListener(myListener);
    myChildInfo.setModel(model);
    BaseTableView.restore(myStorage, myTable);
  }

  public Object getData(final String dataId) {
    if (myModel == null) return null;
    final TestProxy selectedTest = myChildInfo.getTestAt(myTable.getSelectedRow());
    if (TestContext.DATA_KEY.is(dataId)) {
      return new TestContext(myModel, selectedTest);
    }
    return TestsUIUtil.getData(selectedTest, dataId, myModel);
  }

  private class MyJUnitListener extends JUnitAdapter {
    public void onTestChanged(final TestEvent event) {
      if (!StatisticsPanel.this.isShowing()) return;
      if (myCurrentTest == event.getSource()) updateStatistics();
    }

    public void onTestSelected(final TestProxy test) {
      if (!StatisticsPanel.this.isShowing()) return;
      if (myCurrentTest == test)
        return;
      if (test == null) {
        myTable.setVisible(false);
        return;
      }
      myCurrentTest = test;
      updateStatistics();
    }


    public void doDispose() {
      BaseTableView.store(myStorage, myTable);
      myTable.setModel(new ListTableModel(TestColumnInfo.COLUMN_NAMES));
      myModel = null;
      myChildInfo = null;
      myCurrentTest = null;
    }
  }
}
