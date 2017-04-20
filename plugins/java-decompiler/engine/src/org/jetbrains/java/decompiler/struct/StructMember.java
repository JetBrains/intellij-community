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

public class StructMember {

  protected int accessFlags;
  protected Map<String, StructGeneralAttribute> attributes;


  public int getAccessFlags() {
    return accessFlags;
  }

  public StructGeneralAttribute getAttribute(String name) {
    return attributes.get(name);
  }

  public boolean hasAttribute(String name) {
    return attributes.containsKey(name);
  }

  public boolean hasModifier(int modifier) {
    return (accessFlags & modifier) == modifier;
  }

  public boolean isSynthetic() {
    return hasModifier(CodeConstants.ACC_SYNTHETIC) || hasAttribute(StructGeneralAttribute.ATTRIBUTE_SYNTHETIC);
  }

  protected Map<String, StructGeneralAttribute> readAttributes(DataInputFullStream in, ConstantPool pool) throws IOException {
    int length = in.readUnsignedShort();

    Map<String, StructGeneralAttribute> attributes = new HashMap<>(length);
    for (int i = 0; i < length; i++) {
      int nameIndex = in.readUnsignedShort();
      String name = pool.getPrimitiveConstant(nameIndex).getString();

      StructGeneralAttribute attribute = readAttribute(in, pool, name);

      if (attribute != null) {
        if (StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TABLE.equals(name) && attributes.containsKey(name)) {
          // merge all variable tables
          StructLocalVariableTableAttribute table = (StructLocalVariableTableAttribute)attributes.get(name);
          table.add((StructLocalVariableTableAttribute)attribute);
        }
        else if (StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TYPE_TABLE.equals(name) && attributes.containsKey(name)) {
          // merge all variable tables
          StructLocalVariableTypeTableAttribute table = (StructLocalVariableTypeTableAttribute)attributes.get(name);
          table.add((StructLocalVariableTypeTableAttribute)attribute);
        }
        else {
          attributes.put(attribute.getName(), attribute);
        }
      }
    }

    return attributes;
  }

  protected StructGeneralAttribute readAttribute(DataInputFullStream in, ConstantPool pool, String name) throws IOException {
    StructGeneralAttribute attribute = StructGeneralAttribute.createAttribute(name);
    int length = in.readInt();
    if (attribute == null) {
      in.discard(length);
    }
    else {
      attribute.initContent(in, pool);
    }
    return attribute;
  }
}