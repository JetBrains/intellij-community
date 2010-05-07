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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author: Roman Chernyatchik
 *
 * Processes events of test runner in general text-based form
 *
 * NB: Test name should be unique for all suites. E.g. it can consist of suite name
 * and name of test method
 */
public interface GeneralTestEventsProcessor extends Disposable {
  void onTestsCountInSuite(final int count);

  void onTestStarted(final String testName, @Nullable final String locationUrl);
  void onTestFinished(final String testName, final int duration);
  void onTestFailure(final String testName, final String localizedMessage, final String stackTrace,
                     final boolean testError);
  void onTestIgnored(final String testName, final String ignoreComment, @Nullable final String stackTrace);
  void onTestOutput(final String testName, final String text, final boolean stdOut);

  void onSuiteStarted(final String suiteName, @Nullable final String locationUrl);
  void onSuiteFinished(final String suiteName);

  void onUncapturedOutput(final String text, final Key outputType);
  void onError(@NotNull final String localizedMessage, @Nullable final String stackTrace);

  // Custom progress statistics

  /**
   * @param categoryName If isn't empty then progress statistics will use only custom start/failed events.
   * If name is null statistics will be switched to normal mode
   * @param testCount - 0 will be considered as unknown tests number
   */
  void onCustomProgressTestsCategory(@Nullable final String categoryName, final int testCount);
  void onCustomProgressTestStarted();
  void onCustomProgressTestFailed();
}