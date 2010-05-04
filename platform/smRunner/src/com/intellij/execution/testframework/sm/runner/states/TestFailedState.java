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

import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public class TestFailedState extends AbstractState {
  private final String myPresentationText;

  public TestFailedState(@Nullable final String localizedMessage,
                         @Nullable final String stackTrace) {
    myPresentationText = buildErrorPresentationText(localizedMessage, stackTrace);
  }

  @Nullable
  public static String buildErrorPresentationText(@Nullable final String localizedMessage,
                                                  @Nullable final String stackTrace) {
    final String text = (StringUtil.isEmptyOrSpaces(localizedMessage)
                           ? ""
                           : localizedMessage + PrintableTestProxy.NEW_LINE) +
                        (StringUtil.isEmptyOrSpaces(stackTrace)
                           ? ""
                           : stackTrace + PrintableTestProxy.NEW_LINE);
    return StringUtil.isEmptyOrSpaces(text) ? null : text;
  }

  public static void printError(@NotNull final Printer printer,
                                @NotNull final String errorPresentationText) {
    printer.print(PrintableTestProxy.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    printer.mark();
    printer.print(errorPresentationText, ConsoleViewContentType.ERROR_OUTPUT);
  }

  @Override
  public void printOn(final Printer printer) {
    super.printOn(printer);

    if (myPresentationText != null) {
      printError(printer, myPresentationText);
    }
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
