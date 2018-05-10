// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.states;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.CompositePrintable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class TestFailedState extends AbstractState implements Disposable{
  private final List<String> myPresentationText;

  public TestFailedState(@Nullable final String localizedMessage,
                         @Nullable final String stackTrace)
  {
    myPresentationText =
      ContainerUtil.createLockFreeCopyOnWriteList(Collections.singleton(buildErrorPresentationText(localizedMessage, stackTrace)));
  }

  public void addError(@Nullable String localizedMessage, @Nullable String stackTrace, Printer printer) {
    final String msg = buildErrorPresentationText(localizedMessage, stackTrace);
    if (msg != null) {
      myPresentationText.add(msg);
      if (printer != null) {
        printError(printer, Collections.singletonList(msg), false);
      }
    }
  }

  @Override
  public void dispose() {}

  @Nullable
  public static String buildErrorPresentationText(@Nullable final String localizedMessage,
                                                  @Nullable final String stackTrace)
  {
    final String text = (StringUtil.isEmptyOrSpaces(localizedMessage) ? "" : localizedMessage + CompositePrintable.NEW_LINE) +
                        (StringUtil.isEmptyOrSpaces(stackTrace) ? "" : stackTrace + CompositePrintable.NEW_LINE);
    return StringUtil.isEmptyOrSpaces(text) ? null : text;
  }

  public static void printError(@NotNull final Printer printer,
                                @NotNull final List<String> errorPresentationText)
  {
    printError(printer, errorPresentationText, true);
  }

  private static void printError(@NotNull final Printer printer,
                                @NotNull final List<String> errorPresentationText,
                                final boolean setMark)
  {
    boolean addMark = setMark;
    for (final String errorText : errorPresentationText) {
      if (errorText != null) {
        printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
        if (addMark) {
          printer.mark();
          addMark = false;
        }
        printer.printWithAnsiColoring(errorText, ProcessOutputTypes.STDERR);
      }
    }
  }

  @Override
  public void printOn(final Printer printer) {
    super.printOn(printer);
    printError(printer, myPresentationText);
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

  public boolean isInProgress() {
    return false;
  }

  public boolean wasTerminated() {
    return false;
  }

  public Magnitude getMagnitude() {
    return Magnitude.FAILED_INDEX;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "TEST FAILED";
  }
}
