/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.io.IOException;

public class StructMember {

  protected int accessFlags;
  protected VBStyleCollection<StructGeneralAttribute, String> attributes;


  public int getAccessFlags() {
    return accessFlags;
  }

  public VBStyleCollection<StructGeneralAttribute, String> getAttributes() {
    return attributes;
  }

  public boolean hasModifier(int modifier) {
    return (accessFlags & modifier) == modifier;
  }

  public boolean isSynthetic() {
    return hasModifier(CodeConstants.ACC_SYNTHETIC) || attributes.containsKey(StructGeneralAttribute.ATTRIBUTE_SYNTHETIC);
  }

  protected VBStyleCollection<StructGeneralAttribute, String> readAttributes(DataInputFullStream in, ConstantPool pool) throws IOException {
    VBStyleCollection<StructGeneralAttribute, String> attributes = new VBStyleCollection<>();

    int length = in.readUnsignedShort();
    for (int i = 0; i < length; i++) {
      int nameIndex = in.readUnsignedShort();
      String name = pool.getPrimitiveConstant(nameIndex).getString();

      StructGeneralAttribute attribute = readAttribute(in, pool, name);

      if (attribute != null) {
        if (StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TABLE.equals(name) && attributes.containsKey(name)) {
          // merge all variable tables
          StructLocalVariableTableAttribute table = (StructLocalVariableTableAttribute)attributes.getWithKey(name);
          table.addLocalVariableTable((StructLocalVariableTableAttribute)attribute);
        }
        else {
          attributes.addWithKey(attribute, attribute.getName());
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
      byte[] data = in.read(length);
      attribute.setInfo(data);
      attribute.initContent(pool);
    }
    return attribute;
  }
}