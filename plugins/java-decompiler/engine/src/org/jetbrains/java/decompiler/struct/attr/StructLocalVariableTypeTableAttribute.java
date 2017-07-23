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
import java.util.Set;

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
  // store signature instead of descriptor
  private final StructLocalVariableTableAttribute backingAttribute = new StructLocalVariableTableAttribute();

  @Override
  public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException {
    backingAttribute.initContent(data, pool);
  }

  public void add(StructLocalVariableTypeTableAttribute attr) {
    backingAttribute.add(attr.backingAttribute);
  }

  public String getSignature(int index, int visibleOffset) {
    return backingAttribute.getDescriptor(index, visibleOffset);
  }
}
