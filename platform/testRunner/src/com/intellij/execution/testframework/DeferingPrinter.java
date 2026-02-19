// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.testframework;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.annotations.NotNull;

public class DeferingPrinter implements Printer {
  private final CompositePrintable myCompositePrintable;

  public DeferingPrinter() {
    myCompositePrintable = new CompositePrintable();
  }

  @Override
  public void print(final @NotNull String text, final @NotNull ConsoleViewContentType contentType) {
    myCompositePrintable.addLast(new Printable() {
      @Override
      public void printOn(final Printer printer) {
        printer.print(text, contentType);
      }
    });
  }

  @Override
  public void onNewAvailable(final @NotNull Printable printable) {
    myCompositePrintable.addLast(printable);
  }

  @Override
  public void printHyperlink(final @NotNull String text, final HyperlinkInfo info) {
    myCompositePrintable.addLast(new HyperLink(text, info));
  }

  @Override
  public void mark() {
    myCompositePrintable.addLast(printer -> printer.mark());
  }

  public void printAndForget(final Printer printer) {
    myCompositePrintable.printOn(printer);
    myCompositePrintable.clear();
  }
}
