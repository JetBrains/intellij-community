package com.intellij.execution.junit2.states;

import com.intellij.execution.junit2.Printer;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.stacktrace.DiffHyperlink;
import com.intellij.openapi.project.Project;
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

  public String getExpecteed() {
    return myHyperlink.getLeft();
  }

  public String getActual() {
    return myHyperlink.getRight();
  }

  public void openDiff(final Project project) {
    if (myHyperlink != null) myHyperlink.openDiff(project);
  }
}
