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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/*
  u2 local_variable_type_table_length;
    {   u2 start_pc;
        u2 length;
        u2 name_index;
        u2 signature_index;
        u2 index;
    } local_variable_type_table[local_variable_type_table_length];
*/
public class StructLocalVariableTypeTableAttribute extends StructGeneralAttribute {

  private Map<Integer, String> mapVarSignatures = Collections.emptyMap();

  @Override
  public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException {
    int len = data.readUnsignedShort();
    if (len > 0) {
      mapVarSignatures = new HashMap<>(len);
      for (int i = 0; i < len; i++) {
        data.discard(6);
        int signatureIndex = data.readUnsignedShort();
        int varIndex = data.readUnsignedShort();
        mapVarSignatures.put(varIndex, pool.getPrimitiveConstant(signatureIndex).getString());
      }
    }
    else {
      mapVarSignatures = Collections.emptyMap();
    }
  }

  public void add(StructLocalVariableTypeTableAttribute attr) {
    mapVarSignatures.putAll(attr.getMapVarSignatures());
  }

  public Map<Integer, String> getMapVarSignatures() {
    return mapVarSignatures;
  }
}
