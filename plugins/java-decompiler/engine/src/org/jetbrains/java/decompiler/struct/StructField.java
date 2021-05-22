// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;
import java.util.Map;

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
  public static StructField create(DataInputFullStream in, ConstantPool pool, String clQualifiedName) throws IOException {
    int accessFlags = in.readUnsignedShort();
    int nameIndex = in.readUnsignedShort();
    int descriptorIndex = in.readUnsignedShort();

    String[] values = pool.getClassElement(ConstantPool.FIELD, clQualifiedName, nameIndex, descriptorIndex);

    Map<String, StructGeneralAttribute> attributes = readAttributes(in, pool);

    return new StructField(accessFlags, attributes, values[0], values[1]);
  }

  private final String name;
  private final String descriptor;

  protected StructField(int accessFlags, Map<String, StructGeneralAttribute> attributes, String name, String descriptor) {
    super(accessFlags, attributes);
    this.name = name;
    this.descriptor = descriptor;
  }

  public final String getName() {
    return name;
  }

  public final String getDescriptor() {
    return descriptor;
  }

  @Override
  public String toString() {
    return name;
  }
}
