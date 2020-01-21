// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.stats;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class DummyExitStatement extends Statement {
  public Set<Integer> bytecode = null;  // offsets of bytecode instructions mapped to dummy exit

  public DummyExitStatement() {
    type = Statement.TYPE_DUMMYEXIT;
  }

  public void addBytecodeOffsets(Collection<Integer> bytecodeOffsets) {
    if (bytecodeOffsets != null && !bytecodeOffsets.isEmpty()) {
      if (bytecode == null) {
        bytecode = new HashSet<>(bytecodeOffsets);
      }
      else {
        bytecode.addAll(bytecodeOffsets);
      }
    }
  }
}
