// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.util.StartEndPair;
import org.jetbrains.java.decompiler.util.TextBuffer;

public class RootStatement extends Statement {
  private final DummyExitStatement dummyExit;

  public RootStatement(Statement head, DummyExitStatement dummyExit) {
    super(StatementType.ROOT);

    first = head;
    this.dummyExit = dummyExit;

    stats.addWithKey(first, first.id);
    first.setParent(this);
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    return ExprProcessor.listToJava(varDefinitions, indent, tracer).append(first.toJava(indent, tracer));
  }

  public DummyExitStatement getDummyExit() {
    return dummyExit;
  }

  @Override
  public StartEndPair getStartEndRange() {
    return StartEndPair.join(first.getStartEndRange(), dummyExit != null ? dummyExit.getStartEndRange() : null);
  }
}
