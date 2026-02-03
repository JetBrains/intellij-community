// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
*/
public class SMTRunnerEventsAdapter implements SMTRunnerEventsListener {
  @Override
  public void onTestingStarted(@NotNull SMTestProxy.SMRootTestProxy testsRoot){}
  @Override
  public void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy testsRoot){}
  @Override
  public void onTestsCountInSuite(final int count) {}

  @Override
  public void onTestStarted(final @NotNull SMTestProxy test) {}
  @Override
  public void onTestFinished(final @NotNull SMTestProxy test) {}
  @Override
  public void onTestFailed(final @NotNull SMTestProxy test) {}
  @Override
  public void onTestIgnored(final @NotNull SMTestProxy test) {}

  @Override
  public void onSuiteStarted(final @NotNull SMTestProxy suite) {}
  @Override
  public void onSuiteFinished(final @NotNull SMTestProxy suite) {}

  // Custom progress status

  @Override
  public void onCustomProgressTestsCategory(@Nullable String categoryName, final int testCount) {}
  @Override
  public void onCustomProgressTestStarted() {}
  @Override
  public void onCustomProgressTestFailed() {}
  @Override
  public void onCustomProgressTestFinished() {}

  @Override public void onSuiteTreeNodeAdded(SMTestProxy testProxy) {}
  @Override public void onSuiteTreeStarted(SMTestProxy suite) {}
}
