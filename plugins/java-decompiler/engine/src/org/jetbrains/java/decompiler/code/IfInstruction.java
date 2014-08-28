/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
