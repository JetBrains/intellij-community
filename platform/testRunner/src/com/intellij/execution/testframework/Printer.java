package com.intellij.execution.testframework;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.ui.ConsoleViewContentType;

public interface Printer {
  void print(String text, ConsoleViewContentType contentType);
  void onNewAvailable(Printable printable);
  void printHyperlink(String text, HyperlinkInfo info);
  void mark();

  interface Intermediate extends Printer, Printable {}

  Intermediate DEAF = new Intermediate() {
    public void print(final String text, final ConsoleViewContentType contentType) {}
    public void onNewAvailable(final Printable printable) {}
    public void printHyperlink(final String text, final HyperlinkInfo info) {}
    public void mark() {}
    public void printOn(final Printer printer) {}
  };
}
