/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.io.PrintStream;

public class PrintStreamLogger extends IFernflowerLogger {

  private final PrintStream stream;
  private int indent;

  public PrintStreamLogger(PrintStream printStream) {
    stream = printStream;
    indent = 0;
  }

  @Override
  public void writeMessage(String message, Severity severity) {
    if (accepts(severity)) {
      stream.println(severity.prefix + TextUtil.getIndentString(indent) + message);
    }
  }

  @Override
  public void writeMessage(String message, Severity severity, Throwable t) {
    if (accepts(severity)) {
      writeMessage(message, severity);
      t.printStackTrace(stream);
    }
  }

  @Override
  public void startReadingClass(String className) {
    if (accepts(Severity.INFO)) {
      writeMessage("Decompiling class " + className, Severity.INFO);
      ++indent;
    }
  }

  @Override
  public void endReadingClass() {
    if (accepts(Severity.INFO)) {
      --indent;
      writeMessage("... done", Severity.INFO);
    }
  }

  @Override
  public void startClass(String className) {
    if (accepts(Severity.INFO)) {
      writeMessage("Processing class " + className, Severity.TRACE);
      ++indent;
    }
  }

  @Override
  public void endClass() {
    if (accepts(Severity.INFO)) {
      --indent;
      writeMessage("... proceeded", Severity.TRACE);
    }
  }

  @Override
  public void startMethod(String methodName) {
    if (accepts(Severity.INFO)) {
      writeMessage("Processing method " + methodName, Severity.TRACE);
      ++indent;
    }
  }

  public void endMethod() {
    if (accepts(Severity.INFO)) {
      --indent;
      writeMessage("... proceeded", Severity.TRACE);
    }
  }

  @Override
  public void startWriteClass(String className) {
    if (accepts(Severity.INFO)) {
      writeMessage("Writing class " + className, Severity.TRACE);
      ++indent;
    }
  }

  @Override
  public void endWriteClass() {
    if (accepts(Severity.INFO)) {
      --indent;
      writeMessage("... written", Severity.TRACE);
    }
  }
}
