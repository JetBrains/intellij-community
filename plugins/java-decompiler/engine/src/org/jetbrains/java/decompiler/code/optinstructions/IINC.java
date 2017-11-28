// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code.optinstructions;

import org.jetbrains.java.decompiler.code.Instruction;

import java.io.DataOutputStream;
import java.io.IOException;

public class IINC extends Instruction {

  public void writeToStream(DataOutputStream out, int offset) throws IOException {
    if (wide) {
      out.writeByte(opc_wide);
    }
    out.writeByte(opc_iinc);
    if (wide) {
      out.writeShort(getOperand(0));
      out.writeShort(getOperand(1));
    }
    else {
      out.writeByte(getOperand(0));
      out.writeByte(getOperand(1));
    }
  }

  public int length() {
    return wide ? 6 : 3;
  }
}
