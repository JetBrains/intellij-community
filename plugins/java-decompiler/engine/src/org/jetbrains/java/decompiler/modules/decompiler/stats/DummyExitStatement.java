// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

public class DummyExitStatement extends Statement {
  public @Nullable BitSet bytecode = null;  // offsets of bytecode instructions mapped to dummy exit

  public DummyExitStatement() {
    super(StatementType.DUMMY_EXIT);
  }

  public void addBytecodeOffsets(BitSet bytecodeOffsets) {
    if (bytecodeOffsets != null && !bytecodeOffsets.isEmpty()) {
      if (bytecode == null) {
        bytecode = new BitSet();
      }
      bytecode.or(bytecodeOffsets);
    }
  }
}
