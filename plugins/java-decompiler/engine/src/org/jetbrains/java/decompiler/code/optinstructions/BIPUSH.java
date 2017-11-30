// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code.optinstructions;

import org.jetbrains.java.decompiler.code.Instruction;

import java.io.DataOutputStream;
import java.io.IOException;

public class BIPUSH extends Instruction {

  private static final int[] opcodes =
    new int[]{opc_iconst_m1, opc_iconst_0, opc_iconst_1, opc_iconst_2, opc_iconst_3, opc_iconst_4, opc_iconst_5};

  public void writeToStream(DataOutputStream out, int offset) throws IOException {
    int value = getOperand(0);
    if (value < -1 || value > 5) {
      out.writeByte(opc_bipush);
      out.writeByte(value);
    }
    else {
      out.writeByte(opcodes[value + 1]);
    }
  }

  public int length() {
    int value = getOperand(0);
    if (value < -1 || value > 5) {
      return 2;
    }
    else {
      return 1;
    }
  }
}
