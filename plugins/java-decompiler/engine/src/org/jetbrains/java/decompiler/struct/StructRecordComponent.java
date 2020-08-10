// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;

/*
  record_component_info {
    u2 name_index;
    u2 descriptor_index;
    u2 attributes_count;
    attribute_info attributes[attributes_count];
   }
*/
public class StructRecordComponent extends StructMember {

  private final String name;
  private final String descriptor;


  public StructRecordComponent(DataInputFullStream in, ConstantPool pool) throws IOException {
    accessFlags = 0;
    int nameIndex = in.readUnsignedShort();
    int descriptorIndex = in.readUnsignedShort();

    name = ((PrimitiveConstant)pool.getConstant(nameIndex)).getString();
    descriptor = ((PrimitiveConstant)pool.getConstant(descriptorIndex)).getString();

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
