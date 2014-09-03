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
