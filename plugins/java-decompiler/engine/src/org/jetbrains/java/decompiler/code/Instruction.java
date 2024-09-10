// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.code;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.TextUtil;

public class Instruction {
  public static Instruction create(int opcode, boolean wide, int group, int bytecodeVersion, int[] operands, int length) {
	return create(opcode, wide, group, bytecodeVersion, operands, length, null);
  }
  public static Instruction create(int opcode, boolean wide, int group, int bytecodeVersion, int[] operands, int length, ConstantPool pool) {
    if (opcode >= CodeConstants.opc_ifeq && opcode <= CodeConstants.opc_if_acmpne ||
        opcode == CodeConstants.opc_ifnull || opcode == CodeConstants.opc_ifnonnull ||
        opcode == CodeConstants.opc_jsr || opcode == CodeConstants.opc_jsr_w ||
        opcode == CodeConstants.opc_goto || opcode == CodeConstants.opc_goto_w) {
      return new JumpInstruction(opcode, group, wide, bytecodeVersion, operands, length);
    }
    else if (opcode == CodeConstants.opc_tableswitch || opcode == CodeConstants.opc_lookupswitch) {
      return new SwitchInstruction(opcode, group, wide, bytecodeVersion, operands, length);
    }
    else {
      return new Instruction(opcode, group, wide, bytecodeVersion, operands, length, pool);
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
  public final int length;

  protected final int[] operands;
  private final ConstantPool pool;

  public Instruction(int opcode, int group, boolean wide, int bytecodeVersion, int[] operands, int length) {
	this(opcode, group, wide, bytecodeVersion, operands, length, null);
  }
  public Instruction(int opcode, int group, boolean wide, int bytecodeVersion, int[] operands, int length, ConstantPool pool) {
    this.opcode = opcode;
    this.group = group;
    this.wide = wide;
    this.bytecodeVersion = bytecodeVersion;
    this.operands = operands;
    this.length = length;
    this.pool = pool;
  }

  public void initInstruction(InstructionSequence seq) { }

  public int operandsCount() {
    return operands == null ? 0 : operands.length;
  }

  public int operand(int index) {
    return operands[index];
  }

  public boolean canFallThrough() {
    return opcode != CodeConstants.opc_goto && opcode != CodeConstants.opc_goto_w && opcode != CodeConstants.opc_ret &&
           !(opcode >= CodeConstants.opc_ireturn && opcode <= CodeConstants.opc_return) &&
           opcode != CodeConstants.opc_athrow &&
           opcode != CodeConstants.opc_jsr && opcode != CodeConstants.opc_tableswitch && opcode != CodeConstants.opc_lookupswitch;
  }

  @Override
  public String toString() {
    StringBuilder res = new StringBuilder();
    if (wide) res.append("@wide ");
    res.append("@").append(TextUtil.getInstructionName(opcode));

    int len = operandsCount();
    for (int i = 0; i < len; i++) {
      int op = operands[i];
      if (op < 0) {
        res.append(" -").append(Integer.toHexString(-op));
      }
      else {
        res.append(" ").append(Integer.toHexString(op));
        if (pool != null && (this.group == CodeConstants.GROUP_INVOCATION || this.group == CodeConstants.GROUP_FIELDACCESS)) {
          res.append(' ').append(pool.getConstant(op));
        }
      }
    }

    return res.toString();
  }

  @Override
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public Instruction clone() {
    return create(opcode, wide, group, bytecodeVersion, operands == null ? null : operands.clone(), length);
  }
}
