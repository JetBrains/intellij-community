/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.java.decompiler.util.InterpreterUtil;

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
      stream.println(InterpreterUtil.getIndentString(indent) + severity.name() + ": " + message);
    }
  }

  @Override
  public void writeMessage(String message, Throwable t) {
    writeMessage(message, Severity.ERROR);
    if (accepts(Severity.ERROR)) {
      t.printStackTrace(stream);
    }
  }

  @Override
  public void startClass(String className) {
    writeMessage("Processing class " + className + " ...", Severity.INFO);
    ++indent;
  }

  @Override
  public void endClass() {
    --indent;
    writeMessage("... proceeded.", Severity.INFO);
  }

  @Override
  public void startWriteClass(String className) {
    writeMessage("Writing class " + className + " ...", Severity.INFO);
    ++indent;
  }

  @Override
  public void endWriteClass() {
    --indent;
    writeMessage("... written.", Severity.INFO);
  }

  @Override
  public void startMethod(String methodName) {
    writeMessage("Processing method " + methodName + " ...", Severity.INFO);
  }

  public void endMethod() {
    writeMessage("... proceeded.", Severity.INFO);
  }
}
