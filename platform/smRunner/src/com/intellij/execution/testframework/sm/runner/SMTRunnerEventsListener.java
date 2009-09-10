package com.intellij.execution.testframework.sm.runner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 *
 * Handles Test Runner events
*/
public interface SMTRunnerEventsListener {
  /**
   * On start testing, before tests and suits launching
   * @param testsRoot
   */
  void onTestingStarted(@NotNull SMTestProxy testsRoot);
  /**
   * After test framework finish testing
   * @param testsRootNode
   */
  void onTestingFinished(@NotNull SMTestProxy testsRoot);
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
}
