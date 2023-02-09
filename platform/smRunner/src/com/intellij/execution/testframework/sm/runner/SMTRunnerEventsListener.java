// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.sm.runner.events.TestOutputEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSetNodePropertyEvent;
import com.intellij.openapi.util.Key;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 *
 * Handles Test Runner events
*/
public interface SMTRunnerEventsListener {
  Topic<SMTRunnerEventsListener> TEST_STATUS = new Topic<>("test status", SMTRunnerEventsListener.class);

  /**
   * On start testing, before tests and suits launching
   */
  void onTestingStarted(@NotNull SMTestProxy.SMRootTestProxy testsRoot);

  /**
   * Called before {@link #onTestingFinished(SMTestProxy.SMRootTestProxy)}
   */
  default void onBeforeTestingFinished(@NotNull SMTestProxy.SMRootTestProxy testsRoot) {

  }
  /**
   * After test framework finish testing.
   * @see #onBeforeTestingFinished(SMTestProxy.SMRootTestProxy)
   */
  void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy testsRoot);
  /*
   * Tests count in next suite. For several suites this method will be invoked several times
   */
  void onTestsCountInSuite(int count);

  void onTestStarted(@NotNull SMTestProxy test);
  default void onTestStarted(@NotNull SMTestProxy test, @Nullable String nodeId, @Nullable String parentNodeId) {
    onTestStarted(test);
  }
  void onTestFinished(@NotNull SMTestProxy test);
  default void onTestFinished(@NotNull SMTestProxy test, @Nullable String nodeId) {
    onTestFinished(test);
  }
  void onTestFailed(@NotNull SMTestProxy test);
  default void onTestFailed(@NotNull SMTestProxy test, @Nullable String nodeId) {
    onTestFailed(test);
  }
  void onTestIgnored(@NotNull SMTestProxy test);
  default void onTestIgnored(@NotNull SMTestProxy test, @Nullable String nodeId) {
    onTestIgnored(test);
  }

  void onSuiteFinished(@NotNull SMTestProxy suite);
  default void onSuiteFinished(@NotNull SMTestProxy suite, @Nullable String nodeId) {
    onSuiteFinished(suite);
  }
  void onSuiteStarted(@NotNull SMTestProxy suite);
  default void onSuiteStarted(@NotNull SMTestProxy suite, @Nullable String nodeId, @Nullable String parentNodeId) {
    onSuiteStarted(suite);
  } 

  // Custom progress statistics

  /**
   * @param categoryName If isn't empty then progress statistics will use only custom start/failed events.
   * If name is empty string statistics will be switched to normal mode
   * @param testCount - 0 will be considered as unknown tests number
   */
  void onCustomProgressTestsCategory(@Nullable final String categoryName, final int testCount);
  void onCustomProgressTestStarted();
  void onCustomProgressTestFailed();
  void onCustomProgressTestFinished();

  void onSuiteTreeNodeAdded(SMTestProxy testProxy);
  default void onSuiteTreeNodeAdded(SMTestProxy testProxy, boolean isSuite, @Nullable String nodeId, @Nullable String parentNodeId) {
    onSuiteTreeNodeAdded(testProxy);
  }

  default void onSuiteTreeEnded(SMTestProxy.SMRootTestProxy testsRootProxy, String suiteName) { }

  void onSuiteTreeStarted(SMTestProxy suite);

  default void onSuiteTreeStarted(SMTestProxy suite, @Nullable String nodeId, @Nullable String parentNodeId) {
    onSuiteTreeStarted(suite);
  }

  default void onBuildTreeEnded(SMTestProxy.SMRootTestProxy testsRootProxy) { }

  default void onRootPresentationAdded(@NotNull SMTestProxy.SMRootTestProxy testsRoot,
                                       String rootName,
                                       String comment,
                                       String rootLocation) { }

  default void onTestOutput(@NotNull SMTestProxy proxy, @NotNull TestOutputEvent event) { }

  default void onUncapturedOutput(@NotNull SMTestProxy activeProxy, String text, Key type) { }

  default void onSetNodeProperty(@NotNull SMTestProxy nodeProxy, @NotNull TestSetNodePropertyEvent event) { }
}
