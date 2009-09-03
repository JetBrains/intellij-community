package com.intellij.execution.testframework.sm.runner.states;

import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
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
    myText = PrintableTestProxy.NEW_LINE + ignored_msg;
    myStacktrace = stackTrace == null ? null : stackTrace + PrintableTestProxy.NEW_LINE;
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
      printer.print(PrintableTestProxy.NEW_LINE, ConsoleViewContentType.SYSTEM_OUTPUT);
    }
    else {
      printer.print(PrintableTestProxy.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
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