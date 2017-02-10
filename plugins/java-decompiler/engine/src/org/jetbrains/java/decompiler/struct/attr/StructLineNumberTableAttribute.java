/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.IOException;

/**
 * u2 line_number_table_length;
 * {  u2 start_pc;
 *    u2 line_number;
 * } line_number_table[line_number_table_length];
 *
 * Created by Egor on 05.10.2014.
 */
public class StructLineNumberTableAttribute extends StructGeneralAttribute {
  private int[] myLineInfo = InterpreterUtil.EMPTY_INT_ARRAY;

  @Override
  public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException {
    int len = data.readUnsignedShort() * 2;
    if (len > 0) {
      myLineInfo = new int[len];
      for (int i = 0; i < len; i += 2) {
        myLineInfo[i] = data.readUnsignedShort();
        myLineInfo[i + 1] = data.readUnsignedShort();
      }
    }
    else if (myLineInfo.length > 0) {
      myLineInfo = InterpreterUtil.EMPTY_INT_ARRAY;
    }
  }

  public int getFirstLine() {
    return myLineInfo.length > 0 ? myLineInfo[1] : -1;
  }

  public int findLineNumber(int pc) {
    if (myLineInfo.length >= 2) {
      for (int i = myLineInfo.length - 2; i >= 0; i -= 2) {
        if (pc >= myLineInfo[i]) {
          return myLineInfo[i + 1];
        }
      }
    }
    return -1;
  }

  public int[] getRawData() {
    return myLineInfo;
  }
}
