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
