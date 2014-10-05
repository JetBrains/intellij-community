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
package org.jetbrains.java.decompiler.struct.attr;

import com.intellij.openapi.util.Pair;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;
import java.util.*;

/**
 * u2 line_number_table_length;
 * {  u2 start_pc;
 *    u2 line_number;
 * } line_number_table[line_number_table_length];
 *
 * Created by Egor on 05.10.2014.
 */
public class StructLineNumberTableAttribute extends StructGeneralAttribute {
  private List<Pair<Integer, Integer>> myLineInfo = Collections.emptyList();

  @Override
  public void initContent(ConstantPool pool) throws IOException {
    DataInputFullStream data = stream();

    int len = data.readUnsignedShort();
    if (len > 0) {
      myLineInfo = new ArrayList<Pair<Integer, Integer>>(len);
      for (int i = 0; i < len; i++) {
        int startPC = data.readUnsignedShort();
        int lineNumber = data.readUnsignedShort();
        myLineInfo.add(Pair.create(startPC, lineNumber));
      }
    }
    else {
      myLineInfo = Collections.emptyList();
    }
  }

  public int getFirstLine() {
    if (!myLineInfo.isEmpty()) {
      return myLineInfo.get(0).getSecond();
    }
    return -1;
  }

  public int findLineNumber(int pc) {
    for (Pair<Integer, Integer> pair : myLineInfo) {
      if (pc >= pair.getFirst()) {
        return pair.getSecond();
      }
    }
    return -1;
  }
}
