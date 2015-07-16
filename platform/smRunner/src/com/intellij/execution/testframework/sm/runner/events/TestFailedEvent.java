/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner.events;

import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestFailedEvent extends TreeNodeEvent {

  private final String myLocalizedFailureMessage;
  private final String myStacktrace;
  private final boolean myTestError;
  private final String myComparisonFailureActualText;
  private final String myComparisonFailureExpectedText;
  private final String myFilePath;

  public TestFailedEvent(@NotNull TestFailed testFailed, boolean testError) {
    this(testFailed, testError, null);
  }
  public TestFailedEvent(@NotNull TestFailed testFailed, boolean testError, String filePath) {
    super(testFailed.getTestName(), TreeNodeEvent.getNodeId(testFailed));
    if (testFailed.getFailureMessage() == null) throw new NullPointerException();
    myLocalizedFailureMessage = testFailed.getFailureMessage();
    myStacktrace = testFailed.getStacktrace();
    myTestError = testError;
    myComparisonFailureActualText = testFailed.getActual();
    myComparisonFailureExpectedText = testFailed.getExpected();
    myFilePath = filePath;
  }

  public TestFailedEvent(@NotNull String testName,
                         @NotNull String localizedFailureMessage,
                         @Nullable String stackTrace,
                         boolean testError,
                         @Nullable String comparisonFailureActualText,
                         @Nullable String comparisonFailureExpectedText) {
    super(testName, -1);
    myLocalizedFailureMessage = localizedFailureMessage;
    myStacktrace = stackTrace;
    myTestError = testError;
    myComparisonFailureActualText = comparisonFailureActualText;
    myComparisonFailureExpectedText = comparisonFailureExpectedText;
    myFilePath = null;
  }

  @NotNull
  public String getLocalizedFailureMessage() {
    return myLocalizedFailureMessage;
  }

  @Nullable
  public String getStacktrace() {
    return myStacktrace;
  }

  public boolean isTestError() {
    return myTestError;
  }

  @Nullable
  public String getComparisonFailureActualText() {
    return myComparisonFailureActualText;
  }

  @Nullable
  public String getComparisonFailureExpectedText() {
    return myComparisonFailureExpectedText;
  }

  @Override
  protected void appendToStringInfo(@NotNull StringBuilder buf) {
    append(buf, "localizedFailureMessage", myLocalizedFailureMessage);
    append(buf, "stacktrace", myStacktrace);
    append(buf, "isTestError", myTestError);
    append(buf, "comparisonFailureActualText", myComparisonFailureActualText);
    append(buf, "comparisonFailureExpectedText", myComparisonFailureExpectedText);
  }

  public String getFilePath() {
    return myFilePath;
  }
}
