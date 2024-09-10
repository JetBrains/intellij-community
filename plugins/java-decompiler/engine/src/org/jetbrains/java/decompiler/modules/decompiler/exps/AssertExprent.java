// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.BitSet;
import java.util.List;

public class AssertExprent extends Exprent {

  private final List<? extends Exprent> parameters;

  public AssertExprent(List<? extends Exprent> parameters) {
    super(EXPRENT_ASSERT);
    this.parameters = parameters;
  }

  @Override
  protected List<Exprent> getAllExprents(List<Exprent> list) {
    list.addAll(this.parameters);
    return list;
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    TextBuffer buffer = new TextBuffer();

    buffer.append("assert ");

    tracer.addMapping(bytecode);

    if (parameters.get(0) == null) {
      buffer.append("false");
    }
    else {
      buffer.append(parameters.get(0).toJava(indent, tracer));
    }

    if (parameters.size() > 1) {
      buffer.append(" : ");
      buffer.append(parameters.get(1).toJava(indent, tracer));
    }

    return buffer;
  }

  @Override
  public void getBytecodeRange(BitSet values) {
    measureBytecode(values, parameters);
    measureBytecode(values);
  }
}
