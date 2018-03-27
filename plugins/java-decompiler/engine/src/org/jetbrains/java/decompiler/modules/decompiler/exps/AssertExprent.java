/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.util.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;

import java.util.List;

public class AssertExprent extends Exprent {

  private final List<Exprent> parameters;

  public AssertExprent(List<Exprent> parameters) {
    super(EXPRENT_ASSERT);
    this.parameters = parameters;
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
}
