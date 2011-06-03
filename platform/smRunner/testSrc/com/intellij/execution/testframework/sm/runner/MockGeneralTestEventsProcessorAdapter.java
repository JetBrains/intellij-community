/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Roman.Chernyatchik
*/
public class MockGeneralTestEventsProcessorAdapter implements GeneralTestEventsProcessor {
  private final StringBuilder myOutputBuffer = new StringBuilder();
  @Override
  public void onTestsCountInSuite(int count) {
  }

  @Override
  public void onTestStarted(@NotNull String testName, @Nullable String locationUrl) {
  }

  @Override
  public void onTestFinished(@NotNull String testName, int duration) {
  }

  @Override
  public void onTestFailure(@NotNull String testName,
                            @NotNull String localizedMessage,
                            @Nullable String stackTrace,
                            boolean testError,
                            @Nullable String comparisionFailureActualText,
                            @Nullable String comparisionFailureExpectedText) {
  }

  @Override
  public void onTestIgnored(@NotNull String testName, @NotNull String ignoreComment, @Nullable String stackTrace) {
  }

  @Override
  public void onTestOutput(@NotNull String testName, @NotNull String text, boolean stdOut) {
  }

  @Override
  public void onSuiteStarted(@NotNull String suiteName, @Nullable String locationUrl) {
  }

  @Override
  public void onSuiteFinished(@NotNull String suiteName) {
  }

  @Override
  public void onUncapturedOutput(@NotNull String text, Key outputType) {
    myOutputBuffer.append("[").append(outputType.toString()).append("]"+ text);
  }

  @Override
  public void onError(@NotNull String localizedMessage, @Nullable String stackTrace) {
  }

  @Override
  public void onCustomProgressTestsCategory(@Nullable String categoryName, int testCount) {
  }

  @Override
  public void onCustomProgressTestStarted() {
  }

  @Override
  public void onCustomProgressTestFailed() {
  }

  @Override
  public void onTestsReporterAttached() {
  }

  @Override
  public void dispose() {
    myOutputBuffer.setLength(0);
  }

  public String getOutput() {
    return myOutputBuffer.toString();
  }
}
