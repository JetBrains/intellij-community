/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.junit4;

import com.intellij.rt.execution.junit.ComparisonFailureData;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ExpectedPatterns {
  static final Pattern EXPECTED_IS_GOT = compilePattern("\nExpected: is \"(.*)\"\n\\s*got: \"(.*)\"\n");
  static final Pattern EXPECTED_IS_BUT_WAS = compilePattern("\nExpected: is \"(.*)\"\n\\s*but: was \"(.*)\"");
  static final Pattern EXPECTED_GOT = compilePattern("\nExpected: (.*)\n\\s*got: (.*)");
  static final Pattern EXPECTED_SAME_WAS_NOT = compilePattern(".*?\\s*expected same:<(.*)> was not:<(.*)>");
  static final Pattern EXPECTED_BUT_WAS_COLUMN = compilePattern(".*?\\s*expected:<(.*?)> but was:<(.*?)>");
  static final Pattern EXPECTED_BUT_WAS_IN_QUOTES = compilePattern("\nExpected: \"(.*)\"\n\\s*but: was \"(.*)\"");
  static final Pattern EXPECTED_BUT_WAS = compilePattern("\\s*Expected: (.*)\\s*but: was (.*)");

  private static Pattern compilePattern(String regex) {
    return Pattern.compile(regex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  }

  static ComparisonFailureData createExceptionNotification(String message) {
    ComparisonFailureData notification = createExceptionNotification(message, EXPECTED_IS_GOT);
    if (notification == null) {
      notification = createExceptionNotification(message, EXPECTED_IS_BUT_WAS);
    }
    if (notification == null) {
      notification = createExceptionNotification(message, EXPECTED_GOT);
    }
    if (notification == null) {
      notification = createExceptionNotification(message, EXPECTED_SAME_WAS_NOT);
    }
    if (notification == null) {
      notification = createExceptionNotification(message, EXPECTED_BUT_WAS_COLUMN);
    }
    if (notification == null) {
      notification = createExceptionNotification(message, EXPECTED_BUT_WAS_IN_QUOTES);
    }
    if (notification == null) {
      notification = createExceptionNotification(message, EXPECTED_BUT_WAS);
    }
    if (notification != null) {
      return notification;
    }
    return null;
  }

  private static ComparisonFailureData createExceptionNotification(String message, Pattern pattern) {
    final Matcher matcher = ((Pattern)pattern).matcher(message);
    if (matcher.matches()) {
      return new ComparisonFailureData(matcher.group(1).replaceAll("\\\\n", "\n"), matcher.group(2).replaceAll("\\\\n", "\n"));
    }
    return null;
  }
}
