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
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.Marker;
import com.intellij.execution.testframework.sm.runner.BaseSMTRunnerTestCase;
import com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
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
  private SMTestProxy myTestsRootNode;

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

    myConsole = new SMTRunnerConsoleView(myConsoleProperties, environment.getRunnerSettings(), environment.getConfigurationSettings());
    myConsole.initUI();
    myResultsViewer = myConsole.getResultsViewer();
    myTestsRootNode = myResultsViewer.getTestsRootNode();
    myEventsProcessor = new GeneralToSMTRunnerEventsConvertor(myResultsViewer.getTestsRootNode(), "SMTestFramework");
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
    assertEquals(0, myResultsViewer.getTestsCurrentCount());
    assertEquals(0, myResultsViewer.getTestsTotal());
  }

  public void testOnTestStarted() {
    myResultsViewer.onTestStarted(createTestProxy("some_test", myTestsRootNode));
    assertEquals(1, myResultsViewer.getTestsCurrentCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(2, myResultsViewer.getTestsCurrentCount());
  }

  public void testCount() {
    myResultsViewer.onTestsCountInSuite(1);

    assertEquals(1, myResultsViewer.getTestsTotal());

    myResultsViewer.onTestStarted(createTestProxy("some_test", myTestsRootNode));
    assertEquals(1, myResultsViewer.getTestsTotal());

    // if exceeds - will be incremented
    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(2, myResultsViewer.getTestsTotal());
  }

  public void testCount_UnSet() {
    myResultsViewer.onTestStarted(createTestProxy("some_test", myTestsRootNode));
    assertEquals(0, myResultsViewer.getTestsTotal());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(0, myResultsViewer.getTestsTotal());

    // count will be updated only on tests finished if wasn't set
    myResultsViewer.onTestingFinished(myTestsRootNode);
    assertEquals(2, myResultsViewer.getTestsTotal());
  }

  public void testOnTestFailure() {
    final SMTestProxy test = createTestProxy(myTestsRootNode);

    myResultsViewer.onTestStarted(test);
    myResultsViewer.onTestFailed(test);

    assertEquals(1, myResultsViewer.getTestsCurrentCount());
  }

  public void testOnTestFinished() {
    final SMTestProxy test = createTestProxy("some_test", myTestsRootNode);

    myResultsViewer.onTestStarted(test);
    assertEquals(1, myResultsViewer.getTestsCurrentCount());

    myResultsViewer.onTestFinished(test);
    assertEquals(1, myResultsViewer.getTestsCurrentCount());
  }

  public void testOnTestsCountInSuite() {
    myResultsViewer.onTestsCountInSuite(200);

    assertEquals(0, myResultsViewer.getTestsCurrentCount());
    assertEquals(200, myResultsViewer.getTestsTotal());

    myResultsViewer.onTestsCountInSuite(50);
    assertEquals(250, myResultsViewer.getTestsTotal());
  }

  public void testOnTestStart_ChangeTotal() {
    myResultsViewer.onTestsCountInSuite(2);

    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(2, myResultsViewer.getTestsTotal());
    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(2, myResultsViewer.getTestsTotal());
    myResultsViewer.onTestStarted(createTestProxy("some_test3", myTestsRootNode));
    assertEquals(3, myResultsViewer.getTestsTotal());
    myResultsViewer.onTestStarted(createTestProxy("some_test4", myTestsRootNode));
    assertEquals(4, myResultsViewer.getTestsTotal());

    myResultsViewer.onTestsCountInSuite(2);
    myResultsViewer.onTestStarted(createTestProxy("another_test1", myTestsRootNode));
    assertEquals(6, myResultsViewer.getTestsTotal());
    myResultsViewer.onTestStarted(createTestProxy("another_test2", myTestsRootNode));
    assertEquals(6, myResultsViewer.getTestsTotal());
    myResultsViewer.onTestStarted(createTestProxy("another_test3", myTestsRootNode));
    assertEquals(7, myResultsViewer.getTestsTotal());
  }

  public void testOnFinishTesting_EndTime() {
    myResultsViewer.onTestingFinished(myTestsRootNode);
    assertTrue(myResultsViewer.getEndTime() > 0);
  }

  public void testOnSuiteStarted() {
    assertEquals(0, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onSuiteStarted(createSuiteProxy(myTestsRootNode));
    assertEquals(0, myResultsViewer.getTestsCurrentCount());
  }

  public void testChangeSelectionAction() {
    final Marker onSelectedHappend = new Marker();
    final Ref<SMTestProxy> proxyRef = new Ref<SMTestProxy>();
    final Ref<Boolean> focusRequestedRef = new Ref<Boolean>();

    myResultsViewer.setShowStatisticForProxyHandler(new PropagateSelectionHandler() {
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
    myEventsProcessor.onSuiteStarted("suite", null);
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestStarted("test_failed", null);
    myResultsViewer.performUpdate();
    myEventsProcessor.onTestFailure("test_failed", "", "", false);
    myResultsViewer.performUpdate();
    myEventsProcessor.onTestFinished("test_failed", 10);
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestStarted("test", null);
    myResultsViewer.performUpdate();
    assertEquals(2, myTreeModel.getChildCount(myTreeModel.getChild(myTreeModel.getRoot(), 0)));

    myEventsProcessor.onTestFinished("test", 10);
    assertEquals(2, myTreeModel.getChildCount(myTreeModel.getChild(myTreeModel.getRoot(), 0)));

    myEventsProcessor.onSuiteFinished("suite");
    myEventsProcessor.onFinishTesting();

    assertEquals(1, myTreeModel.getChildCount(myTreeModel.getChild(myTreeModel.getRoot(), 0)));
  }

  public void testExpandIfOnlyOneRootChild() throws InterruptedException {
    myEventsProcessor.onStartTesting();
    myEventsProcessor.onSuiteStarted("suite1", null);
    myResultsViewer.performUpdate();
    myEventsProcessor.onSuiteStarted("suite2", null);
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestStarted("test_failed", null);
    myResultsViewer.performUpdate();
    myEventsProcessor.onTestFailure("test_failed", "", "", false);
    myResultsViewer.performUpdate();
    myEventsProcessor.onTestFinished("test_failed", 10);
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestStarted("test", null);
    myResultsViewer.performUpdate();

    myEventsProcessor.onTestFinished("test", 10);
    myResultsViewer.performUpdate();

    myEventsProcessor.onSuiteFinished("suite2");
    myResultsViewer.performUpdate();
    myEventsProcessor.onSuiteFinished("suite1");
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

  public void testCustomProgress_General() {
    myResultsViewer.onCustomProgressTestsCategory("foo", 4);

    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(0, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(1, myResultsViewer.getTestsCurrentCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(1, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getTestsCurrentCount());
  }

  public void testCustomProgress_MixedMde() {
    // enable custom mode
    myResultsViewer.onCustomProgressTestsCategory("foo", 4);

    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(0, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(1, myResultsViewer.getTestsCurrentCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(1, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getTestsCurrentCount());

    // disable custom mode
    myResultsViewer.onCustomProgressTestsCategory(null, 0);

    assertEquals(2, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(3, myResultsViewer.getTestsCurrentCount());

    assertEquals(3, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(3, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(4, myResultsViewer.getTestsCurrentCount());
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
    assertEquals(0, myResultsViewer.getTestsTotal());
    assertEquals(Color.LIGHT_GRAY, myResultsViewer.getTestsStatusColor());
  }

  public void testCustomProgress_Failure() {
    myResultsViewer.onCustomProgressTestsCategory("foo", 4);

    final SMTestProxy test1 = createTestProxy("some_test1", myTestsRootNode);
    myResultsViewer.onTestStarted(test1);
    myResultsViewer.onCustomProgressTestStarted();

    myResultsViewer.onTestFailed(test1);
    assertEquals(0, myResultsViewer.getTestsFailuresCount());

    myResultsViewer.onCustomProgressTestFailed();
    assertEquals(1, myResultsViewer.getTestsFailuresCount());

    assertEquals(ColorProgressBar.RED, myResultsViewer.getTestsStatusColor());
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

    assertEquals(ColorProgressBar.RED, myResultsViewer.getTestsStatusColor());
  }

  public void testCustomProgress_UnSetCount() {
    myResultsViewer.onCustomProgressTestsCategory("foo", 0);

    assertEquals(0, myResultsViewer.getTestsTotal());

    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(0, myResultsViewer.getTestsTotal());

    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(0, myResultsViewer.getTestsTotal());

    // count will be updated only on tests finished if wasn't set
    myResultsViewer.onTestingFinished(myTestsRootNode);
    assertEquals(2, myResultsViewer.getTestsTotal());
  }

  public void testCustomProgress_IncreaseCount() {
    myResultsViewer.onCustomProgressTestsCategory("foo", 1);

    assertEquals(1, myResultsViewer.getTestsTotal());

    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(1, myResultsViewer.getTestsTotal());

    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getTestsTotal());
  }

  public void testCustomProgress_IncreaseCount_MixedMode() {
    // custom mode
    myResultsViewer.onCustomProgressTestsCategory("foo", 1);

    assertEquals(1, myResultsViewer.getTestsTotal());

    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(1, myResultsViewer.getTestsTotal());

    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getTestsTotal());

    // disable custom mode
    myResultsViewer.onCustomProgressTestsCategory(null, 0);
    assertEquals(2, myResultsViewer.getTestsTotal());

    myResultsViewer.onTestsCountInSuite(1);
    assertEquals(3, myResultsViewer.getTestsTotal());

    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(3, myResultsViewer.getTestsTotal());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(4, myResultsViewer.getTestsTotal());
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
    assertEquals(0, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(1, myResultsViewer.getTestsCurrentCount());

    myResultsViewer.onTestStarted(createTestProxy("some_test2", myTestsRootNode));
    assertEquals(1, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getTestsCurrentCount());

    // disable custom mode
    myResultsViewer.onCustomProgressTestsCategory(null, 0);

    assertEquals(2, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(2, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(3, myResultsViewer.getTestsCurrentCount());

    assertEquals(3, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onCustomProgressTestStarted();
    assertEquals(3, myResultsViewer.getTestsCurrentCount());
    myResultsViewer.onTestStarted(createTestProxy("some_test1", myTestsRootNode));
    assertEquals(4, myResultsViewer.getTestsCurrentCount());
  }
}
