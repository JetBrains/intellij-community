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
package com.intellij.execution.testframework.sm.runner.states;

import com.intellij.execution.testframework.CompositePrintable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public class TestIgnoredState extends AbstractState {
  @NonNls private static final String IGNORED_TEST_TEXT = SMTestsRunnerBundle.message("sm.test.runner.states.test.is.ignored");
  private final String myText;
  private final String myStacktrace;

  public TestIgnoredState(final String ignoredComment, @Nullable final String stackTrace) {
    final String ignored_msg = StringUtil.isEmpty(ignoredComment) ? IGNORED_TEST_TEXT : ignoredComment;
    myText = CompositePrintable.NEW_LINE + ignored_msg;
    myStacktrace = stackTrace == null ? null : stackTrace + CompositePrintable.NEW_LINE;
  }

  public boolean isInProgress() {
    return false;
  }

  public boolean isDefect() {
    return true;
  }

  public boolean wasLaunched() {
    return true;
  }

  public boolean isFinal() {
    return true;
  }

  public boolean wasTerminated() {
    return false;
  }

  public Magnitude getMagnitude() {
    return Magnitude.IGNORED_INDEX;
  }

  @Override
  public void printOn(final Printer printer) {
    super.printOn(printer);

    printer.print(myText, ConsoleViewContentType.SYSTEM_OUTPUT);
    if (StringUtil.isEmptyOrSpaces(myStacktrace)) {
      printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.SYSTEM_OUTPUT);
    }
    else {
      printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
      printer.mark();
      printer.print(myStacktrace, ConsoleViewContentType.ERROR_OUTPUT);
    }
  }


  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "TEST IGNORED";
  }
}