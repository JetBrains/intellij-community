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

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StructInnerClassesAttribute extends StructGeneralAttribute {

  private List<int[]> classEntries;
  private List<String[]> stringEntries;

  @Override
  public void initContent(ConstantPool pool) throws IOException {
    DataInputStream data = stream();

    int len = data.readUnsignedShort();
    if (len > 0) {
      classEntries = new ArrayList<int[]>(len);
      stringEntries = new ArrayList<String[]>(len);

      for (int i = 0; i < len; i++) {
        int[] classEntry = new int[4];
        for (int j = 0; j < 4; j++) {
          classEntry[j] = data.readUnsignedShort();
        }
        classEntries.add(classEntry);

        // inner name, enclosing class, original simple name
        String[] stringEntry = new String[3];
        stringEntry[0] = pool.getPrimitiveConstant(classEntry[0]).getString();
        if (classEntry[1] != 0) {
          stringEntry[1] = pool.getPrimitiveConstant(classEntry[1]).getString();
        }
        if (classEntry[2] != 0) {
          stringEntry[2] = pool.getPrimitiveConstant(classEntry[2]).getString();
        }
        stringEntries.add(stringEntry);
      }
    }
    else {
      classEntries = Collections.emptyList();
      stringEntries = Collections.emptyList();
    }
  }

  public List<int[]> getClassEntries() {
    return classEntries;
  }

  public List<String[]> getStringEntries() {
    return stringEntries;
  }
}
