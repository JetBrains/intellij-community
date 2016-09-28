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
package com.intellij.execution.testframework;

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
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
    decoder.escapeText(text, ProcessOutputTypes.STDOUT, new AnsiEscapeDecoder.ColoredTextAcceptor() {
      @Override
      public void coloredTextAvailable(String text, Key attributes) {
        ConsoleViewContentType contentType = ConsoleViewContentType.getConsoleViewType(attributes);
        if (contentType == null || contentType == ConsoleViewContentType.NORMAL_OUTPUT) {
          contentType = ConsoleViewContentType.getConsoleViewType(processOutputType);
        }
        print(text, contentType);
      }
    });
  }

  default void printWithAnsiColoring(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
    decoder.escapeText(text, ProcessOutputTypes.STDOUT, new AnsiEscapeDecoder.ColoredTextAcceptor() {
      @Override
      public void coloredTextAvailable(String text, Key attributes) {
        ConsoleViewContentType viewContentType = ConsoleViewContentType.getConsoleViewType(attributes);
        if (viewContentType == null) {
          viewContentType = contentType;
        }
        print(text, viewContentType);
      }
    });
  }
}
