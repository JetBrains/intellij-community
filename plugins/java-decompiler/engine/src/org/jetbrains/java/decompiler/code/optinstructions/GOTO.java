// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code.optinstructions;

import org.jetbrains.java.decompiler.code.JumpInstruction;

import java.io.DataOutputStream;
import java.io.IOException;

public class GOTO extends JumpInstruction {

  public void writeToStream(DataOutputStream out, int offset) throws IOException {
    int operand = getOperand(0);
    if (operand < -32768 || operand > 32767) {
      out.writeByte(opc_goto_w);
      out.writeInt(operand);
    }
    else {
      out.writeByte(opc_goto);
      out.writeShort(operand);
    }
  }

  public int length() {
    int operand = getOperand(0);
    if (operand < -32768 || operand > 32767) {
      return 5;
    }
    else {
      return 3;
    }
  }
}
