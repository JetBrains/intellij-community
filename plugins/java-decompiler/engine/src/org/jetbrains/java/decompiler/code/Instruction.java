// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code;

public class Instruction implements CodeConstants {
  public static Instruction create(int opcode, boolean wide, int group, int bytecodeVersion, int[] operands) {
    if (opcode >= opc_ifeq && opcode <= opc_if_acmpne ||
        opcode == opc_ifnull || opcode == opc_ifnonnull ||
        opcode == opc_jsr || opcode == opc_jsr_w ||
        opcode == opc_goto || opcode == opc_goto_w) {
      return new JumpInstruction(opcode, group, wide, bytecodeVersion, operands);
    }
    else if (opcode == opc_tableswitch || opcode == opc_lookupswitch) {
      return new SwitchInstruction(opcode, group, wide, bytecodeVersion, operands);
    }
    else {
      return new Instruction(opcode, group, wide, bytecodeVersion, operands);
    }
  }

  public static boolean equals(Instruction i1, Instruction i2) {
    return i1 != null && i2 != null &&
           (i1 == i2 ||
            i1.opcode == i2.opcode &&
            i1.wide == i2.wide &&
            i1.operandsCount() == i2.operandsCount());
  }

  public final int opcode;
  public final int group;
  public final boolean wide;
  public final int bytecodeVersion;

  protected final int[] operands;

  public Instruction(int opcode, int group, boolean wide, int bytecodeVersion, int[] operands) {
    this.opcode = opcode;
    this.group = group;
    this.wide = wide;
    this.bytecodeVersion = bytecodeVersion;
    this.operands = operands;
  }

  public void initInstruction(InstructionSequence seq) { }

  public int operandsCount() {
    return operands == null ? 0 : operands.length;
  }

  public int operand(int index) {
    return operands[index];
  }

  public boolean canFallThrough() {
    return opcode != opc_goto && opcode != opc_goto_w && opcode != opc_ret &&
           !(opcode >= opc_ireturn && opcode <= opc_return) &&
           opcode != opc_athrow &&
           opcode != opc_jsr && opcode != opc_tableswitch && opcode != opc_lookupswitch;
  }

  @Override
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public Instruction clone() {
    return create(opcode, wide, group, bytecodeVersion, operands == null ? null : operands.clone());
  }
}