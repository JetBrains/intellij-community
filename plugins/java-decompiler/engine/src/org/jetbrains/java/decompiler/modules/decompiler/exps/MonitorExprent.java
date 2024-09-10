// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public class MonitorExprent extends Exprent {

  public static final int MONITOR_ENTER = 0;
  public static final int MONITOR_EXIT = 1;

  private final int monType;
  private Exprent value;

  public MonitorExprent(int monType, Exprent value, BitSet bytecodeOffsets) {
    super(EXPRENT_MONITOR);
    this.monType = monType;
    this.value = value;

    addBytecodeOffsets(bytecodeOffsets);
  }

  @Override
  public Exprent copy() {
    return new MonitorExprent(monType, value.copy(), bytecode);
  }

  @Override
  public List<Exprent> getAllExprents(List<Exprent> lst) {
    lst.add(value);
    return lst;
  }

  @Override
  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    tracer.addMapping(bytecode);

    if (monType == MONITOR_ENTER) {
      return value.toJava(indent, tracer).enclose("synchronized(", ")");
    }
    else {
      return new TextBuffer();
    }
  }

  @Override
  public void replaceExprent(Exprent oldExpr, Exprent newExpr) {
    if (oldExpr == value) {
      value = newExpr;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof MonitorExprent me)) return false;

    return monType == me.getMonType() &&
           Objects.equals(value, me.getValue());
  }

  public int getMonType() {
    return monType;
  }

  public Exprent getValue() {
    return value;
  }

  @Override
  public void getBytecodeRange(BitSet values) {
    measureBytecode(values, value);
    measureBytecode(values);
  }
}
