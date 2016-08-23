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
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/*
  u2 local_variable_table_length;
  local_variable {
    u2 start_pc;
    u2 length;
    u2 name_index;
    u2 descriptor_index;
    u2 index;
  }
*/
public class StructLocalVariableTableAttribute extends StructGeneralAttribute {

  private Map<Integer, String> mapVarNames = Collections.emptyMap();

  @Override
  public void initContent(ConstantPool pool) throws IOException {
    DataInputFullStream data = stream();

    int len = data.readUnsignedShort();
    if (len > 0) {
      mapVarNames = new HashMap<>(len);
      for (int i = 0; i < len; i++) {
        data.discard(4);
        int nameIndex = data.readUnsignedShort();
        data.discard(2);
        int varIndex = data.readUnsignedShort();
        mapVarNames.put(varIndex, pool.getPrimitiveConstant(nameIndex).getString());
      }
    }
    else {
      mapVarNames = Collections.emptyMap();
    }
  }

  public void addLocalVariableTable(StructLocalVariableTableAttribute attr) {
    mapVarNames.putAll(attr.getMapVarNames());
  }

  public Map<Integer, String> getMapVarNames() {
    return mapVarNames;
  }
}
