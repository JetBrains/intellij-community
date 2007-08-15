package com.intellij.execution.junit2.states;

import com.intellij.execution.junit2.Printer;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.junit2.ui.Formatters;
import com.intellij.execution.ui.ConsoleViewContentType;

class SkippedState extends ReadableState {
  private TestProxy myPeformedTest;

  void initializeFrom(final ObjectReader reader) {
    myPeformedTest = reader.readObject();
  }

  public void printOn(final Printer printer) {
    printer.print(Formatters.printTest(myPeformedTest) + ":" + TestProxy.NEW_LINE,
                  ConsoleViewContentType.SYSTEM_OUTPUT);
    myPeformedTest.printOn(printer);
  }
}
