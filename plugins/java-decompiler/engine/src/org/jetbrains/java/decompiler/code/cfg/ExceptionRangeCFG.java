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
package org.jetbrains.java.decompiler.code.cfg;

import org.jetbrains.java.decompiler.main.DecompilerContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExceptionRangeCFG {

  private List<BasicBlock> protectedRange = new ArrayList<>(); // FIXME: replace with set

  private BasicBlock handler;

  private List<String> exceptionTypes;

  public ExceptionRangeCFG(List<BasicBlock> protectedRange, BasicBlock handler, List<String> exceptionType) {
    this.protectedRange = protectedRange;
    this.handler = handler;

    if (exceptionType != null) {
      this.exceptionTypes = new ArrayList<>(exceptionType);
    }
  }

  public boolean isCircular() {
    return protectedRange.contains(handler);
  }

  public String toString() {

    String new_line_separator = DecompilerContext.getNewLineSeparator();

    StringBuilder buf = new StringBuilder();

    buf.append("exceptionType:");
    for (String exception_type : exceptionTypes) {
      buf.append(" ").append(exception_type);
    }
    buf.append(new_line_separator);

    buf.append("handler: ").append(handler.id).append(new_line_separator);
    buf.append("range: ");
    for (int i = 0; i < protectedRange.size(); i++) {
      buf.append(protectedRange.get(i).id).append(" ");
    }
    buf.append(new_line_separator);

    return buf.toString();
  }

  public BasicBlock getHandler() {
    return handler;
  }

  public void setHandler(BasicBlock handler) {
    this.handler = handler;
  }

  public List<BasicBlock> getProtectedRange() {
    return protectedRange;
  }

  public void setProtectedRange(List<BasicBlock> protectedRange) {
    this.protectedRange = protectedRange;
  }

  public List<String> getExceptionTypes() {
    return this.exceptionTypes;
  }

  public void addExceptionType(String exceptionType) {

    if (this.exceptionTypes == null) {
      return;
    }

    if (exceptionType == null) {
      this.exceptionTypes = null;
    }
    else {
      this.exceptionTypes.add(exceptionType);
    }
  }

  public String getUniqueExceptionsString() {

    if (exceptionTypes == null) {
      return null;
    }

    Set<String> setExceptionStrings = new HashSet<>();

    for (String exceptionType : exceptionTypes) { // normalize order
      setExceptionStrings.add(exceptionType);
    }

    String ret = "";
    for (String exception : setExceptionStrings) {
      if (!ret.isEmpty()) {
        ret += ":";
      }
      ret += exception;
    }

    return ret;
  }


  //	public void setExceptionType(String exceptionType) {
  //		this.exceptionType = exceptionType;
  //	}
}
