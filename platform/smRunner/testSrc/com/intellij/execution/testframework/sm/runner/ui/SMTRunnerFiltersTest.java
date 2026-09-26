// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.BaseSMTRunnerTestCase;
import com.intellij.execution.testframework.sm.runner.SMTRunnerTreeStructure;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.util.Disposer;

public class SMTRunnerFiltersTest extends BaseSMTRunnerTestCase {
  private MockTestResultsViewer myResultsViewer;
  private TestConsoleProperties myProperties;
  private SMTestRunnerResultsForm myResultsForm;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myProperties = createConsoleProperties();
    myResultsViewer = new MockTestResultsViewer(myProperties, mySuite);

    TestConsoleProperties.HIDE_PASSED_TESTS.set(myProperties, false);
    TestConsoleProperties.HIDE_IGNORED_TEST.set(myProperties, false);
    TestConsoleProperties.HIDE_SUCCESSFUL_CONFIG.set(myProperties, true);
    ConsoleView console = myProperties.createConsole();
    myResultsForm = new SMTestRunnerResultsForm(console, myProperties, null);
    Disposer.register(myResultsForm, console);
    Disposer.register(myResultsForm, myProperties);
    myResultsForm.initUI();

    //setup suites tree
    mySuite.setStarted();

    final SMTestProxy testsSuite = createSuiteProxy("my suite", mySuite);
    testsSuite.setStarted();

    // passed test
    final SMTestProxy testPassed1 = createTestProxy("testPassed1", testsSuite);
    testPassed1.setStarted();
    testPassed1.setFinished();
    
    // passed config
    final SMTestProxy testConfig1 = createTestProxy("testConfig1", testsSuite);
    testConfig1.setConfig(true);
    testConfig1.setStarted();
    testConfig1.setFinished();

    //ignored test
    final SMTestProxy testIgnored1 = createTestProxy("testIgnored1", testsSuite);
    testIgnored1.setStarted();
    testIgnored1.setTestIgnored("", "");
    testsSuite.setFinished();
    mySuite.setFinished();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      TestConsoleProperties.HIDE_PASSED_TESTS.set(myProperties, true);
      TestConsoleProperties.HIDE_IGNORED_TEST.set(myProperties, false);
      TestConsoleProperties.HIDE_SUCCESSFUL_CONFIG.set(myProperties, false);

      Disposer.dispose(myResultsViewer);
      Disposer.dispose(myResultsForm);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testShowPassedShowIgnored() {
    final SMTRunnerTreeStructure treeStructure = myResultsForm.getTreeBuilder().getTreeStructure();
    final Object[] suites = treeStructure.getChildElements(mySuite);
    assertTrue(suites.length == 1);
    final Object[] tests = treeStructure.getChildElements(suites[0]);
    assertTrue(tests.length == 2);
  }
  
  public void testShowPassedHideIgnored() {
    TestConsoleProperties.HIDE_IGNORED_TEST.set(myProperties, true);
    final SMTRunnerTreeStructure treeStructure = myResultsForm.getTreeBuilder().getTreeStructure();
    final Object[] suites = treeStructure.getChildElements(mySuite);
    assertTrue(suites.length == 1);
    final Object[] tests = treeStructure.getChildElements(suites[0]);
    assertTrue(tests.length == 1);
    assertTrue(tests[0] instanceof SMTestProxy && "testPassed1".equals(((SMTestProxy)tests[0]).getName()));
  }
  
  public void testShowIgnoredHidePassed() {
    TestConsoleProperties.HIDE_PASSED_TESTS.set(myProperties, true);
    final SMTRunnerTreeStructure treeStructure = myResultsForm.getTreeBuilder().getTreeStructure();
    final Object[] suites = treeStructure.getChildElements(mySuite);
    assertTrue(suites.length == 1);
    final Object[] tests = treeStructure.getChildElements(suites[0]);
    assertTrue(tests.length == 1);
    assertTrue(tests[0] instanceof SMTestProxy && "testIgnored1".equals(((SMTestProxy)tests[0]).getName()));
  }
  
  public void testHidePassedHideIgnored() {
    TestConsoleProperties.HIDE_PASSED_TESTS.set(myProperties, true);
    TestConsoleProperties.HIDE_IGNORED_TEST.set(myProperties, true);
    final SMTRunnerTreeStructure treeStructure = myResultsForm.getTreeBuilder().getTreeStructure();
    final Object[] suites = treeStructure.getChildElements(mySuite);
    assertTrue(suites.length == 0);
  }
}
