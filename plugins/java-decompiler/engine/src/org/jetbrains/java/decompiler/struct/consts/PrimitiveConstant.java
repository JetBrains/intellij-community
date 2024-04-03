// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.consts;

import org.jetbrains.java.decompiler.code.CodeConstants;

public class PrimitiveConstant extends PooledConstant {
  public int index;
  public Object value;
  public boolean isArray;

  public PrimitiveConstant(int type, Object value) {
    super(type);
    this.value = value;

    initConstant();
  }

  public PrimitiveConstant(int type, int index) {
    super(type);
    this.index = index;
  }

  private void initConstant() {
    if (type == CodeConstants.CONSTANT_Class) {
      String className = getString();
      isArray = (!className.isEmpty() && className.charAt(0) == '['); // empty string for a class name seems to be possible in some android files
    }
  }

  public String getString() {
    return (String)value;
  }

  @Override
  public void resolveConstant(ConstantPool pool) {
    if (type == CodeConstants.CONSTANT_Class || type == CodeConstants.CONSTANT_String || type == CodeConstants.CONSTANT_MethodType ||
        type == CodeConstants.CONSTANT_Module || type == CodeConstants.CONSTANT_Package) {
      value = pool.getPrimitiveConstant(index).getString();
      initConstant();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof PrimitiveConstant cn)) return false;

    return this.type == cn.type &&
           this.isArray == cn.isArray &&
           this.value.equals(cn.value);
  }
}
