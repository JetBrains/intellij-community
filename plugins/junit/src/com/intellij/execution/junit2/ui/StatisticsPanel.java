package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.NewChildEvent;
import com.intellij.execution.junit2.StatisticsChanged;
import com.intellij.execution.junit2.TestEvent;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.junit2.ui.actions.TestContext;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableToolTipHandler;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.BaseTableView;
import com.intellij.util.config.Storage;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import java.awt.*;

class StatisticsPanel extends JPanel {
  private final MyJUnitListener myListener = new MyJUnitListener();
  private TestProxy myCurrentTest = null;
  private StatisticsTable myChildInfo = null;

  private final JScrollPane mySuiteInfo;
//  private TestCaseStatistics myTestCaseInfo = new TestCaseStatistics(TestColumnInfo.COLUMN_NAMES);
  private JUnitRunningModel myModel;
  private final DataProvidingTable myTable;
  private final Storage.PropertiesComponentStorage myStorage = new Storage.PropertiesComponentStorage("junit_statistics_table_columns");

  public StatisticsPanel() {
    super(new BorderLayout(0, 0));
    myTable = new DataProvidingTable(new ListTableModel(TestColumnInfo.COLUMN_NAMES));
    mySuiteInfo = ScrollPaneFactory.createScrollPane(myTable);
//    add(myTestCaseInfo, BorderLayout.NORTH);
    add(mySuiteInfo, BorderLayout.CENTER);
  }

  private void updateStatistics() {
    mySuiteInfo.setVisible(true);
//    myTestCaseInfo.setVisible(false);
    if (myCurrentTest.isLeaf() && myCurrentTest.getParent() != null) {
      myChildInfo.onSelectionChanged(myCurrentTest.getParent());
    } else
      myChildInfo.onSelectionChanged(myCurrentTest);
    myTable.selectRow(myCurrentTest);
  }

  public void attachTo(final JUnitRunningModel model) {
    myModel = model;
    myModel.addListener(myListener);
    myChildInfo = new StatisticsTable(TestColumnInfo.COLUMN_NAMES);
    TableToolTipHandler.install(myTable);
    myTable.setModel(myChildInfo);
    myChildInfo.setModel(model);
    BaseTableView.restore(myStorage, myTable);
  }

  private static void installStatisticsPopupHandler(final JComponent component) {
    PopupHandler.installPopupHandler(component,
                        IdeActions.GROUP_TESTSTATISTICS_POPUP,
                        ActionPlaces.TESTSTATISTICS_VIEW_POPUP);
  }

  private class MyJUnitListener extends JUnitAdapter {
    public void onTestChanged(final TestEvent event) {
      if (event instanceof StatisticsChanged) {
        if (myCurrentTest == event.getSource())
          updateStatistics();
      } else if (event instanceof NewChildEvent) {
        if (event.getSource() == myCurrentTest && !mySuiteInfo.isVisible())
          updateStatistics();
      }
    }

    public void onTestSelected(final TestProxy test) {
      if (myCurrentTest == test)
        return;
      if (test == null) {
        mySuiteInfo.setVisible(false);
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

  private class DataProvidingTable extends BaseTableView implements DataProvider {
    public DataProvidingTable(final ListTableModel tableModel) {
      super(tableModel);
      TestTableRenderer.installOn(this, TestColumnInfo.COLUMN_NAMES);
      installStatisticsPopupHandler(this);
      getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    public Object getData(final String dataId) {
      if (myModel == null) return null;
      final TestProxy selectedTest = (TestProxy)myChildInfo.getTestAt(getSelectedRow());
      if (TestContext.TEST_CONTEXT.equals(dataId)) {
        return new TestContext(myModel, selectedTest);
      }
      return TestsUIUtil.getData(selectedTest, dataId, myModel);
    }

    public void selectRow(final AbstractTestProxy test) {
      final int testRow = myChildInfo.getIndexOf(test);
      myTable.getSelectionModel().setLeadSelectionIndex(testRow);
      TableUtil.scrollSelectionToVisible(this);
    }

    protected void onHeaderClicked(final int column) {
      getListTableModel().sortByColumn(column);
    }
  }
}
