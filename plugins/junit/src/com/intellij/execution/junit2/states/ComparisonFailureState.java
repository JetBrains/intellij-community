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

package com.intellij.execution.junit2.states;

import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.annotations.NonNls;

public class ComparisonFailureState extends FaultyState {
  private DiffHyperlink myHyperlink;
  @NonNls
  protected static final String EXPECTED_VALUE_MESSAGE_TEXT = "expected:<";

  public void initializeFrom(final ObjectReader reader) {
    super.initializeFrom(reader);
    myHyperlink = JUnitDiffHyperlink.readFrom(reader);
  }

  protected void printExceptionHeader(final Printer printer, String message) {
    final int columnIndex = message.indexOf(':');
    if (columnIndex != -1) {
      printer.print(message.substring(0, columnIndex + 1), ConsoleViewContentType.ERROR_OUTPUT);
      message = message.substring(columnIndex + 1);
    }
    if (message.trim().length() > 0) {
      final int generatedMessageStart = message.indexOf(EXPECTED_VALUE_MESSAGE_TEXT);
      if (generatedMessageStart != -1) message = message.substring(0, generatedMessageStart);
      printer.print(message, ConsoleViewContentType.ERROR_OUTPUT);
    }
    myHyperlink.printOn(printer);
  }

  public DiffHyperlink getHyperlink() {
    return myHyperlink;
  }
}
