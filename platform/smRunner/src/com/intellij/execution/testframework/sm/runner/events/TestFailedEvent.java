/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import jetbrains.buildServer.messages.serviceMessages.TestFailed;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class TestFailedEvent extends TreeNodeEvent {

  private final String myLocalizedFailureMessage;
  private final String myStacktrace;
  private final boolean myTestError;
  private final String myComparisonFailureActualText;
  private final String myComparisonFailureExpectedText;
  private final String myExpectedFilePath;
  private final String myActualFilePath;
  private final long myDurationMillis;
  private final boolean myExpectedFileTemp;
  private final boolean myActualFileTemp;
  private final boolean myPrintExpectedAndActualValues;

  public TestFailedEvent(@NotNull TestFailed testFailed, boolean testError) {
    this(testFailed, testError, null);
  }
  public TestFailedEvent(@NotNull TestFailed testFailed, boolean testError, @Nullable String expectedFilePath) {
    this(testFailed, testError, expectedFilePath, null);
  }  
  public TestFailedEvent(@NotNull TestFailed testFailed,
                         boolean testError,
                         @Nullable String expectedFilePath,
                         @Nullable String actualFilePath) {
    super(testFailed.getTestName(), TreeNodeEvent.getNodeId(testFailed));
    if (testFailed.getFailureMessage() == null) throw new NullPointerException();
    myLocalizedFailureMessage = testFailed.getFailureMessage();
    myStacktrace = testFailed.getStacktrace();
    myTestError = testError;

    myExpectedFilePath = expectedFilePath;
    String expected = testFailed.getExpected();
    if (expected == null && expectedFilePath != null) {
      expected = loadExpectedText(expectedFilePath);
    }
    myComparisonFailureExpectedText = expected;

    myActualFilePath = actualFilePath;
    String actual = testFailed.getActual();
    if (actual == null && actualFilePath != null) {
      try {
        actual = FileUtil.loadFile(new File(actualFilePath));
      }
      catch (IOException ignore) {}
    }
    myComparisonFailureActualText = actual;

    Map<String, String> attributes = testFailed.getAttributes();
    myDurationMillis = parseDuration(attributes.get("duration"));
    myActualFileTemp = Boolean.parseBoolean(attributes.get("actualIsTempFile"));
    myExpectedFileTemp = Boolean.parseBoolean(attributes.get("expectedIsTempFile"));
    myPrintExpectedAndActualValues = parsePrintExpectedAndActual(testFailed);
  }

  private static boolean parsePrintExpectedAndActual(@NotNull TestFailed testFailed) {
    return !Boolean.FALSE.toString().equals(testFailed.getAttributes().get("printExpectedAndActual"));
  }

  public boolean isExpectedFileTemp() {
    return myExpectedFileTemp;
  }

  public boolean isActualFileTemp() {
    return myActualFileTemp;
  }

  private static long parseDuration(@Nullable String durationStr) {
    if (!StringUtil.isEmpty(durationStr)) {
      try {
        return Long.parseLong(durationStr);
      }
      catch (NumberFormatException ignored) {
      }
    }
    return -1;
  }

  public TestFailedEvent(@NotNull String testName,
                         @NotNull String localizedFailureMessage,
                         @Nullable String stackTrace,
                         boolean testError,
                         @Nullable String comparisonFailureActualText,
                         @Nullable String comparisonFailureExpectedText) {
    this(testName,
         null,
         localizedFailureMessage,
         stackTrace,
         testError,
         comparisonFailureActualText,
         comparisonFailureExpectedText,
         null,
         null,
         false,
         false,
         -1);
  }

  public TestFailedEvent(@Nullable String testName,
                         @Nullable String id,
                         @NotNull String localizedFailureMessage,
                         @Nullable String stackTrace,
                         boolean testError,
                         @Nullable String comparisonFailureActualText,
                         @Nullable String comparisonFailureExpectedText,
                         @Nullable String expectedFilePath,
                         @Nullable String actualFilePath,
                         boolean expectedFileTemp,
                         boolean actualFileTemp,
                         long durationMillis) {
    this(testName,
         id,
         localizedFailureMessage,
         stackTrace,
         testError,
         comparisonFailureActualText,
         comparisonFailureExpectedText,
         true,
         expectedFilePath,
         actualFilePath,
         expectedFileTemp,
         actualFileTemp,
         durationMillis);
  }

  private TestFailedEvent(@Nullable String testName,
                          @Nullable String id,
                          @NotNull String localizedFailureMessage,
                          @Nullable String stackTrace,
                          boolean testError,
                          @Nullable String comparisonFailureActualText,
                          @Nullable String comparisonFailureExpectedText,
                          boolean printExpectedAndActualValues,
                          @Nullable String expectedFilePath,
                          @Nullable String actualFilePath,
                          boolean expectedFileTemp,
                          boolean actualFileTemp,
                          long durationMillis) {
    super(testName, id);
    myLocalizedFailureMessage = localizedFailureMessage;
    myStacktrace = stackTrace;
    myTestError = testError;
    myExpectedFilePath = expectedFilePath;
    if (comparisonFailureExpectedText == null && expectedFilePath != null) {
      comparisonFailureExpectedText = loadExpectedText(expectedFilePath);
    }
    myComparisonFailureActualText = comparisonFailureActualText;
    myPrintExpectedAndActualValues = printExpectedAndActualValues;

    myActualFilePath = actualFilePath;
    myComparisonFailureExpectedText = comparisonFailureExpectedText;
    myDurationMillis = durationMillis;
    myExpectedFileTemp = expectedFileTemp;
    myActualFileTemp = actualFileTemp;
  }

  private static String loadExpectedText(@NotNull String expectedFilePath) {
    try {
      int jarSep = expectedFilePath.indexOf(URLUtil.JAR_SEPARATOR);
      if (jarSep == -1) {
        return FileUtil.loadFile(new File(expectedFilePath));
      }
      else {
        String localPath = expectedFilePath.substring(0, jarSep);
        String jarPath = expectedFilePath.substring(jarSep + URLUtil.JAR_SEPARATOR.length());
        return FileUtil.loadTextAndClose(URLUtil.getJarEntryURL(new File(localPath), jarPath).openStream());
      }
    }
    catch (IOException ignore) {}
    return null;
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

  @Nullable
  public String getExpectedFilePath() {
    return myExpectedFilePath;
  }

  public boolean shouldPrintExpectedAndActualValues() {
    return myPrintExpectedAndActualValues;
  }

  @Nullable
  public String getActualFilePath() {
    return myActualFilePath;
  }
  
  /**
   * @return the test duration in milliseconds, or -1 if undefined
   */
  public long getDurationMillis() {
    return myDurationMillis;
  }
}
