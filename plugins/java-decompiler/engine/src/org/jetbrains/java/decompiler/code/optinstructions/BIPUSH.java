/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
