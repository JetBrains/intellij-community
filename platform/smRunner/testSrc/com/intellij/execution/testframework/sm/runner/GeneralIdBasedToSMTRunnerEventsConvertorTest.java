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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.AbstractTestProxy.SuiteResultPolicy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestIgnoredEvent;
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent;
import com.intellij.execution.testframework.sm.runner.events.TreeNodeEvent;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneralIdBasedToSMTRunnerEventsConvertorTest extends BaseSMTRunnerTestCase {
  private SMTRunnerConsoleView myConsole;
  private GeneralIdBasedToSMTRunnerEventsConvertor myEventsProcessor;
  private SMTestProxy.SMRootTestProxy myRootProxy;
  private SMTestRunnerResultsForm myResultsViewer;

  @Test
  public void testRetrySuiteUpdatesAnExistingNode() {
    var root = new SMTestProxy.SMRootTestProxy();
    var processor = new GeneralIdBasedToSMTRunnerEventsConvertor(getProject(), root, "test");
    try {
      processor.onStartTesting();
      var treeEvent = new TestSuiteStartedEvent("retry", "1", TreeNodeEvent.ROOT_NODE_ID,
                                                null, null, null, null, false);
      treeEvent.setSuiteResultPolicy(SuiteResultPolicy.LAST_CHILD);
      processor.onSuiteStarted(treeEvent);
      var started = new TestSuiteStartedEvent("retry", "1", TreeNodeEvent.ROOT_NODE_ID,
                                              null, null, null, null, true);
      processor.onSuiteStarted(started);
      processor.onTestStarted(new TestStartedEvent("first", "2", "1", null, null, null, null, true));
      processor.onTestFailure(new TestFailedEvent(null, "2", "failure", "trace", false,
                                                  null, null, null, null, false, false, -1));
      processor.onTestFinished(new TestFinishedEvent(null, "2", 1L));
      assertFalse(root.isDefect());
      processor.onTestStarted(new TestStartedEvent("last", "3", "1", null, null, null, null, true));
      processor.onTestFinished(new TestFinishedEvent(null, "3", 1L));
      processor.onSuiteFinished(new TestSuiteFinishedEvent("retry", "1"));
      processor.onFinishTesting();

      assertEquals(SuiteResultPolicy.LAST_CHILD, root.getChildren().getFirst().getSuiteResultPolicy());
      assertTrue(root.getChildren().getFirst().isPassed());
      assertTrue(root.isPassed());
    }
    finally {
      Disposer.dispose(processor);
      Disposer.dispose(root);
    }
  }

  @BeforeEach
  void setUp() {
    runInEdtAndWait(() -> {
      TestConsoleProperties consoleProperties = createConsoleProperties();
      myConsole = new SMTRunnerConsoleView(consoleProperties);
      myConsole.initUI();
      myResultsViewer = myConsole.getResultsViewer();

      myRootProxy = new SMTestProxy.SMRootTestProxy();
      myEventsProcessor = new GeneralIdBasedToSMTRunnerEventsConvertor(getProject(), myRootProxy, "test");
      myEventsProcessor.addEventsListener(myResultsViewer);
      myEventsProcessor.onStartTesting();
    });
  }

  @AfterEach
  void tearDown() {
    runInEdtAndWait(() -> {
      Disposer.dispose(myEventsProcessor);
      Disposer.dispose(myConsole);
    });
  }

  @Test
  public void testOnStartedTesting() {
    runInEdtAndWait(() -> {
      assertTrue(myRootProxy.wasLaunched());
      assertTrue(myRootProxy.isInProgress());
      assertTrue(myRootProxy.isLeaf());
    });
  }

  @Test
  public void testOnTestStarted() {
    runInEdtAndWait(() -> {
      onTestStarted("my test", null, "1", TreeNodeEvent.ROOT_NODE_ID, true);
      assertStatusLine("");
      SMTestProxy proxy = validateTest("1", "my test", null, true, myRootProxy);
      onTestFailed("1", "", 1);
      assertStatusLine("1 test failed");
      validateTestFailure("1", proxy, 1);
    });
  }

  @Test
  public void testRunningSuite() {
    runInEdtAndWait(() -> {
      onSuiteStarted("Code", null, "1", TreeNodeEvent.ROOT_NODE_ID);
      SMTestProxy suiteProxy = validateSuite("1", "Code", null, myRootProxy);
      onTestStarted("should work", null, "2", "1", true);
      SMTestProxy testProxy = validateTest("2", "should work", null, true, suiteProxy);
      onTestFailed("2", "NPE", 5);
      validateTestFailure("2", testProxy, 5);
      assertTrue(suiteProxy.isInProgress());

      onSuiteStarted("Bugs", null, "3", TreeNodeEvent.ROOT_NODE_ID);
      SMTestProxy bugsSuiteProxy = validateSuite("3", "Bugs", null, myRootProxy);
      onTestStarted("should be fixed", null, "4", "3", false);
      validateTest("4", "should be fixed", null, false, bugsSuiteProxy);
      assertFalse(bugsSuiteProxy.isInProgress());
    });
  }

  @Test
  public void testRunningSuiteWithMetainfo() {
    runInEdtAndWait(() -> {
      onSuiteStarted("Code", "any:info:string:that:can:help?navigation", "1", TreeNodeEvent.ROOT_NODE_ID);
      SMTestProxy suiteProxy = validateSuite("1", "Code", "any:info:string:that:can:help?navigation", myRootProxy);
      onTestStarted("should work", "but is not a part of primary key", "2", "1", true);
      SMTestProxy testProxy = validateTest("2", "should work", "but is not a part of primary key", true, suiteProxy);
      onTestFailed("2", "NPE", 5);
      validateTestFailure("2", testProxy, 5);
      assertTrue(suiteProxy.isInProgress());
    });
  }

  @Test
  public void testIgnoredEvent() {
    runInEdtAndWait(() -> {
      onSuiteStarted("Suite", null, "1", TreeNodeEvent.ROOT_NODE_ID);
      SMTestProxy suite = validateSuite("1", "Suite", null, myRootProxy);
      onTestStarted("testA", null, "A", "1", true);
      SMTestProxy testA = validateTest("A", "testA", null, true, suite);
      onTestIgnored("A");
      validateTestIgnored("A", testA);
      assertFalse(testA.isInProgress());
      assertTrue(suite.isInProgress());
      assertEquals(1, myResultsViewer.getFinishedTestCount());
      onTestFinished("A", null);
      assertEquals(1, myResultsViewer.getFinishedTestCount());

      onTestStarted("testB", null, "B", "1", true);
      SMTestProxy testB = validateTest("B", "testB", null, true, suite);
      assertEquals(1, myResultsViewer.getFinishedTestCount());
      onTestIgnored("B");
      assertStatusLine("2 tests ignored");
      assertEquals(2, myResultsViewer.getFinishedTestCount());
      validateTestIgnored("B", testB);
    });
  }

  @Test
  public void testCheckMetaUpdateWithValue() {
    runInEdtAndWait(() -> checkMetainfo("suiteMetaUpdate", "testMetaUpdate"));
  }

  @Test
  public void testCheckMetaUpdateWithNull() {
    runInEdtAndWait(() -> checkMetainfo(null, null));
  }

  private void checkMetainfo(@Nullable String updatedSuiteMetainfo, @Nullable String updatedTestMetainfo) {
    final String suiteName = "some_suite";
    final String testName = "some_test";
    final String initSuiteMeta = "suiteMeta";
    final String initTestMeta = "testMeta";

    myEventsProcessor.onSuiteTreeNodeAdded(true, suiteName, suiteName, initSuiteMeta, suiteName, TreeNodeEvent.ROOT_NODE_ID);
    myEventsProcessor.onSuiteTreeNodeAdded(false, testName, testName, initTestMeta, testName, suiteName);
    //myEventsProcessor.onSuiteTreeEnded(testName);
    //myEventsProcessor.onSuiteTreeEnded(suiteName);
    myEventsProcessor.onBuildTreeEnded();

    myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent(
      suiteName,
      suiteName,
      TreeNodeEvent.ROOT_NODE_ID,
      suiteName,
      updatedSuiteMetainfo,
      null,
      null,
      true));
    myEventsProcessor.onTestStarted(new TestStartedEvent(
      testName,
      testName,
      suiteName,
      testName,
      updatedTestMetainfo,
      null,
      null,
      true));

    myResultsViewer.performUpdate();
    PlatformTestUtil.waitWhileBusy(myResultsViewer.getTreeView());

    final SMTestProxy suiteProxy = myEventsProcessor.findProxyById(suiteName);
    assertEquals(updatedSuiteMetainfo == null ? initSuiteMeta : updatedSuiteMetainfo, suiteProxy.getMetainfo());

    final SMTestProxy testProxy = myEventsProcessor.findProxyById(testName);
    assertEquals(updatedTestMetainfo == null ? initTestMeta : updatedTestMetainfo, testProxy.getMetainfo());
  }

  @NotNull
  private SMTestProxy validateSuite(@NotNull String id,
                                    @NotNull String expectedName,
                                    @Nullable String expectedMetainfo,
                                    @NotNull SMTestProxy parentProxy) {
    SMTestProxy suite = myEventsProcessor.findProxyById(id);
    assertNotNull(suite);
    assertTrue(suite.isSuite());
    assertEquals(expectedName, suite.getName());
    assertEquals(expectedMetainfo, suite.getMetainfo());
    assertFalse(suite.isInProgress());
    assertTrue(parentProxy.getChildren().contains(suite));
    return suite;
  }

  @NotNull
  private SMTestProxy validateTest(@NotNull String id,
                                   @NotNull String expectedName,
                                   @Nullable String expectedMetainfo,
                                   boolean inProgress,
                                   @NotNull SMTestProxy parent) {
    SMTestProxy suite = myEventsProcessor.findProxyById(id);
    assertNotNull(suite);
    assertFalse(suite.isSuite());
    assertTrue(suite.isLeaf());
    assertEquals(expectedName, suite.getName());
    assertEquals(expectedMetainfo, suite.getMetainfo());
    assertEquals(inProgress, suite.isInProgress());
    assertTrue(parent.getChildren().contains(suite));
    return suite;
  }

  @NotNull
  private SMTestProxy validateTestFailure(@NotNull String id,
                                          @NotNull SMTestProxy expectedTestProxy,
                                          long expectedDurationMillis) {
    SMTestProxy test = myEventsProcessor.findProxyById(id);
    assertEquals(expectedTestProxy, test);
    assertFalse(test.isSuite());
    assertTrue(test.isFinal());
    assertTrue(test.isDefect());
    assertEquals(Long.valueOf(expectedDurationMillis), test.getDuration());
    return test;
  }

  private void validateTestIgnored(@NotNull String id, @NotNull SMTestProxy expectedTestProxy) {
    SMTestProxy test = myEventsProcessor.findProxyById(id);
    assertEquals(expectedTestProxy, test);
    assertFalse(test.isSuite());
    assertTrue(test.isFinal());
    assertTrue(test.isIgnored());
  }

  private void onSuiteStarted(@NotNull String suiteName, @Nullable String metainfo, @NotNull String id, @NotNull String parentId) {
    myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent(suiteName, id, parentId, null, metainfo, null, null, false));
  }

  private void onTestStarted(@NotNull String testName,
                             @Nullable String metainfo,
                             @NotNull String id,
                             @NotNull String parentId,
                             boolean running) {
    myEventsProcessor.onTestStarted(new TestStartedEvent(testName, id, parentId, null, metainfo, null, null, running));
  }

  private void onTestFinished(@NotNull String id, @Nullable Long duration) {
    myEventsProcessor.onTestFinished(new TestFinishedEvent(null, id, duration));
  }

  private void onTestFailed(@NotNull String id, @NotNull String errorMessage, int durationMillis) {
    myEventsProcessor.onTestFailure(new TestFailedEvent(null, id, errorMessage, null, false, null,
                                                        null, null, null, false, false, durationMillis));
  }

  private void onTestIgnored(@NotNull String id) {
    myEventsProcessor.onTestIgnored(new TestIgnoredEvent(null, id, null, null));
  }

  private void assertStatusLine(@NotNull String expectedTestStatus) {
    assertEquals(expectedTestStatus, myResultsViewer.getStatusLine().getStateText());
  }
}
