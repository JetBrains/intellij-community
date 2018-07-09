// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code.cfg;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ExceptionRangeCFG {
  private final List<BasicBlock> protectedRange; // FIXME: replace with set
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

  public BasicBlock getHandler() {
    return handler;
  }

  public void setHandler(BasicBlock handler) {
    this.handler = handler;
  }

  public List<BasicBlock> getProtectedRange() {
    return protectedRange;
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
    return exceptionTypes != null ? exceptionTypes.stream().distinct().collect(Collectors.joining(":")) : null;
  }
}