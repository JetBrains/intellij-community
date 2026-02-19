// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.gen;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.util.ListStack;

import java.util.ArrayList;
import java.util.List;

public class DataPoint {
  private final List<VarType> localVariables;
  private final ListStack<VarType> stack;

  public DataPoint() {
    this(new ArrayList<>(), new ListStack<>());
  }

  private DataPoint(List<VarType> localVariables, ListStack<VarType> stack) {
    this.localVariables = localVariables;
    this.stack = stack;
  }

  public DataPoint copy() {
    return new DataPoint(new ArrayList<>(localVariables), stack.copy());
  }

  public void setVariable(int index, VarType value) {
    if (index >= localVariables.size()) {
      for (int i = localVariables.size(); i <= index; i++) {
        localVariables.add(new VarType(CodeConstants.TYPE_NOTINITIALIZED));
      }
    }

    localVariables.set(index, value);
  }

  public VarType getVariable(int index) {
    if (index < localVariables.size()) {
      return localVariables.get(index);
    }
    else {
      return new VarType(CodeConstants.TYPE_NOTINITIALIZED);
    }
  }

  public static DataPoint getInitialDataPoint(StructMethod mt) {
    DataPoint point = new DataPoint();

    MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());

    int k = 0;
    if (!mt.hasModifier(CodeConstants.ACC_STATIC)) {
      point.setVariable(k++, new VarType(CodeConstants.TYPE_OBJECT, 0, null));
    }

    for (int i = 0; i < md.params.length; i++) {
      VarType var = md.params[i];

      point.setVariable(k++, var);
      if (var.getStackSize() == 2) {
        point.setVariable(k++, new VarType(CodeConstants.TYPE_GROUP2EMPTY));
      }
    }

    return point;
  }

  public ListStack<VarType> getStack() {
    return stack;
  }
}
