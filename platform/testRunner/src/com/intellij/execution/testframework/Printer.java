// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public interface Printer {
  void print(String text, ConsoleViewContentType contentType);
  void onNewAvailable(@NotNull Printable printable);
  void printHyperlink(String text, HyperlinkInfo info);
  void mark();

  default void printWithAnsiColoring(@NotNull String text, @NotNull Key processOutputType) {
    if (processOutputType != ProcessOutputTypes.STDERR &&
        processOutputType != ProcessOutputTypes.STDOUT &&
        processOutputType != ProcessOutputTypes.SYSTEM) {
      print(text, ConsoleViewContentType.getConsoleViewType(processOutputType));
      return;
    }
    new AnsiEscapeDecoder().escapeText(text, processOutputType, (text1, attributes) ->
      print(text1, ConsoleViewContentType.getConsoleViewType(attributes)));
  }

  default void printExpectedActualHeader(String expected, String actual) {
    printExpectedActualHeader(this, expected, actual);
  }

  static void printExpectedActualHeader(Printer printer, String expected, String actual) {
    printer.print("\n", ConsoleViewContentType.ERROR_OUTPUT);
    printer.print(ExecutionBundle.message("diff.content.expected.for.file.title"), ConsoleViewContentType.SYSTEM_OUTPUT);
    printer.print(expected + "\n", ConsoleViewContentType.ERROR_OUTPUT);
    printer.print(ExecutionBundle.message("junit.actual.text.label"), ConsoleViewContentType.SYSTEM_OUTPUT);
    printer.print(actual, ConsoleViewContentType.ERROR_OUTPUT);
  }
}
