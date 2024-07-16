// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.consts;

import org.jetbrains.java.decompiler.code.CodeConstants;

public class LinkConstant extends PooledConstant {
  public int index1, index2;
  public String className;
  public String elementName;
  public String descriptor;

  public LinkConstant(int type, String className, String elementName, String descriptor) {
    super(type);
    this.className = className;
    this.elementName = elementName;
    this.descriptor = descriptor;

    initConstant();
  }

  public LinkConstant(int type, int index1, int index2) {
    super(type);
    this.index1 = index1;
    this.index2 = index2;
  }

  private void initConstant() {
    if (type == CodeConstants.CONSTANT_Methodref ||
        type == CodeConstants.CONSTANT_InterfaceMethodref ||
        type == CodeConstants.CONSTANT_InvokeDynamic ||
        (type == CodeConstants.CONSTANT_MethodHandle && index1 != CodeConstants.CONSTANT_MethodHandle_REF_getField &&
         index1 != CodeConstants.CONSTANT_MethodHandle_REF_putField)) {
      int parenth = descriptor.indexOf(')');
      if (descriptor.length() < 2 || parenth < 0 || descriptor.charAt(0) != '(') {
        throw new IllegalArgumentException("Invalid descriptor: " + descriptor +
                                           "; type = " + type + "; className = " + className + "; elementName = " + elementName);
      }
    }
  }

  @Override
  public void resolveConstant(ConstantPool pool) {
    if (type == CodeConstants.CONSTANT_NameAndType) {
      elementName = pool.getPrimitiveConstant(index1).getString();
      descriptor = pool.getPrimitiveConstant(index2).getString();
    }
    else if (type == CodeConstants.CONSTANT_MethodHandle) {
      LinkConstant ref_info = pool.getLinkConstant(index2);

      className = ref_info.className;
      elementName = ref_info.elementName;
      descriptor = ref_info.descriptor;
    }
    else {
      if (type != CodeConstants.CONSTANT_InvokeDynamic && type != CodeConstants.CONSTANT_Dynamic) {
        className = pool.getPrimitiveConstant(index1).getString();
      }

      LinkConstant nameType = pool.getLinkConstant(index2);
      elementName = nameType.elementName;
      descriptor = nameType.descriptor;
    }

    initConstant();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof LinkConstant cn)) return false;

    return this.type == cn.type &&
           this.elementName.equals(cn.elementName) &&
           this.descriptor.equals(cn.descriptor) &&
           (this.type != CodeConstants.CONSTANT_NameAndType || this.className.equals(cn.className));
  }
}
