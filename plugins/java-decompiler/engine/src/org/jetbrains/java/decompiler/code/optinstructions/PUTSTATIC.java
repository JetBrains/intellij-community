// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code.optinstructions;

import org.jetbrains.java.decompiler.code.Instruction;

import java.io.DataOutputStream;
import java.io.IOException;

public class PUTSTATIC extends Instruction {

  public void writeToStream(DataOutputStream out, int offset) throws IOException {
    out.writeByte(opc_putstatic);
    out.writeShort(getOperand(0));
  }

  public int length() {
    return 3;
  }
}
