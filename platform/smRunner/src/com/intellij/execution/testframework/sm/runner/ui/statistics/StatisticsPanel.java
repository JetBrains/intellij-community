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
package com.intellij.execution.testframework.sm.runner.ui.statistics;

import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.execution.testframework.sm.SMRunnerUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.PropagateSelectionHandler;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.TableView;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class StatisticsPanel implements DataProvider {
  public static final Icon STATISTICS_TAB_ICON = TestsUIUtil.loadIcon("testStatistics");
  public static final DataKey<StatisticsPanel> SM_TEST_RUNNER_STATISTICS  = DataKey.create("SM_TEST_RUNNER_STATISTICS");

  private TableView<SMTestProxy> myStatisticsTableView;
  private JPanel myContentPane;

  private final StatisticsTableModel myTableModel;
  private final List<PropagateSelectionHandler> myPropagateSelectionHandlers = new ArrayList<PropagateSelectionHandler>();
  private final Project myProject;
  private final TestFrameworkRunningModel myFrameworkRunningModel;

  public StatisticsPanel(final Project project, final TestFrameworkRunningModel model) {
    myProject = project;
    myTableModel = new StatisticsTableModel();
    myStatisticsTableView.setModel(myTableModel);
    myFrameworkRunningModel = model;

    final Runnable gotoSuiteOrParentAction = createGotoSuiteOrParentAction();
    myStatisticsTableView.addMouseListener(new MouseAdapter(){
      @Override
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          gotoSuiteOrParentAction.run();
        }
      }
    });

    // Fire selection changed and move focus on SHIFT+ENTER
    final KeyStroke shiftEnterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK);
    SMRunnerUtil.registerAsAction(shiftEnterKey, "select-test-proxy-in-test-view",
                            new Runnable() {
                              public void run() {
                                showSelectedProxyInTestsTree();
                              }
                            },
                            myStatisticsTableView);

    // Expand selected or go to parent on ENTER
    final KeyStroke enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
    SMRunnerUtil.registerAsAction(enterKey, "go-to-selected-suite-or-parent",
                            gotoSuiteOrParentAction,
                            myStatisticsTableView);
    // Contex menu in Table
    PopupHandler.installPopupHandler(myStatisticsTableView, IdeActions.GROUP_TESTTREE_POPUP, ActionPlaces.TESTTREE_VIEW_POPUP);
    // set this statistic tab as dataprovider for test's table view
    myStatisticsTableView.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, this);
  }

  public void addPropagateSelectionListener(final PropagateSelectionHandler handler) {
    myPropagateSelectionHandlers.add(handler);
  }

  public JPanel getContentPane() {
    return myContentPane;
  }

  public SMTRunnerEventsListener createTestEventsListener() {
    return new SMTRunnerEventsAdapter() {
      @Override
      public void onSuiteStarted(@NotNull final SMTestProxy suite) {
        if (myTableModel.shouldUpdateModelBySuite(suite)) {
          updateAndRestoreSelection();
        }
      }

      @Override
      public void onSuiteFinished(@NotNull final SMTestProxy suite) {
        if (myTableModel.shouldUpdateModelBySuite(suite)) {
          updateAndRestoreSelection();
        }
      }

      @Override
      public void onTestStarted(@NotNull final SMTestProxy test) {
        if (myTableModel.shouldUpdateModelByTest(test)) {
          updateAndRestoreSelection();
        }
      }

      @Override
      public void onTestFinished(@NotNull final SMTestProxy test) {
        if (myTableModel.shouldUpdateModelByTest(test)) {
          updateAndRestoreSelection();
        }
      }

      private void updateAndRestoreSelection() {
        SMRunnerUtil.addToInvokeLater(new Runnable() {
          public void run() {
            // statisticsTableView can be null in JUnit tests
            final SMTestProxy oldSelection = myStatisticsTableView.getSelectedObject();

            // update module
            myTableModel.updateModel();

            // restore selection if it is possible
            if (oldSelection != null) {
              final int newRow = myTableModel.getIndexOf(oldSelection);
              if (newRow > -1) {
                myStatisticsTableView.setRowSelectionInterval(newRow, newRow);
              }
            }
          }
        });
      }
    };
  }

  public Object getData(@NonNls final String dataId) {
    if (SM_TEST_RUNNER_STATISTICS.is(dataId)) {
      return this;
    }
    return TestsUIUtil.getData(getSelectedItem(), dataId, myFrameworkRunningModel);
  }

  /**
   * On event - change selection and probably requests focus. Is used when we want
   * navigate from other component to this
   * @return Listener
   */
  public PropagateSelectionHandler createSelectMeListener() {
    return new PropagateSelectionHandler() {
      public void handlePropagateSelectionRequest(@Nullable final SMTestProxy selectedTestProxy,
                                    @NotNull final Object sender,
                                    final boolean requestFocus) {
        selectProxy(selectedTestProxy, sender, requestFocus);
      }
    };
  }

  public void selectProxy(@Nullable final SMTestProxy selectedTestProxy,
                          @NotNull final Object sender,
                          final boolean requestFocus) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        // Select tab if focus was requested
        if (requestFocus) {
          IdeFocusManager.getInstance(myProject).requestFocus(myStatisticsTableView, true);
        }

        // Select proxy in table
        selectProxy(selectedTestProxy);
      }
    });
  }

  protected void showSelectedProxyInTestsTree() {
    final Collection<SMTestProxy> proxies = myStatisticsTableView.getSelection();
    if (proxies.isEmpty()) {
      return;
    }
    final SMTestProxy proxy = proxies.iterator().next();
    myStatisticsTableView.clearSelection();
    fireOnPropagateSelection(proxy);
  }

  protected Runnable createGotoSuiteOrParentAction() {
    // Expand selected or go to parent
    return new Runnable() {
      public void run() {
        final SMTestProxy selectedProxy = getSelectedItem();
        if (selectedProxy == null) {
          return;
        }

        final int i = myStatisticsTableView.getSelectedRow();
        assert i >= 0; //because something is selected

        // If first line is selected we should go to parent suite
        if (ColumnTest.TestsCellRenderer.isFirstLine(i)) {
          final SMTestProxy parentSuite = selectedProxy.getParent();
          if (parentSuite != null) {
            // go to parent and current suit in it
            showInTableAndSelectRow(parentSuite, selectedProxy);
          }
        } else {
          // if selected element is suite - we should expand it
          if (selectedProxy.isSuite()) {
            // expand and select first (Total) row
            showInTableAndSelectRow(selectedProxy, selectedProxy);
          }
        }
      }
    };
  }

  protected void selectProxy(@Nullable final SMTestProxy selectedTestProxy) {
    // Send event to model
    myTableModel.updateModelOnProxySelected(selectedTestProxy);

    // Now we want to select proxy in table (if it is possible)
    if (selectedTestProxy != null) {
      findAndSelectInTable(selectedTestProxy);
    }
  }
  /**
   * Selects row in table
   * @param rowIndex Row's index
   */
  protected void selectRow(final int rowIndex) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        // updates model
        myStatisticsTableView.setRowSelectionInterval(rowIndex, rowIndex);

        // Scroll to visible
        TableUtil.scrollSelectionToVisible(myStatisticsTableView);
      }
    });
  }

  /**
   * Selects row in table
   */
  protected void selectRowOf(final SMTestProxy proxy) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final int rowIndex = myTableModel.getIndexOf(proxy);
        myStatisticsTableView.setRowSelectionInterval(rowIndex, rowIndex >= 0 ? rowIndex : 0);
        // Scroll to visible
        TableUtil.scrollSelectionToVisible(myStatisticsTableView);
      }
    });
  }

  @Nullable
  protected SMTestProxy getSelectedItem() {
    return myStatisticsTableView.getSelectedObject();
  }

  protected List<SMTestProxy> getTableItems() {
    return myTableModel.getItems();
  }

  private void findAndSelectInTable(final SMTestProxy proxy) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final int rowIndex = myTableModel.getIndexOf(proxy);
        if (rowIndex >= 0) {
          myStatisticsTableView.setRowSelectionInterval(rowIndex, rowIndex);
        }
      }
    });
  }

  private void fireOnPropagateSelection(final SMTestProxy selectedTestProxy) {
    for (PropagateSelectionHandler handler : myPropagateSelectionHandlers) {
      handler.handlePropagateSelectionRequest(selectedTestProxy, this, true);
    }
  }

  private void createUIComponents() {
    myStatisticsTableView = new TableView<SMTestProxy>();
  }

  private void showInTableAndSelectRow(final SMTestProxy suite, final SMTestProxy suiteProxy) {
    selectProxy(suite);
    selectRowOf(suiteProxy);
  }
}
