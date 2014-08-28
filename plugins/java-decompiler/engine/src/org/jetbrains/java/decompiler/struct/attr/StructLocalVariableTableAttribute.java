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

import java.util.HashMap;

public class StructLocalVariableTableAttribute extends StructGeneralAttribute {

  private HashMap<Integer, String> mapVarNames = new HashMap<Integer, String>();

  public void initContent(ConstantPool pool) {

    name = ATTRIBUTE_LOCAL_VARIABLE_TABLE;

    int len = ((info[0] & 0xFF) << 8) | (info[1] & 0xFF);

    int ind = 6;
    for (int i = 0; i < len; i++, ind += 10) {
      int nindex = ((info[ind] & 0xFF) << 8) | (info[ind + 1] & 0xFF);
      int vindex = ((info[ind + 4] & 0xFF) << 8) | (info[ind + 5] & 0xFF);

      mapVarNames.put(vindex, pool.getPrimitiveConstant(nindex).getString());
    }
  }

  public void addLocalVariableTable(StructLocalVariableTableAttribute attr) {
    mapVarNames.putAll(attr.getMapVarNames());
  }

  public HashMap<Integer, String> getMapVarNames() {
    return mapVarNames;
  }
}
