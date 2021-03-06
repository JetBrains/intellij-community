// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTableAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTypeTableAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class StructMember {
  private final int accessFlags;
  private final Map<String, StructGeneralAttribute> attributes;

  protected StructMember(int accessFlags, Map<String, StructGeneralAttribute> attributes) {
    this.accessFlags = accessFlags;
    this.attributes = attributes;
  }

  public int getAccessFlags() {
    return accessFlags;
  }

  public <T extends StructGeneralAttribute> T getAttribute(StructGeneralAttribute.Key<T> attribute) {
    @SuppressWarnings("unchecked") T t = (T)attributes.get(attribute.name);
    return t;
  }

  public boolean hasAttribute(StructGeneralAttribute.Key<?> attribute) {
    return attributes.containsKey(attribute.name);
  }

  public boolean hasModifier(int modifier) {
    return (accessFlags & modifier) == modifier;
  }

  public boolean isSynthetic() {
    return hasModifier(CodeConstants.ACC_SYNTHETIC) || hasAttribute(StructGeneralAttribute.ATTRIBUTE_SYNTHETIC);
  }

  public static Map<String, StructGeneralAttribute> readAttributes(DataInputFullStream in, ConstantPool pool) throws IOException {
    int length = in.readUnsignedShort();
    Map<String, StructGeneralAttribute> attributes = new HashMap<>(length);

    for (int i = 0; i < length; i++) {
      int nameIndex = in.readUnsignedShort();
      String name = pool.getPrimitiveConstant(nameIndex).getString();

      StructGeneralAttribute attribute = StructGeneralAttribute.createAttribute(name);
      int attLength = in.readInt();
      if (attribute == null) {
        in.discard(attLength);
      }
      else {
        attribute.initContent(in, pool);
        if (StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TABLE.name.equals(name) && attributes.containsKey(name)) {
          // merge all variable tables
          StructLocalVariableTableAttribute table = (StructLocalVariableTableAttribute)attributes.get(name);
          table.add((StructLocalVariableTableAttribute)attribute);
        }
        else if (StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TYPE_TABLE.name.equals(name) && attributes.containsKey(name)) {
          // merge all variable tables
          StructLocalVariableTypeTableAttribute table = (StructLocalVariableTypeTableAttribute)attributes.get(name);
          table.add((StructLocalVariableTypeTableAttribute)attribute);
        }
        else {
          attributes.put(name, attribute);
        }
      }
    }

    return attributes;
  }
}
