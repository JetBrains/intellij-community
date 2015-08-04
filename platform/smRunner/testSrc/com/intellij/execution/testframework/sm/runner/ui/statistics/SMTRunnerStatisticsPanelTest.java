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

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.Marker;
import com.intellij.execution.testframework.sm.runner.BaseSMTRunnerTestCase;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.PropagateSelectionHandler;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class SMTRunnerStatisticsPanelTest extends BaseSMTRunnerTestCase {
  private StatisticsPanel myStatisticsPanel;
  private SMTRunnerEventsListener myTestEventsListener;
  private SMTestProxy myRootSuite;
  private SMTestRunnerResultsForm myResultsForm;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRootSuite = createSuiteProxy("root");

    final TestConsoleProperties consoleProperties = createConsoleProperties();
    final ExecutionEnvironment environment = new ExecutionEnvironment();
    myResultsForm = new SMTestRunnerResultsForm(
      new JLabel(),
                                                consoleProperties
    );
    Disposer.register(myResultsForm, consoleProperties);
    myResultsForm.initUI();
    myStatisticsPanel = myResultsForm.getStatisticsPane();
    myTestEventsListener = myStatisticsPanel.createTestEventsListener();
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myResultsForm);
    super.tearDown();
  }

  public void testGotoSuite_OnTest() {
    // create test sturcure
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");
    final SMTestProxy suite1 = createSuiteProxy("suite1", rootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite1);

    // show suite in table
    myStatisticsPanel.selectProxy(suite1);
    // selects row that corresponds to test1
    myStatisticsPanel.selectRow(0);

    // Check that necessary row is selected
    assertEquals(test1, myStatisticsPanel.getSelectedItem());

    // Perform action on test
    myStatisticsPanel.createGotoSuiteOrParentAction().run();

    // Check that current suite in table wasn't changed.
    // For it let's select Total row and check selected object
    myStatisticsPanel.selectRow(0);
    assertEquals(test1, myStatisticsPanel.getSelectedItem());
  }

  public void testGotoSuite_OnSuite() {
    // create test sturcure
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");
    final SMTestProxy suite1 = createSuiteProxy("suite1", rootSuite);

    // show root suite in table
    myStatisticsPanel.selectProxy(rootSuite);
    // selects row that corresponds to suite1
    myStatisticsPanel.selectRow(0);

    // Check that necessary row is selected
    assertEquals(suite1, myStatisticsPanel.getSelectedItem());

  }

  public void testGotoParentSuite_Total() {
    // create test sturcure
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");
    final SMTestProxy suite1 = createSuiteProxy("suite1", rootSuite);

    // show suite in table
    myStatisticsPanel.selectProxy(suite1);
    // selects Total row
    assertEmpty(myStatisticsPanel.getTableItems());

  }

  public void testGotoParentSuite_TotalRoot() {
    // create test sturcure
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");
    final SMTestProxy suite1 = createSuiteProxy("suite1", rootSuite);

    // show root suite in table
    myStatisticsPanel.selectProxy(rootSuite);
    // selects Total row
    myStatisticsPanel.selectRow(0);

    // Check that necessary row is selected
    assertEquals(suite1, myStatisticsPanel.getSelectedItem());

  }

  public void testChangeSelectionListener() {
    // create data fixture
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");
    final SMTestProxy suite1 = createSuiteProxy("suite1", rootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite1);

    //test
    myStatisticsPanel.selectProxy(test1);
    assertEquals(test1, myStatisticsPanel.getSelectedItem());

    //suite
    myStatisticsPanel.selectProxy(suite1);
    assertEquals(null, myStatisticsPanel.getSelectedItem());
  }

  public void testChangeSelectionAction() {
    final Marker onSelectedHappend = new Marker();
    final Ref<SMTestProxy> proxyRef = new Ref<SMTestProxy>();
    final Ref<Boolean> focusRequestedRef = new Ref<Boolean>();

    myStatisticsPanel.addPropagateSelectionListener(new PropagateSelectionHandler() {
      @Override
      public void handlePropagateSelectionRequest(@Nullable final SMTestProxy selectedTestProxy, @NotNull final Object sender,
                                    final boolean requestFocus) {
        onSelectedHappend.set();
        proxyRef.set(selectedTestProxy);
        focusRequestedRef.set(requestFocus);
      }
    });

    // create data fixture
    final SMTestProxy rootSuite = createSuiteProxy("rootSuite");
    final SMTestProxy suite1 = createSuiteProxy("suite1", rootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite1);

    //on test
    myStatisticsPanel.selectProxy(suite1);
    myStatisticsPanel.selectRow(0);
    assertEquals(test1, myStatisticsPanel.getSelectedItem());

    myStatisticsPanel.showSelectedProxyInTestsTree();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(test1, proxyRef.get());
    assertTrue(focusRequestedRef.get());

    //on suite
    //reset markers
    onSelectedHappend.reset();
    proxyRef.set(null);
    focusRequestedRef.set(null);

    myStatisticsPanel.selectProxy(rootSuite);
    myStatisticsPanel.selectRow(0);
    assertEquals(suite1, myStatisticsPanel.getSelectedItem());

    myStatisticsPanel.showSelectedProxyInTestsTree();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(suite1, proxyRef.get());
    assertTrue(focusRequestedRef.get());

    //on Total
    //reset markers
    onSelectedHappend.reset();
    proxyRef.set(null);
    focusRequestedRef.set(null);

    myStatisticsPanel.selectProxy(rootSuite);
    myStatisticsPanel.selectRow(0);
    assertEquals(suite1, myStatisticsPanel.getSelectedItem());

    myStatisticsPanel.showSelectedProxyInTestsTree();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(suite1, proxyRef.get());
    assertTrue(focusRequestedRef.get());
  }

  public void testOnSuiteStarted_NoCurrent() {
    myStatisticsPanel.selectProxy(null);

    final SMTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onSuiteStarted(suite1);
    assertEmpty(getItems());
  }

  public void testOnSuiteStarted_Current() {
    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    myStatisticsPanel.selectProxy(suite);
    assertSameElements(getItems());

    final SMTestProxy test1 = createTestProxy("test1", suite);
    final SMTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteStarted(suite);
    assertSameElements(getItems(), test1, test2);
  }

  public void testOnSuiteStarted_Child() {
    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    myStatisticsPanel.selectProxy(suite);
    assertSameElements(getItems());

    final SMTestProxy test1 = createTestProxy("test1", suite);
    final SMTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteStarted(test1);
    assertSameElements(getItems(), test1, test2);
  }

  public void testOnSuiteStarted_Other() {
    final SMTestProxy suite = createSuiteProxy("suite", myRootSuite);
    final SMTestProxy other_suite = createSuiteProxy("other_suite", myRootSuite);

    myStatisticsPanel.selectProxy(suite);
    assertSameElements(getItems());

    createTestProxy("test1", suite);
    createTestProxy("test2", suite);
    myTestEventsListener.onSuiteStarted(other_suite);
    assertSameElements(getItems());
  }

  public void testOnSuiteFinished_NoCurrent() {
    myStatisticsPanel.selectProxy(null);

    final SMTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onSuiteFinished(suite1);
    assertEmpty(getItems());
  }

  public void testOnSuiteFinished_Current() {
    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    myStatisticsPanel.selectProxy(suite);
    assertSameElements(getItems());

    final SMTestProxy test1 = createTestProxy("test1", suite);
    final SMTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteFinished(suite);
    assertSameElements(getItems(), test1, test2);
  }

  public void testOnSuiteFinished_Child() {
    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);

    myStatisticsPanel.selectProxy(suite);
    assertSameElements(getItems());

    final SMTestProxy test1 = createTestProxy("test1", suite);
    final SMTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onSuiteFinished(test1);
    assertSameElements(getItems(), test1, test2);
  }

  public void testOnSuiteFinished_Other() {
    final SMTestProxy suite = createSuiteProxy("suite", myRootSuite);
    final SMTestProxy other_suite = createSuiteProxy("other_suite", myRootSuite);

    myStatisticsPanel.selectProxy(suite);
    assertSameElements(getItems());

    createTestProxy("test1", suite);
    createTestProxy("test2", suite);
    myTestEventsListener.onSuiteFinished(other_suite);
    assertSameElements(getItems());
  }

  public void testOnTestStarted_NoCurrent() {
    myStatisticsPanel.selectProxy(null);

    final SMTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onTestStarted(test1);
    assertEmpty(getItems());
  }

  public void testOnTestStarted_Child() {
    final SMTestProxy test1 = createTestProxy("test1", myRootSuite);

    myStatisticsPanel.selectProxy(test1);
    assertSameElements(getItems(),test1);

    final SMTestProxy test2 = createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestStarted(test1);
    assertSameElements(getItems(), test1, test2);
  }

  public void testOnTestStarted_Other() {
    final SMTestProxy test1 = createTestProxy("test1", myRootSuite);

    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    final SMTestProxy other_test = createTestProxy("other_test", suite);

    myStatisticsPanel.selectProxy(test1);
    assertSameElements(getItems(), test1, suite);

    createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestStarted(other_test);
    assertSameElements(getItems(), test1, suite);
  }

  public void testOnTestFinished_NoCurrent() {
    myStatisticsPanel.selectProxy(null);

    final SMTestProxy suite1 = createSuiteProxy("suite1", myRootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite1);
    createTestProxy("test2", suite1);

    myTestEventsListener.onTestFinished(test1);
    assertEmpty(getItems());

  }

  public void testOnTestFinished_Child() {
    final SMTestProxy test1 = createTestProxy("test1", myRootSuite);

    myStatisticsPanel.selectProxy(test1);
    assertSameElements(getItems(), test1);

    final SMTestProxy test2 = createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestFinished(test1);
    assertSameElements(getItems(), test1, test2);
  }

  public void testOnTestFinished_Other() {
    final SMTestProxy test1 = createTestProxy("test1", myRootSuite);

    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    final SMTestProxy other_test = createTestProxy("other_test", suite);

    myStatisticsPanel.selectProxy(test1);
    assertSameElements(getItems(), test1, suite);

    createTestProxy("test2", myRootSuite);
    myTestEventsListener.onTestFinished(other_test);
    assertSameElements(getItems(), test1, suite);
  }

  public void testSelectionRestoring_ForTest() {
    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    final SMTestProxy test1 = createTestProxy("test1", suite);

    myStatisticsPanel.selectProxy(test1);

    final SMTestProxy test2 = createTestProxy("test2", suite);
    myTestEventsListener.onTestStarted(test2);

    assertEquals(test1, myStatisticsPanel.getSelectedItem());
  }

  public void testSelectionRestoring_ForSuite() {
    myStatisticsPanel.selectProxy(myRootSuite);

    // another suite was added. Model should be updated
    final SMTestProxy suite = createSuiteProxy("suite1", myRootSuite);
    myTestEventsListener.onSuiteStarted(suite);

    assertEquals(null, myStatisticsPanel.getSelectedItem());
  }

  private List<SMTestProxy> getItems() {
    return myStatisticsPanel.getTableItems();
  }
}
