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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.Marker;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.ui.TestsOutputConsolePrinter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.util.List;
import java.util.Set;

/**
 * @author Roman Chernyatchik
 */
public class GeneralToSMTRunnerEventsConvertorTest extends BaseSMTRunnerTestCase {
  private SMTRunnerConsoleView myConsole;
  private GeneralToSMTRunnerEventsConvertor myEventsProcessor;
  private TreeModel myTreeModel;
  private SMTestRunnerResultsForm myResultsViewer;
  private MockPrinter myMockResettablePrinter;

  private class MyConsoleView extends SMTRunnerConsoleView {
    private final TestsOutputConsolePrinter myTestsOutputConsolePrinter;

    private MyConsoleView(final TestConsoleProperties consoleProperties, final ExecutionEnvironment environment) {
      super(consoleProperties, environment);

      myTestsOutputConsolePrinter = new TestsOutputConsolePrinter(MyConsoleView.this, consoleProperties, null) {
        @Override
        public void print(final String text, final ConsoleViewContentType contentType) {
          myMockResettablePrinter.print(text, contentType);
        }
      };
    }

    @Override
    public TestsOutputConsolePrinter getPrinter() {
      return myTestsOutputConsolePrinter;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    TestConsoleProperties consoleProperties = createConsoleProperties();
    TestConsoleProperties.HIDE_PASSED_TESTS.set(consoleProperties, false);
    TestConsoleProperties.OPEN_FAILURE_LINE.set(consoleProperties, false);
    TestConsoleProperties.SCROLL_TO_SOURCE.set(consoleProperties, false);
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(consoleProperties, false);
    TestConsoleProperties.TRACK_RUNNING_TEST.set(consoleProperties, false);

    final ExecutionEnvironment environment = new ExecutionEnvironment();
    myMockResettablePrinter = new MockPrinter(true);
    myConsole = new MyConsoleView(consoleProperties, environment);
    myConsole.initUI();
    myResultsViewer = myConsole.getResultsViewer();
    myEventsProcessor = new GeneralToSMTRunnerEventsConvertor(myResultsViewer.getTestsRootNode(), "SMTestFramework");
    myEventsProcessor.addEventsListener(myResultsViewer);
    myTreeModel = myResultsViewer.getTreeView().getModel();

    myEventsProcessor.onStartTesting();
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myEventsProcessor);
    Disposer.dispose(myConsole);

    super.tearDown();
  }

  public void testOnStartedTesting() {
    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(0, myTreeModel.getChildCount(rootTreeNode));

    final SMTRunnerNodeDescriptor nodeDescriptor =
        (SMTRunnerNodeDescriptor)((DefaultMutableTreeNode)rootTreeNode).getUserObject();
    assertFalse(nodeDescriptor.expandOnDoubleClick());

    final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);

    assertTrue(rootProxy.wasLaunched());
    assertTrue(rootProxy.isInProgress());
    assertTrue(rootProxy.isLeaf());

