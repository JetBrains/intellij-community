// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.states;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.CompositePrintable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public class TestFailedState extends AbstractState implements Disposable{
  private final String myErrorMsgPresentation;
  private final String myStacktracePresentation;

  public TestFailedState(@Nullable final String localizedMessage,
                         @Nullable final String stackTrace) {
    myErrorMsgPresentation = StringUtil.isEmptyOrSpaces(localizedMessage) ? "" : localizedMessage;
    myStacktracePresentation = StringUtil.isEmptyOrSpaces(stackTrace) ? "" : stackTrace;
  }

  @Override
  public void dispose() {}


  @Override
  public void printOn(final Printer printer) {
    printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    printer.mark();
    if (!StringUtil.isEmpty(myErrorMsgPresentation)) {
      printer.printWithAnsiColoring(myErrorMsgPresentation, ProcessOutputTypes.STDERR);
      printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    }
    if (!StringUtil.isEmpty(myStacktracePresentation)) {
      printer.printWithAnsiColoring(myStacktracePresentation, ProcessOutputTypes.STDERR);
      printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  @Override
  public boolean isDefect() {
    return true;
  }

  @Override
  public boolean wasLaunched() {
    return true;
  }

  @Override
  public boolean isFinal() {
    return true;
  }

  @Override
  public boolean isInProgress() {
    return false;
  }

  @Override
  public boolean wasTerminated() {
    return false;
  }

  @Override
  public Magnitude getMagnitude() {
    return Magnitude.FAILED_INDEX;
  }

  protected String getErrorMsgPresentation() {
    return myErrorMsgPresentation;
  }

  protected String getStacktracePresentation() {
    return myStacktracePresentation;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "TEST FAILED";
  }
}
