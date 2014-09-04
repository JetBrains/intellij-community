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
package org.jetbrains.java.decompiler.main.decompiler.helper;

import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.PrintStream;

public class PrintStreamLogger implements IFernflowerLogger {

  private int severity;

  private int indent;

  private PrintStream stream;

  public PrintStreamLogger(int severity, PrintStream stream) {
    this.severity = severity;
    this.indent = 0;
    this.stream = stream;
  }


  public void writeMessage(String message, int severity) {
    if (severity >= this.severity) {
      stream.println(InterpreterUtil.getIndentString(indent) + names[severity] + ": " + message);
    }
  }

  public void writeMessage(String message, Throwable t) {
    t.printStackTrace(stream);
    writeMessage(message, ERROR);
  }

  public void startClass(String classname) {
    stream.println(InterpreterUtil.getIndentString(indent++) + "Processing class " + classname + " ...");
  }

  public void endClass() {
    stream.println(InterpreterUtil.getIndentString(--indent) + "... proceeded.");
  }

  public void startWriteClass(String classname) {
    stream.println(InterpreterUtil.getIndentString(indent++) + "Writing class " + classname + " ...");
  }

  public void endWriteClass() {
    stream.println(InterpreterUtil.getIndentString(--indent) + "... written.");
  }

  public void startMethod(String method) {
    if (severity <= INFO) {
      stream.println(InterpreterUtil.getIndentString(indent) + "Processing method " + method + " ...");
    }
  }

  public void endMethod() {
    if (severity <= INFO) {
      stream.println(InterpreterUtil.getIndentString(indent) + "... proceeded.");
    }
  }

  public int getSeverity() {
    return severity;
  }

  public void setSeverity(int severity) {
    this.severity = severity;
  }
}
