// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/*
  PermittedSubclasses_attribute {
      u2 attribute_name_index;
      u4 attribute_length;
      u2 number_of_classes;
      u2 classes[number_of_classes];
  }
 */
public class StructPermittedSubclassesAttribute extends StructGeneralAttribute {
  List<String> classes;

  @Override
  public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException {
    int numberOfClasses = data.readUnsignedShort();
    String[] classes = new String[numberOfClasses];
    for (int i = 0; i < numberOfClasses; i++) {
      classes[i] = pool.getPrimitiveConstant(data.readUnsignedShort()).getString();
    }
    this.classes = Arrays.asList(classes);
  }

  public List<String> getClasses() {
    return classes;
  }
}
