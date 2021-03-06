// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
  u2 local_variable_table_length;
  local_variable {
    u2 start_pc;
    u2 length;
    u2 name_index;
    u2 descriptor_index;
    u2 index;
  }
*/
public class StructLocalVariableTableAttribute extends StructGeneralAttribute {
  private List<LocalVariable> localVariables = Collections.emptyList();

  @Override
  public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException {
    int len = data.readUnsignedShort();
    if (len > 0) {
      localVariables = new ArrayList<>(len);

      for (int i = 0; i < len; i++) {
        int start_pc = data.readUnsignedShort();
        int length = data.readUnsignedShort();
        int nameIndex = data.readUnsignedShort();
        int descriptorIndex = data.readUnsignedShort();
        int varIndex = data.readUnsignedShort();
        localVariables.add(new LocalVariable(start_pc,
                                             length,
                                             pool.getPrimitiveConstant(nameIndex).getString(),
                                             pool.getPrimitiveConstant(descriptorIndex).getString(),
                                             varIndex));
      }
    }
    else {
      localVariables = Collections.emptyList();
    }
  }

  public void add(StructLocalVariableTableAttribute attr) {
    localVariables.addAll(attr.localVariables);
  }

  public String getName(int index, int visibleOffset) {
    return matchingVars(index, visibleOffset).map(v -> v.name).findFirst().orElse(null);
  }

  public String getDescriptor(int index, int visibleOffset) {
    return matchingVars(index, visibleOffset).map(v -> v.descriptor).findFirst().orElse(null);
  }

  private Stream<LocalVariable> matchingVars(int index, int visibleOffset) {
    return localVariables.stream()
      .filter(v -> v.index == index && (visibleOffset >= v.start_pc && visibleOffset < v.start_pc + v.length));
  }

  public boolean containsName(String name) {
    return localVariables.stream().anyMatch(v -> Objects.equals(v.name, name));
  }

  public Map<Integer, String> getMapParamNames() {
    return localVariables.stream().filter(v -> v.start_pc == 0).collect(Collectors.toMap(v -> v.index, v -> v.name, (n1, n2) -> n2));
  }

  private static final class LocalVariable {
    final int start_pc;
    final int length;
    final String name;
    final String descriptor;
    final int index;

    private LocalVariable(int start_pc, int length, String name, String descriptor, int index) {
      this.start_pc = start_pc;
      this.length = length;
      this.name = name;
      this.descriptor = descriptor;
      this.index = index;
    }
  }
}
