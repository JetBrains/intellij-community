// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code;

import org.jetbrains.java.decompiler.code.interpreter.Util;
import org.jetbrains.java.decompiler.struct.StructContext;

import java.util.Collections;
import java.util.List;

public class ExceptionTable {
  public static final ExceptionTable EMPTY = new ExceptionTable(null) {
    @Override
    public List<ExceptionHandler> getHandlers() {
      return Collections.emptyList();
    }
  };

  private final List<ExceptionHandler> handlers;

  public ExceptionTable(List<ExceptionHandler> handlers) {
    this.handlers = handlers;
  }


  public ExceptionHandler getHandlerByClass(StructContext context, int line, String valclass, boolean withany) {

    ExceptionHandler res = null; // no handler found

    for (ExceptionHandler handler : handlers) {
      if (handler.from <= line && handler.to > line) {
        String name = handler.exceptionClass;

        if ((withany && name == null) ||   // any -> finally or synchronized handler
            (name != null && Util.instanceOf(context, valclass, name))) {
          res = handler;
          break;
        }
      }
    }

    return res;
  }

  public List<ExceptionHandler> getHandlers() {
    return handlers;
  }
}
