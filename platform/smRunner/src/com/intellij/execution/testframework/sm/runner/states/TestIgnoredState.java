// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.states;

import com.intellij.execution.testframework.CompositePrintable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public class TestIgnoredState extends AbstractState {
  private final String myText;
  private final String myStacktrace;

  public TestIgnoredState(@Nullable String ignoredMsg, final @Nullable String stackTrace) {
    if (StringUtil.isEmpty(ignoredMsg)) {
      myText = null;
    }
    else {
      myText = CompositePrintable.NEW_LINE + ignoredMsg + CompositePrintable.NEW_LINE;
    }
    myStacktrace = stackTrace == null ? null : stackTrace + CompositePrintable.NEW_LINE;
  }

  @Override
  public boolean isInProgress() {
    return false;
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
  public boolean wasTerminated() {
    return false;
  }

  @Override
  public Magnitude getMagnitude() {
    return Magnitude.IGNORED_INDEX;
  }

  @Override
  public void printOn(final Printer printer) {
    super.printOn(printer);

    if (myText != null) {
      printer.print(myText, ConsoleViewContentType.SYSTEM_OUTPUT);
    }
    if (!StringUtil.isEmptyOrSpaces(myStacktrace)) {
      printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.SYSTEM_OUTPUT);
      printer.mark();
      printer.print(myStacktrace, ConsoleViewContentType.SYSTEM_OUTPUT);
    }
  }


  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return "TEST IGNORED";
  }
}