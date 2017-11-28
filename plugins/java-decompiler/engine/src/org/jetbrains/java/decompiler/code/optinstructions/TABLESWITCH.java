// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code.optinstructions;

import org.jetbrains.java.decompiler.code.SwitchInstruction;

import java.io.DataOutputStream;
import java.io.IOException;

public class TABLESWITCH extends SwitchInstruction {

  public void writeToStream(DataOutputStream out, int offset) throws IOException {

    out.writeByte(opc_tableswitch);

    int padding = 3 - (offset % 4);
    for (int i = 0; i < padding; i++) {
      out.writeByte(0);
    }

    for (int i = 0; i < operandsCount(); i++) {
      out.writeInt(getOperand(i));
    }
  }

  public int length() {
    return 1 + operandsCount() * 4;
  }
}
