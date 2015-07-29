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

import com.intellij.rt.execution.testFrameworks.AbstractExpectedPatterns;
import com.intellij.rt.execution.junit.ComparisonFailureData;

import java.util.ArrayList;
import java.util.List;

public class ExpectedPatterns extends AbstractExpectedPatterns {
  private static final List PATTERNS = new ArrayList();

  private static final String[] PATTERN_STRINGS = new String[]{
    "\nexpected: is \"(.*)\"\n\\s*got: \"(.*)\"\n",
    "\nexpected: is \"(.*)\"\n\\s*but: was \"(.*)\"",
    "\nexpected: (.*)\n\\s*got: (.*)",
    ".*?\\s*expected same:<(.*)> was not:<(.*)>",
    ".*?\\s*expected:<(.*?)> but was:<(.*?)>",
    "\nexpected: \"(.*)\"\n\\s*but: was \"(.*)\"",
    "\\s*expected: (.*)\\s*but: was (.*)",
    ".*?\\s*expected: (.*)\\s*but was: (.*)"
  };

  static {
    registerPatterns(PATTERN_STRINGS, PATTERNS);
  }

  public static ComparisonFailureData createExceptionNotification(String message) {
    return createExceptionNotification(message, PATTERNS);
  }
}
