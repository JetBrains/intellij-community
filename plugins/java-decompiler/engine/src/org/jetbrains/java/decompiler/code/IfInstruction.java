// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code;

import java.io.DataOutputStream;
import java.io.IOException;

/*
 * opc_ifeq, opc_ifne, opc_iflt, opc_ifge, opc_ifgt, opc_ifle, opc_if_icmpeq, opc_if_icmpne, opc_if_icmplt,
 * opc_if_icmpge, opc_if_icmpgt, opc_if_icmple, opc_if_acmpeq, opc_if_acmpne, opc_ifnull, opc_ifnonnull
 */

public class IfInstruction extends JumpInstruction {

  public void writeToStream(DataOutputStream out, int offset) throws IOException {
    out.writeByte(opcode);
    out.writeShort(getOperand(0));
  }

  public int length() {
    return 3;
  }
}
