// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code;

public class JumpInstruction extends Instruction {
  public int destination;

  public JumpInstruction(int opcode, int group, boolean wide, int bytecodeVersion, int[] operands, int length) {
    super(opcode, group, wide, bytecodeVersion, operands, length);
  }

  @Override
  public void initInstruction(InstructionSequence seq) {
    destination = seq.getPointerByRelOffset(this.operand(0));
  }

  @Override
  public JumpInstruction clone() {
    JumpInstruction copy = (JumpInstruction)super.clone();
    copy.destination = destination;
    return copy;
  }
}