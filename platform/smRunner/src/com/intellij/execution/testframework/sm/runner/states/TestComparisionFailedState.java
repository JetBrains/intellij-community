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
package com.intellij.execution.testframework.sm.runner.states;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.CompositePrintable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.sm.runner.ui.TestsPresentationUtil;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman.Chernyatchik
 */
public class TestComparisionFailedState extends TestFailedState {
  private final String myErrorMsgPresentation;
  private final String myStacktracePresentation;
  private DiffHyperlink myHyperlink;


  public TestComparisionFailedState(@Nullable final String localizedMessage,
                                    @Nullable final String stackTrace,
                                    @NotNull final String actualText,
                                    @NotNull final String expectedText) {
    this(localizedMessage, stackTrace, actualText, expectedText, null);
  }

  public TestComparisionFailedState(@Nullable final String localizedMessage,
                                    @Nullable final String stackTrace,
                                    @NotNull final String actualText,
                                    @NotNull final String expectedText,
                                    @Nullable final String filePath) {
    this(localizedMessage, stackTrace, actualText, expectedText, filePath, null);
  }
  
  public TestComparisionFailedState(@Nullable final String localizedMessage,
                                    @Nullable final String stackTrace,
                                    @NotNull final String actualText,
                                    @NotNull final String expectedText,
                                    @Nullable final String expectedFilePath,
                                    @Nullable final String actualFilePath) {
    super(localizedMessage, stackTrace);
    myHyperlink = new DiffHyperlink(expectedText, actualText, expectedFilePath, actualFilePath, true);

    myErrorMsgPresentation = StringUtil.isEmptyOrSpaces(localizedMessage) ? "" : localizedMessage;
    myStacktracePresentation = StringUtil.isEmptyOrSpaces(stackTrace) ? "" : stackTrace;
  }

  @Override
  public void printOn(Printer printer) {
    printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    printer.mark();

    // Error msg
    TestsPresentationUtil.printWithAnsiColoring(printer, myErrorMsgPresentation, ProcessOutputTypes.STDERR);

    // Diff link
    myHyperlink.printOn(printer);

    // Stacktrace
    printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    TestsPresentationUtil.printWithAnsiColoring(printer, myStacktracePresentation, ProcessOutputTypes.STDERR);
    printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
  }

  @Nullable
  public DiffHyperlink getHyperlink() {
    return myHyperlink;
  }
}
