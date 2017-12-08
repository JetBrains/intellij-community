/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;

public class RootStatement extends Statement {
  private final DummyExitStatement dummyExit;

  public RootStatement(Statement head, DummyExitStatement dummyExit) {
    type = Statement.TYPE_ROOT;

    first = head;
    this.dummyExit = dummyExit;

    stats.addWithKey(first, first.id);
    first.setParent(this);
  }

  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    return ExprProcessor.listToJava(varDefinitions, indent, tracer).append(first.toJava(indent, tracer));
  }

  public DummyExitStatement getDummyExit() {
    return dummyExit;
  }
}