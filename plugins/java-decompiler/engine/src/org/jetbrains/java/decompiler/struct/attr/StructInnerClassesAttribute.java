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

import java.util.ArrayList;
import java.util.List;

public class StructInnerClassesAttribute extends StructGeneralAttribute {

  private List<int[]> classentries = new ArrayList<int[]>();

  private List<String[]> stringentries = new ArrayList<String[]>();

  public void initContent(ConstantPool pool) {

    name = ATTRIBUTE_INNER_CLASSES;

    int length = 2 + (((info[0] & 0xFF) << 8) | (info[1] & 0xFF)) * 8;
    int i = 2;

    while (i < length) {

      int[] arr = new int[4];
      for (int j = 0; j < 4; j++) {
        arr[j] = ((info[i] & 0xFF) << 8) | (info[i + 1] & 0xFF);
        i += 2;
      }

      classentries.add(arr);
    }

    for (int[] entry : classentries) {

      String[] arr = new String[3];
      // inner name
      arr[0] = pool.getPrimitiveConstant(entry[0]).getString();
      //enclosing class
      if (entry[1] != 0) {
        arr[1] = pool.getPrimitiveConstant(entry[1]).getString();
      }
      // original simple name
      if (entry[2] != 0) {
        arr[2] = pool.getPrimitiveConstant(entry[2]).getString();
      }

      stringentries.add(arr);
    }
  }

  public List<int[]> getClassentries() {
    return classentries;
  }

  public List<String[]> getStringentries() {
    return stringentries;
  }
}
