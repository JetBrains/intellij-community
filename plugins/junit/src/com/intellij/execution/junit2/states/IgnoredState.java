package com.intellij.execution.junit2.states;

import com.intellij.execution.junit2.Printer;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ExecutionBundle;

public class IgnoredState extends ReadableState {
  private TestProxy myPeformedTest;

  void initializeFrom(final ObjectReader reader) {
    myPeformedTest = reader.readObject();
  }

  public void printOn(final Printer printer) {
    String message = ExecutionBundle.message("junit.runing.info.ignored.console.message",
                                             myPeformedTest.getParent().toString(),
                                             myPeformedTest.getInfo().getName());
    printer.print(message + TestProxy.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
  }
}
