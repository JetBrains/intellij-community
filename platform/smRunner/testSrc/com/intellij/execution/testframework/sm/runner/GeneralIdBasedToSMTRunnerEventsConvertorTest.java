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

import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent;
import com.intellij.execution.testframework.sm.runner.events.TreeNodeEvent;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GeneralIdBasedToSMTRunnerEventsConvertorTest extends BaseSMTRunnerTestCase {
  private GeneralIdBasedToSMTRunnerEventsConvertor myEventsProcessor;
  private SMTestProxy.SMRootTestProxy myRootProxy;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRootProxy = new SMTestProxy.SMRootTestProxy();
    myEventsProcessor = new GeneralIdBasedToSMTRunnerEventsConvertor(getProject(), myRootProxy, "test");
    myEventsProcessor.onStartTesting();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myEventsProcessor);
    }
    finally {
      super.tearDown();
    }
  }

  public void testOnStartedTesting() {
    assertTrue(myRootProxy.wasLaunched());
    assertTrue(myRootProxy.isInProgress());
    assertTrue(myRootProxy.isLeaf());
  }

  public void testOnTestStarted() {
    onTestStarted("my test", null, "1", TreeNodeEvent.ROOT_NODE_ID, true);
    SMTestProxy proxy = validateTest("1", "my test", null, true, myRootProxy);
    onTestFailed("1", "", 1);
    validateTestFailure("1", proxy, 1);
  }

  public void testRunningSuite() {
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
  }

  public void testRunningSuiteWithMetainfo() {
    onSuiteStarted("Code", "any:info:string:that:can:help?navigation", "1", TreeNodeEvent.ROOT_NODE_ID);
    SMTestProxy suiteProxy = validateSuite("1", "Code", "any:info:string:that:can:help?navigation", myRootProxy);
    onTestStarted("should work", "but is not a part of primary key", "2", "1", true);
    SMTestProxy testProxy = validateTest("2", "should work", "but is not a part of primary key", true, suiteProxy);
    onTestFailed("2", "NPE", 5);
    validateTestFailure("2", testProxy, 5);
    assertTrue(suiteProxy.isInProgress());
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

  private void onTestFailed(@NotNull String id, @NotNull String errorMessage, int durationMillis) {
    myEventsProcessor.onTestFailure(new TestFailedEvent(null, id, errorMessage, null, false, null,
                                                        null, null, null, false, false, durationMillis));
  }

}
