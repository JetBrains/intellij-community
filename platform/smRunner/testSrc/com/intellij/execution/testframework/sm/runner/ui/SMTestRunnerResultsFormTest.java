/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.Marker;
import com.intellij.execution.testframework.sm.runner.BaseSMTRunnerTestCase;
import com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;

/**
 * @author Roman Chernyatchik
 */
public class SMTestRunnerResultsFormTest extends BaseSMTRunnerTestCase {
  private SMTRunnerConsoleView myConsole;
  private GeneralToSMTRunnerEventsConvertor myEventsProcessor;
  private TreeModel myTreeModel;
  private SMTestRunnerResultsForm myResultsViewer;
  private TestConsoleProperties myConsoleProperties;
  private SMTestProxy.SMRootTestProxy myTestsRootNode;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myConsoleProperties = createConsoleProperties();
    TestConsoleProperties.HIDE_PASSED_TESTS.set(myConsoleProperties, false);
    TestConsoleProperties.OPEN_FAILURE_LINE.set(myConsoleProperties, false);
    TestConsoleProperties.SCROLL_TO_SOURCE.set(myConsoleProperties, false);
    TestConsoleProperties.SELECT_FIRST_DEFECT.set(myConsoleProperties, false);
    TestConsoleProperties.TRACK_RUNNING_TEST.set(myConsoleProperties, false);

    final ExecutionEnvironment environment = new ExecutionEnvironment();

