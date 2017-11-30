// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code;

import org.jetbrains.java.decompiler.main.DecompilerContext;

import java.io.DataOutputStream;
import java.io.IOException;

public class ExceptionHandler {

  public int from = 0;
  public int to = 0;
  public int handler = 0;

  public int from_instr = 0;
  public int to_instr = 0;
  public int handler_instr = 0;

  public int class_index = 0;
  public String exceptionClass = null;

  public ExceptionHandler() {
  }

  public ExceptionHandler(int from_raw, int to_raw, int handler_raw, String exceptionClass) {
    this.from = from_raw;
    this.to = to_raw;
    this.handler = handler_raw;
    this.exceptionClass = exceptionClass;
  }

  public void writeToStream(DataOutputStream out) throws IOException {
    out.writeShort(from);
    out.writeShort(to);
    out.writeShort(handler);
    out.writeShort(class_index);
  }

  public String toString() {

    String new_line_separator = DecompilerContext.getNewLineSeparator();

    return "from: " + from + " to: " + to + " handler: " + handler + new_line_separator +
           "from_instr: " + from_instr + " to_instr: " + to_instr + " handler_instr: " + handler_instr + new_line_separator +
           "exceptionClass: " + exceptionClass + new_line_separator;
  }
}
