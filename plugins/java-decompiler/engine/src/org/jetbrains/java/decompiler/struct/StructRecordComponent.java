// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;
import java.util.Map;

/*
  record_component_info {
    u2 name_index;
    u2 descriptor_index;
    u2 attributes_count;
    attribute_info attributes[attributes_count];
   }
*/
public class StructRecordComponent extends StructField {
  public static StructRecordComponent create(DataInputFullStream in, ConstantPool pool) throws IOException {
    int nameIndex = in.readUnsignedShort();
    int descriptorIndex = in.readUnsignedShort();

    String name = ((PrimitiveConstant)pool.getConstant(nameIndex)).getString();
    String descriptor = ((PrimitiveConstant)pool.getConstant(descriptorIndex)).getString();

    Map<String, StructGeneralAttribute> attributes = readAttributes(in, pool);

    return new StructRecordComponent(0, attributes, name, descriptor);
  }

  private StructRecordComponent(int flags, Map<String, StructGeneralAttribute> attributes, String name, String descriptor) {
    super(flags, attributes, name, descriptor);
  }
}
