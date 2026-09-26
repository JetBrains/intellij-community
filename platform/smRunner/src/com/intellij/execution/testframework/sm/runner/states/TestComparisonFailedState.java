// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.states;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.CompositePrintable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Roman.Chernyatchik
 */
public class TestComparisonFailedState extends TestFailedState {

  private final @NotNull DiffHyperlink myHyperlink;
  private boolean myToDeleteExpectedFile;
  private boolean myToDeleteActualFile;

  public TestComparisonFailedState(
    final @Nullable String localizedMessage,
    final @Nullable String stackTrace,
    final @NotNull String actualText,
    final @NotNull String expectedText,
    boolean printExpectedAndActualValues,
    final @Nullable String expectedFilePath,
    final @Nullable String actualFilePath
  ) {
    super(localizedMessage, stackTrace);
    myHyperlink = new DiffHyperlink(expectedText, actualText, expectedFilePath, actualFilePath, printExpectedAndActualValues);
  }

  @Override
  public void printOn(Printer printer) {
    printer.mark();

    // Error msg
    var errorMsgPresentation = getErrorMsgPresentation();
    if (!StringUtil.isEmptyOrSpaces(errorMsgPresentation)) {
      printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
      printer.printWithAnsiColoring(errorMsgPresentation, ProcessOutputTypes.STDERR);
    }

    // Diff link
    myHyperlink.printOn(printer);

    // Stacktrace
    printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    printer.printWithAnsiColoring(getStacktracePresentation(), ProcessOutputTypes.STDERR);
    printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
  }

  public @NotNull DiffHyperlink getHyperlink() {
    return myHyperlink;
  }

  public void setToDeleteExpectedFile(boolean expectedTemp) {
    myToDeleteExpectedFile = expectedTemp;
  }

  public void setToDeleteActualFile(boolean actualTemp) {
    myToDeleteActualFile = actualTemp;
  }

  @Override
  public void dispose() {
    if (myToDeleteActualFile) {
      FileUtil.delete(new File(myHyperlink.getActualFilePath()));
    }
    if (myToDeleteExpectedFile) {
      FileUtil.delete(new File(myHyperlink.getFilePath()));
    }
  }
}
