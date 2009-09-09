package com.intellij.execution.testframework.sm.runner;

import org.jetbrains.annotations.NotNull;

/**
 * @author Roman Chernyatchik
*/
public class SMTRunnerEventsAdapter implements SMTRunnerEventsListener {
  public void onTestingStarted(@NotNull SMTestProxy testsRoot){}
  public void onTestingFinished(@NotNull SMTestProxy testsRoot){}
  public void onTestsCountInSuite(final int count) {}

  public void onTestStarted(@NotNull final SMTestProxy test) {}
  public void onTestFinished(@NotNull final SMTestProxy test) {}
  public void onTestFailed(@NotNull final SMTestProxy test) {}
  public void onTestIgnored(@NotNull final SMTestProxy test) {}

  public void onSuiteStarted(@NotNull final SMTestProxy suite) {}
  public void onSuiteFinished(@NotNull final SMTestProxy suite) {}
}
