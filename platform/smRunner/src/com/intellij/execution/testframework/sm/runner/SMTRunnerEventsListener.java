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

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Roman Chernyatchik
 *
 * Handles Test Runner events
*/
public interface SMTRunnerEventsListener {
  Topic<SMTRunnerEventsListener> TEST_STATUS = new Topic<>("test status", SMTRunnerEventsListener.class);

  /**
   * On start testing, before tests and suits launching
   * @param testsRoot
   */
  void onTestingStarted(@NotNull SMTestProxy.SMRootTestProxy testsRoot);
  /**
   * After test framework finish testing
   * @param testsRootNode
   */
  void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy testsRoot);
  /*
   * Tests count in next suite. For several suites this method will be invoked several times
   */
  void onTestsCountInSuite(int count);

  void onTestStarted(@NotNull SMTestProxy test);
  void onTestFinished(@NotNull SMTestProxy test);
  void onTestFailed(@NotNull SMTestProxy test);
  void onTestIgnored(@NotNull SMTestProxy test);

  void onSuiteFinished(@NotNull SMTestProxy suite);
  void onSuiteStarted(@NotNull SMTestProxy suite);

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
  void onSuiteTreeStarted(SMTestProxy suite);

}
