// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.struct.StructMember;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;
import java.util.Map;

/*
  u2 max_stack;
  u2 max_locals;
  u4 code_length;
  u1 code[];
  u2 exception_table_length;
  exception_table[] {
     u2 start_pc;
     u2 end_pc;
     u2 handler_pc;
     u2 catch_type;
  };
  u2 attributes_count;
  attribute_info attributes[];
*/
public class StructCodeAttribute extends StructGeneralAttribute {
  public int localVariables = 0;
  public int codeLength = 0;
  public int codeFullLength = 0;
  public Map<String, StructGeneralAttribute> codeAttributes;

  @Override
  public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException {
    data.discard(2);
    localVariables = data.readUnsignedShort();
    codeLength = data.readInt();
    data.discard(codeLength);
    int excLength = data.readUnsignedShort();
    data.discard(excLength * 8);
    codeFullLength = codeLength + excLength * 8 + 2;
    codeAttributes = StructMember.readAttributes(data, pool);
  }
}