    myConsole = new SMTRunnerConsoleView(myConsoleProperties);
    myConsole.initUI();
    myResultsViewer = myConsole.getResultsViewer();
    myTestsRootNode = myResultsViewer.getTestsRootNode();
    myEventsProcessor = new GeneralToSMTRunnerEventsConvertor(myConsoleProperties.getProject(), myResultsViewer.getTestsRootNode(), "SMTestFramework");
    myEventsProcessor.addEventsListener(myResultsViewer);
    myTreeModel = myResultsViewer.getTreeView().getModel();
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myEventsProcessor);
    Disposer.dispose(myConsole);

    super.tearDown();
  }

  public void testGetTestsRootNode() {
    assertNotNull(myTestsRootNode);

    myResultsViewer.onTestingFinished(myTestsRootNode);
    assertNotNull(myResultsViewer.getTestsRootNode());
  }

  public void testTestingStarted() {
    myResultsViewer.onTestingStarted(myTestsRootNode);

    assertTrue(myResultsViewer.getStartTime() > 0);
    assertEquals(0, myResultsViewer.getFinishedTestCount());
    assertEquals(0, myResultsViewer.getTotalTestCount());
  }

  public void testOnTestStarted() {
    myResultsViewer.onTestStarted(createTestProxy("some_test", myTestsRootNode));
    assertEquals(1, myResultsViewer.getStartedTestCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(2, myResultsViewer.getStartedTestCount());
  }

  public void testCount() {
    myResultsViewer.onTestsCountInSuite(1);

    assertEquals(1, myResultsViewer.getTotalTestCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test", myTestsRootNode));
    assertEquals(1, myResultsViewer.getTotalTestCount());

    // if exceeds - will be incremented
    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(2, myResultsViewer.getTotalTestCount());
  }

  public void testCount_UnSet() {
    myResultsViewer.onTestStarted(createTestProxy("some_test", myTestsRootNode));
    assertEquals(0, myResultsViewer.getTotalTestCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(0, myResultsViewer.getTotalTestCount());

    // count will be updated only on tests finished if wasn't set
    myResultsViewer.onTestingFinished(myTestsRootNode);
    assertEquals(2, myResultsViewer.getTotalTestCount());
  }

  public void testOnTestFailure() {
    final SMTestProxy test = createTestProxy(myTestsRootNode);

    myResultsViewer.onTestStarted(test);
    myResultsViewer.onTestFailed(test);

    assertEquals(1, myResultsViewer.getFailedTestCount());
    assertEquals(1, myResultsViewer.getFailedTestCount());
  }

  public void testOnTestFinished() {
    final SMTestProxy test = createTestProxy("some_test", myTestsRootNode);

    myResultsViewer.onTestStarted(test);
    assertEquals(1, myResultsViewer.getStartedTestCount());

    myResultsViewer.onTestFinished(test);
    assertEquals(1, myResultsViewer.getFinishedTestCount());
  }

  public void testOnTestsCountInSuite() {
    myResultsViewer.onTestsCountInSuite(200);

    assertEquals(0, myResultsViewer.getFinishedTestCount());
    assertEquals(200, myResultsViewer.getTotalTestCount());

    myResultsViewer.onTestsCountInSuite(50);
    assertEquals(250, myResultsViewer.getTotalTestCount());
  }

  public void testOnTestStart_ChangeTotal() {
    myResultsViewer.onTestsCountInSuite(2);

    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(2, myResultsViewer.getTotalTestCount());
    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(2, myResultsViewer.getTotalTestCount());
    myResultsViewer.onTestStarted(createTestProxy("some_test3", myTestsRootNode));
    assertEquals(3, myResultsViewer.getTotalTestCount());
    myResultsViewer.onTestStarted(createTestProxy("some_test4", myTestsRootNode));
    assertEquals(4, myResultsViewer.getTotalTestCount());

    myResultsViewer.onTestsCountInSuite(2);
    myResultsViewer.onTestStarted(createTestProxy("another_test1", myTestsRootNode));
    assertEquals(6, myResultsViewer.getTotalTestCount());
    myResultsViewer.onTestStarted(createTestProxy("another_test2", myTestsRootNode));
    assertEquals(6, myResultsViewer.getTotalTestCount());
    myResultsViewer.onTestStarted(createTestProxy("another_test3", myTestsRootNode));
    assertEquals(7, myResultsViewer.getTotalTestCount());
  }

  public void testOnFinishTesting_EndTime() {
    myResultsViewer.onTestingFinished(myTestsRootNode);
    assertTrue(myResultsViewer.getEndTime() > 0);
  }

  public void testOnSuiteStarted() {
    assertEquals(0, myResultsViewer.getFinishedTestCount());
    myResultsViewer.onSuiteStarted(createSuiteProxy(myTestsRootNode));
    assertEquals(0, myResultsViewer.getFinishedTestCount());
  }

  public void testChangeSelectionAction() {
    final Marker onSelectedHappend = new Marker();
    final Ref<SMTestProxy> proxyRef = new Ref<>();
    final Ref<Boolean> focusRequestedRef = new Ref<>();

    myResultsViewer.setShowStatisticForProxyHandler(new PropagateSelectionHandler() {
      @Override
      public void handlePropagateSelectionRequest(@Nullable final SMTestProxy selectedTestProxy, @NotNull final Object sender,
                                                  final boolean requestFocus) {
        onSelectedHappend.set();
        proxyRef.set(selectedTestProxy);
        focusRequestedRef.set(requestFocus);
      }
    });

    final SMTestProxy suite = createSuiteProxy("suite", myTestsRootNode);
    final SMTestProxy test = createTestProxy("test", myTestsRootNode);
    myResultsViewer.onSuiteStarted(suite);
    myResultsViewer.onTestStarted(test);

    //On test
    myResultsViewer.selectAndNotify(test);
    myResultsViewer.showStatisticsForSelectedProxy();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(test, proxyRef.get());
    assertTrue(focusRequestedRef.get());

    //on suite
    //reset markers
    onSelectedHappend.reset();
    proxyRef.set(null);
    focusRequestedRef.set(null);

    myResultsViewer.selectAndNotify(suite);
    myResultsViewer.showStatisticsForSelectedProxy();
    assertTrue(onSelectedHappend.isSet());
    assertEquals(suite, proxyRef.get());
    assertTrue(focusRequestedRef.get());
  }

  public void testRuby_1767() throws InterruptedException {
    TestConsoleProperties.HIDE_PASSED_TESTS.set(myConsoleProperties, true);

    myEventsProcessor.onStartTesting();
    myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent("suite", null));
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestStarted(new TestStartedEvent("test_failed", null));
    myResultsViewer.performUpdate();
    myEventsProcessor.onTestFailure(new TestFailedEvent("test_failed", "", "", false, null, null));
    myResultsViewer.performUpdate();
    myEventsProcessor.onTestFinished(new TestFinishedEvent("test_failed", 10l));
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestStarted(new TestStartedEvent("test", null));
    myResultsViewer.performUpdate();
    assertEquals(2, myTreeModel.getChildCount(myTreeModel.getChild(myTreeModel.getRoot(), 0)));

    myEventsProcessor.onTestFinished(new TestFinishedEvent("test", 10l));
    assertEquals(2, myTreeModel.getChildCount(myTreeModel.getChild(myTreeModel.getRoot(), 0)));

    myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite"));
    myEventsProcessor.onFinishTesting();

    assertEquals(1, myTreeModel.getChildCount(myTreeModel.getChild(myTreeModel.getRoot(), 0)));
  }

  public void testExpandIfOnlyOneRootChild() throws InterruptedException {
    myEventsProcessor.onStartTesting();
    myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent("suite1", null));
    myResultsViewer.performUpdate();
    myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent("suite2", null));
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestStarted(new TestStartedEvent("test_failed", null));
    myResultsViewer.performUpdate();
    myEventsProcessor.onTestFailure(new TestFailedEvent("test_failed", "", "", false, null, null));
    myResultsViewer.performUpdate();
    myEventsProcessor.onTestFinished(new TestFinishedEvent("test_failed", 10l));
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestStarted(new TestStartedEvent("test", null));
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestFinished(new TestFinishedEvent("test", 10l));
    myResultsViewer.performUpdate();

    myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite2"));
    myResultsViewer.performUpdate();
    myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite1"));
    myResultsViewer.performUpdate();
    myEventsProcessor.onFinishTesting();
    myResultsViewer.performUpdate();

    final DefaultMutableTreeNode suite1Node =
      (DefaultMutableTreeNode)myTreeModel.getChild(myTreeModel.getRoot(), 0);
    final DefaultMutableTreeNode suite2Node =
      (DefaultMutableTreeNode)myTreeModel.getChild(suite1Node, 0);

    assertTrue(myResultsViewer.getTreeView().isExpanded(new TreePath(suite1Node.getPath())));
    assertFalse(myResultsViewer.getTreeView().isExpanded(new TreePath(suite2Node.getPath())));
  }

  //with test tree build before start actual tests
  public void testPrependTreeAndSameTestsStartFinish() throws Exception {
    //send tree
    myEventsProcessor.onSuiteTreeStarted("suite1", null, "suite1", "0");
    myEventsProcessor.onSuiteTreeNodeAdded("test1", null, "test1", "suite1");
    myEventsProcessor.onSuiteTreeEnded("suite1");

    //start testing
    myEventsProcessor.onStartTesting();
    
    //invocation count for method set to 2
    for(int i = 0; i < 2; i++) {
      myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent("suite1", null));

      myEventsProcessor.onTestStarted(new TestStartedEvent("test1", null));
      myResultsViewer.performUpdate();
      myEventsProcessor.onTestFailure(new TestFailedEvent("test1", "", "", false, "a", "b"));
      myResultsViewer.performUpdate();
      myEventsProcessor.onTestFinished(new TestFinishedEvent("test1", 10l));
      myResultsViewer.performUpdate();
      myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite1"));
      myResultsViewer.performUpdate();
    }

    myEventsProcessor.onFinishTesting();
    myResultsViewer.performUpdate();

    //ensure 2 nodes found
    assertEquals(2, myResultsViewer.getFailedTestCount());
  }

  public void testCustomProgress_General() {
    myResultsViewer.onCustomProgressTestsCategory("foo", 4);

    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(0, myResultsViewer.getFinishedTestCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(1, myResultsViewer.getStartedTestCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(1, myResultsViewer.getStartedTestCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getStartedTestCount());
  }

  public void testCustomProgress_MixedMde() {
    // enable custom mode
    myResultsViewer.onCustomProgressTestsCategory("foo", 4);

    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(0, myResultsViewer.getFinishedTestCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(1, myResultsViewer.getStartedTestCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(1, myResultsViewer.getStartedTestCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getStartedTestCount());

    // disable custom mode
    myResultsViewer.onCustomProgressTestsCategory(null, 0);

    assertEquals(2, myResultsViewer.getStartedTestCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getStartedTestCount());
    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(3, myResultsViewer.getStartedTestCount());

    assertEquals(3, myResultsViewer.getStartedTestCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(3, myResultsViewer.getStartedTestCount());
    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(4, myResultsViewer.getStartedTestCount());
  }

  public void testCustomProgress_EmptySuite() {
    myResultsViewer.onCustomProgressTestsCategory("foo", 0);

    final SMTestProxy suite = createSuiteProxy("some_suite", myTestsRootNode);
    myTestsRootNode.setStarted();

    myResultsViewer.onSuiteStarted(suite);
    suite.setStarted();
    suite.setFinished();
    myResultsViewer.onSuiteFinished(suite);

    myTestsRootNode.setFinished();
    myResultsViewer.onSuiteFinished(myTestsRootNode);
    
    myResultsViewer.onTestingFinished(myTestsRootNode);
    assertEquals(0, myResultsViewer.getTotalTestCount());
    assertEquals(Color.LIGHT_GRAY, myResultsViewer.getTestsStatusColor());
  }

  public void testCustomProgress_Failure() {
    myResultsViewer.onCustomProgressTestsCategory("foo", 4);

    final SMTestProxy test1 = createTestProxy("some_test1", myTestsRootNode);
    myResultsViewer.onTestStarted(test1);
    myResultsViewer.onCustomProgressTestStarted();

    myResultsViewer.onTestFailed(test1);
    assertEquals(0, myResultsViewer.getFailedTestCount());

    myResultsViewer.onCustomProgressTestFailed();
    assertEquals(1, myResultsViewer.getFailedTestCount());

    assertEquals(ColorProgressBar.RED, myResultsViewer.getTestsStatusColor());
  }

  public void testProgressBar_Ignored() {
    final SMTestProxy test1 = createTestProxy("some_test1", myTestsRootNode);
    myResultsViewer.onTestStarted(test1);
    myResultsViewer.performUpdate();
    myResultsViewer.onTestIgnored(test1);
    myResultsViewer.performUpdate();
    assertEquals(0, myResultsViewer.getFailedTestCount());
    assertEquals(1, myResultsViewer.getIgnoredTestCount());

    assertEquals(ColorProgressBar.GREEN, myResultsViewer.getTestsStatusColor());
  }

  public void testCustomProgress_Terminated() {
    myResultsViewer.onTestingStarted(myTestsRootNode);

    final SMTestProxy test1 = createTestProxy("some_test1", myTestsRootNode);
    myResultsViewer.onTestStarted(test1);

    myResultsViewer.onTestingFinished(myTestsRootNode);

    assertEquals(ColorProgressBar.GREEN, myResultsViewer.getTestsStatusColor());
  }

  public void testCustomProgress_NotRun() {
    myResultsViewer.onTestingStarted(myTestsRootNode);
    myResultsViewer.onTestingFinished(myTestsRootNode);

    assertEquals(Color.LIGHT_GRAY, myResultsViewer.getTestsStatusColor());
  }

  public void testCustomProgress_NotRun_ReporterAttached() {
    myResultsViewer.onTestingStarted(myTestsRootNode);
    myTestsRootNode.setTestsReporterAttached();
    myResultsViewer.onTestingFinished(myTestsRootNode);

    // e.g. reporter attached but tests were actually launched
    // seems cannot happen in current implementation but is expected behaviour
    // for future
    assertEquals(ColorProgressBar.RED, myResultsViewer.getTestsStatusColor());
  }

  public void testCustomProgress_Terminated_SmthFailed() {
    myResultsViewer.onTestingStarted(myTestsRootNode);

    final SMTestProxy test1 = createTestProxy("some_test1", myTestsRootNode);
    myResultsViewer.onTestStarted(test1);
    myResultsViewer.onTestFailed(test1);
    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    myResultsViewer.onTestingFinished(myTestsRootNode);

    assertEquals(ColorProgressBar.RED, myResultsViewer.getTestsStatusColor());
  }

  public void testCustomProgress_UnSetCount() {
    myResultsViewer.onCustomProgressTestsCategory("foo", 0);

    assertEquals(0, myResultsViewer.getTotalTestCount());

    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(0, myResultsViewer.getTotalTestCount());

    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(0, myResultsViewer.getTotalTestCount());

    // count will be updated only on tests finished if wasn't set
    myResultsViewer.onTestingFinished(myTestsRootNode);
    assertEquals(2, myResultsViewer.getTotalTestCount());
  }

  public void testCustomProgress_IncreaseCount() {
    myResultsViewer.onCustomProgressTestsCategory("foo", 1);

    assertEquals(1, myResultsViewer.getTotalTestCount());

    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(1, myResultsViewer.getTotalTestCount());

    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getTotalTestCount());
  }

  public void testCustomProgress_IncreaseCount_MixedMode() {
    // custom mode
    myResultsViewer.onCustomProgressTestsCategory("foo", 1);

    assertEquals(1, myResultsViewer.getTotalTestCount());

    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(1, myResultsViewer.getTotalTestCount());

    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getTotalTestCount());

    // disable custom mode
    myResultsViewer.onCustomProgressTestsCategory(null, 0);
    assertEquals(2, myResultsViewer.getTotalTestCount());

    myResultsViewer.onTestsCountInSuite(1);
    assertEquals(3, myResultsViewer.getTotalTestCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(3, myResultsViewer.getTotalTestCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(4, myResultsViewer.getTotalTestCount());
  }

  //TODO categories - mized

  public void testCustomProgress_MentionedCategories_CategoryWithoutName() {
    // enable custom mode
    assertTrue(myResultsViewer.getMentionedCategories().isEmpty());

    myResultsViewer.onCustomProgressTestsCategory("foo", 4);

    assertTrue(myResultsViewer.getMentionedCategories().isEmpty());
  }

  public void testCustomProgress_MentionedCategories_DefaultCategory() {
    // enable custom mode
    assertTrue(myResultsViewer.getMentionedCategories().isEmpty());

    myResultsViewer.onCustomProgressTestStarted();

    assertTrue(myResultsViewer.getMentionedCategories().isEmpty());
  }

  public void testCustomProgress_MentionedCategories_OneCustomCategory() {
    // enable custom mode
    myResultsViewer.onCustomProgressTestsCategory("Foo", 4);
    assertTrue(myResultsViewer.getMentionedCategories().isEmpty());

    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertTrue(myResultsViewer.getMentionedCategories().isEmpty());

    myResultsViewer.onCustomProgressTestStarted();
    assertSameElements(myResultsViewer.getMentionedCategories(), "Foo");

    // disable custom mode
    myResultsViewer.onCustomProgressTestsCategory(null, 0);
    assertSameElements(myResultsViewer.getMentionedCategories(), "Foo");
  }

  public void testCustomProgress_MentionedCategories_SeveralCategories() {
    // enable custom mode
    myResultsViewer.onCustomProgressTestsCategory("Foo", 4);
    assertTrue(myResultsViewer.getMentionedCategories().isEmpty());

    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertTrue(myResultsViewer.getMentionedCategories().isEmpty());

    myResultsViewer.onCustomProgressTestStarted();
    assertSameElements(myResultsViewer.getMentionedCategories(), "Foo");

    // disable custom mode
    myResultsViewer.onCustomProgressTestsCategory(null, 0);

    myResultsViewer.onCustomProgressTestStarted();
    assertSameElements(myResultsViewer.getMentionedCategories(), "Foo");

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertSameElements(myResultsViewer.getMentionedCategories(), "Foo", TestsPresentationUtil.DEFAULT_TESTS_CATEGORY);
  }

  public void testCustomProgress_MentionedCategories() {
    // enable custom mode
    assertTrue(myResultsViewer.getMentionedCategories().isEmpty());

    myResultsViewer.onCustomProgressTestsCategory("foo", 4);

    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(0, myResultsViewer.getStartedTestCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(1, myResultsViewer.getStartedTestCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(1, myResultsViewer.getStartedTestCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getStartedTestCount());

    // disable custom mode
    myResultsViewer.onCustomProgressTestsCategory(null, 0);

    assertEquals(2, myResultsViewer.getStartedTestCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getStartedTestCount());
    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(3, myResultsViewer.getStartedTestCount());

    assertEquals(3, myResultsViewer.getStartedTestCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(3, myResultsViewer.getStartedTestCount());
    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(4, myResultsViewer.getStartedTestCount());
  }
}