    assertEquals("[root]", rootTreeNode.toString());
  }

  public void testOnTestStarted() throws InterruptedException {
    onTestStarted("some_test");
    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

    assertNotNull(proxy);
    assertTrue(proxy.isInProgress());

    final Object rootTreeNode = (myTreeModel.getRoot());
    assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
    final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);
    assertSameElements(rootProxy.getChildren(), proxy);


    onTestStarted("some_test2");
    final String fullName2 = myEventsProcessor.getFullTestName("some_test2");
    final SMTestProxy proxy2 = myEventsProcessor.getProxyByFullTestName(fullName2);
    assertSameElements(rootProxy.getChildren(), proxy, proxy2);
  }

  public void testOnTestStarted_Twice() {
    onTestStarted("some_test");
    onTestStarted("some_test");

    assertEquals(1, myEventsProcessor.getRunningTestsQuantity());
  }

  public void testOnTestStarted_WithLocation() throws InterruptedException {
    onTestStarted("some_test", "file://some/file.rb:1");
    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

    assertNotNull(proxy);
    assertEquals("file://some/file.rb:1", proxy.getLocationUrl());
  }

  public void testOnTestFailure() {
    onTestStarted("some_test");
    myEventsProcessor.onTestFailure(new TestFailedEvent("some_test", "", "", false, null, null));

    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

    assertNotNull(proxy);
    assertTrue(proxy.isDefect());
    assertFalse(proxy.isInProgress());
  }

  public void testOnTestComparisonFailure() {
    onTestStarted("some_test");
    myEventsProcessor.onTestFailure(new TestFailedEvent("some_test", "", "", false, "actual", "expected"));

    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

    assertNotNull(proxy);
    assertTrue(proxy.isDefect());
    assertFalse(proxy.isInProgress());
  }

  public void testOnTestFailure_Twice() {
    myMockResettablePrinter.resetIfNecessary();
    onTestStarted("some_test");
    myEventsProcessor.onTestFailure(new TestFailedEvent("some_test", "msg 1", "trace 1", false, null, null));
    myEventsProcessor.onTestFailure(new TestFailedEvent("some_test", "msg 2", "trace 2", false, null, null));

    assertEquals(1, myEventsProcessor.getRunningTestsQuantity());
    final Set<AbstractTestProxy> failedTests = myEventsProcessor.getFailedTestsSet();
    assertEquals(1, failedTests.size());
    for (final AbstractTestProxy test : failedTests) {
      assertEquals("some_test", test.getName());
    }
    assertEquals("\nmsg 1\ntrace 1\n\nmsg 2\ntrace 2\n", myMockResettablePrinter.getStdErr());
  }

  public void testOnTestError() {
    onTestStarted("some_test");
    myEventsProcessor.onTestFailure(new TestFailedEvent("some_test", "", "", true, null, null));

    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

    assertNotNull(proxy);
    assertTrue(proxy.isDefect());
    assertFalse(proxy.isInProgress());
  }

  public void testOnTestIgnored() {
    onTestStarted("some_test");
    myEventsProcessor.onTestIgnored(new TestIgnoredEvent("some_test", "", null));

    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

    assertNotNull(proxy);
    assertTrue(proxy.isDefect());
    assertFalse(proxy.isInProgress());
  }

  public void testOnTestFinished() {
    onTestStarted("some_test");
    final String fullName = myEventsProcessor.getFullTestName("some_test");
    final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);
    myEventsProcessor.onTestFinished(new TestFinishedEvent("some_test", 10));

    assertEquals(0, myEventsProcessor.getRunningTestsQuantity());
    assertEquals(0, myEventsProcessor.getFailedTestsSet().size());

    assertNotNull(proxy);
    assertFalse(proxy.isDefect());
    assertFalse(proxy.isInProgress());

    //Tree
    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
    final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);
    assertSameElements(rootProxy.getChildren(), proxy);
  }

  //TODO[romeo] catch assertion
  //public void testFinished_Twice() {
  //  myEventsProcessor.onTestStarted("some_test");
  //  myEventsProcessor.onTestFinished("some_test");
  //  myEventsProcessor.onTestFinished("some_test");
  //
  //  assertEquals(1, myEventsProcessor.getTestsCurrentCount());
  //  assertEquals(0, myEventsProcessor.getRunningTestsFullNameToProxy().size());
  //  assertEquals(0, myEventsProcessor.getFailedTestsSet().size());
  //
  //}

  public void testOnTestFinished_EmptySuite() {
    myEventsProcessor.onFinishTesting();

    //Tree
    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(0, myTreeModel.getChildCount(rootTreeNode));
    final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);

    assertFalse(rootProxy.isInProgress());
    assertFalse(rootProxy.isDefect());
  }

  public void testOnFinishedTesting_WithFailure() {
    onTestStarted("test");
    myEventsProcessor.onTestFailure(new TestFailedEvent("test", "", "", false, null, null));
    myEventsProcessor.onTestFinished(new TestFinishedEvent("test", 10));
    myEventsProcessor.onFinishTesting();

    //Tree
    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
    final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);

    assertFalse(rootProxy.isInProgress());
    assertTrue(rootProxy.isDefect());
  }

  public void testOnFinishedTesting_WithError() {
    onTestStarted("test");
    myEventsProcessor.onTestFailure(new TestFailedEvent("test", "", "", true, null, null));
    myEventsProcessor.onTestFinished(new TestFinishedEvent("test", 10));
    myEventsProcessor.onFinishTesting();

    //Tree
    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
    final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);

    assertFalse(rootProxy.isInProgress());
    assertTrue(rootProxy.isDefect());
  }

  public void testOnFinishedTesting_WithIgnored() {
    onTestStarted("test");
    myEventsProcessor.onTestIgnored(new TestIgnoredEvent("test", "", null));
    myEventsProcessor.onTestFinished(new TestFinishedEvent("test", 10));
    myEventsProcessor.onFinishTesting();

    //Tree
    final Object rootTreeNode = myTreeModel.getRoot();
    assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
    final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
    assertNotNull(rootProxy);

    assertFalse(rootProxy.isInProgress());
    assertTrue(rootProxy.isDefect());
  }

  public void testOnFinishedTesting_twice() {
    myEventsProcessor.onFinishTesting();

    final Marker finishedMarker = new Marker();
    myEventsProcessor.addEventsListener(new SMTRunnerEventsAdapter(){
      @Override
      public void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy testsRoot) {
        finishedMarker.set();
      }
    });
    myEventsProcessor.onFinishTesting();
    assertFalse(finishedMarker.isSet());
  }

  public void testOnSuiteStarted() {
    onTestSuiteStarted("suite1");

    //lets check that new tests have right parent
    onTestStarted("test1");
    final SMTestProxy test1 =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test1"));
    assertNotNull(test1);
    assertEquals("suite1", test1.getParent().getName());

    //lets check that new suits have righ parent
    onTestSuiteStarted("suite2");
    onTestSuiteStarted("suite3");
    onTestStarted("test2");
    final SMTestProxy test2 =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test2"));
    assertNotNull(test2);
    assertEquals("suite3", test2.getParent().getName());
    assertEquals("suite2", test2.getParent().getParent().getName());

    myEventsProcessor.onTestFinished(new TestFinishedEvent("test2", 10));

    //check that after finishing suite (suite3), current will be parent of finished suite (i.e. suite2)
    myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite3"));
    onTestStarted("test3");
    final SMTestProxy test3 =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test3"));
    assertNotNull(test3);
    assertEquals("suite2", test3.getParent().getName());

    //clean up
    myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite2"));
    myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite1"));
  }

  public void testOnSuiteStarted_WithLocation() {
    onTestSuiteStarted("suite1", "file://some/file.rb:1");

    //lets check that new tests have right parent
    onTestStarted("test1", "file://some/file.rb:4");
    final SMTestProxy test1 =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test1"));

    assertNotNull(test1);
    assertEquals("file://some/file.rb:1", test1.getParent().getLocationUrl());
    assertEquals("file://some/file.rb:4", test1.getLocationUrl());
  }

  public void testGetCurrentTestSuite() {
    assertEquals(myResultsViewer.getTestsRootNode(), myEventsProcessor.getCurrentSuite());

    onTestSuiteStarted("my_suite");
    assertEquals("my_suite", myEventsProcessor.getCurrentSuite().getName());
  }

  public void testConcurrentSuite_intersected() {
    myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent("suite1", null));
    myEventsProcessor.onTestStarted(new TestStartedEvent("suite2.test1", null));

    final SMTestProxy test1 =
        myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("suite2.test1"));

    myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite1"));

    myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent("suite2", null));
    myEventsProcessor.onTestFinished(new TestFinishedEvent("suite2.test1", 10));
    myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite2"));

    assertNotNull(test1);
    assertEquals("suite1", test1.getParent().getName());

    final List<? extends SMTestProxy> children =
        myResultsViewer.getTestsRootNode().getChildren();
    assertEquals(2, children.size());
    assertEquals("suite1", children.get(0).getName());
    assertEquals(1, children.get(0).getChildren().size());
    assertEquals("suite2", children.get(1).getName());
    assertEquals(0, children.get(1).getChildren().size());
  }

  public void test3212() {
    // let's make
    myEventsProcessor.clearInternalSuitesStack();

    assertEquals(myResultsViewer.getTestsRootNode(), myEventsProcessor.getCurrentSuite());
  }

  private void onTestStarted(final String testName) {
    onTestStarted(testName, null);
  }

  private void onTestStarted(final String testName, @Nullable final String locationUrl) {
    myEventsProcessor.onTestStarted(new TestStartedEvent(testName, locationUrl));
    myResultsViewer.performUpdate();
  }

  private void onTestSuiteStarted(final String suiteName) {
    onTestSuiteStarted(suiteName, null);
  }

  private void onTestSuiteStarted(final String suiteName, @Nullable final String locationUrl) {
    myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent(suiteName, locationUrl));
    myResultsViewer.performUpdate();
  }
}
