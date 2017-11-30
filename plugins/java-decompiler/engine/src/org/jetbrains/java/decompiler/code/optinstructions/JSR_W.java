// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code.optinstructions;

import org.jetbrains.java.decompiler.code.JumpInstruction;

import java.io.DataOutputStream;
import java.io.IOException;

public class JSR_W extends JumpInstruction {

  public void writeToStream(DataOutputStream out, int offset) throws IOException {
    out.writeByte(opc_jsr_w);
    out.writeInt(getOperand(0));
  }

  public int length() {
    return 5;
  }
}
