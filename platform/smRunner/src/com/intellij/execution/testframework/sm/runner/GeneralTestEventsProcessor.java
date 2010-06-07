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

  void onTestStarted(@NotNull final String testName,
                     @Nullable final String locationUrl);

  void onTestFinished(@NotNull final String testName,
                      final int duration);

  void onTestFailure(@NotNull final String testName, 
                     @NotNull final String localizedMessage,
                     @Nullable final String stackTrace,
                     final boolean testError);

  void onTestIgnored(@NotNull final String testName,
                     @NotNull final String ignoreComment,
                     @Nullable final String stackTrace);

  void onTestOutput(@NotNull final String testName,
                    @NotNull final String text,
                    final boolean stdOut);

  void onSuiteStarted(@NotNull final String suiteName,
                      @Nullable final String locationUrl);

  void onSuiteFinished(@NotNull final String suiteName);

  void onUncapturedOutput(@NotNull final String text,
                          final Key outputType);

  void onError(@NotNull final String localizedMessage,
               @Nullable final String stackTrace);

  // Custom progress statistics

  /**
   * @param categoryName If isn't empty then progress statistics will use only custom start/failed events.
   * If name is null statistics will be switched to normal mode
   * @param testCount - 0 will be considered as unknown tests number
   */
  void onCustomProgressTestsCategory(@Nullable final String categoryName,
                                     final int testCount);
  void onCustomProgressTestStarted();
  void onCustomProgressTestFailed();
}