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
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;

/*
  field_info {
    u2 access_flags;
    u2 name_index;
    u2 descriptor_index;
    u2 attributes_count;
    attribute_info attributes[attributes_count];
   }
*/
public class StructField extends StructMember {

  private final String name;
  private final String descriptor;


  public StructField(DataInputFullStream in, StructClass clStruct) throws IOException {
    accessFlags = in.readUnsignedShort();
    int nameIndex = in.readUnsignedShort();
    int descriptorIndex = in.readUnsignedShort();

    ConstantPool pool = clStruct.getPool();
    String[] values = pool.getClassElement(ConstantPool.FIELD, clStruct.qualifiedName, nameIndex, descriptorIndex);
    name = values[0];
    descriptor = values[1];

    attributes = readAttributes(in, pool);
  }

  public String getName() {
    return name;
  }

  public String getDescriptor() {
    return descriptor;
  }

  @Override
  public String toString() {
    return name;
  }
}
