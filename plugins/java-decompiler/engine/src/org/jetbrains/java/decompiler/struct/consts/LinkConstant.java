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
package org.jetbrains.java.decompiler.struct.consts;

import java.io.DataOutputStream;
import java.io.IOException;

/*
 *   NameAndType, FieldRef, MethodRef, InterfaceMethodref
 *   InvokeDynamic, MethodHandle
 */

public class LinkConstant extends PooledConstant {

  // *****************************************************************************
  // public fields
  // *****************************************************************************

  public int index1, index2;

  public String classname;

  public String elementname;

  public String descriptor;

  public int paramCount = 0;

  public boolean isVoid = false;

  public boolean returnCategory2 = false;


  // *****************************************************************************
  // constructors
  // *****************************************************************************

  public LinkConstant(int type, String classname, String elementname, String descriptor) {
    this.type = type;
    this.classname = classname;
    this.elementname = elementname;
    this.descriptor = descriptor;

    initConstant();
  }

  public LinkConstant(int type, int index1, int index2) {
    this.type = type;
    this.index1 = index1;
    this.index2 = index2;
  }


  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public void resolveConstant(ConstantPool pool) {

    if (type == CONSTANT_NameAndType) {
      elementname = pool.getPrimitiveConstant(index1).getString();
      descriptor = pool.getPrimitiveConstant(index2).getString();
    }
    else if (type == CONSTANT_MethodHandle) {
      LinkConstant ref_info = pool.getLinkConstant(index2);

      classname = ref_info.classname;
      elementname = ref_info.elementname;
      descriptor = ref_info.descriptor;
    }
    else {
      if (type != CONSTANT_InvokeDynamic) {
        classname = pool.getPrimitiveConstant(index1).getString();
      }

      LinkConstant nametype = pool.getLinkConstant(index2);
      elementname = nametype.elementname;
      descriptor = nametype.descriptor;
    }

    initConstant();
  }

  public void writeToStream(DataOutputStream out) throws IOException {
    out.writeByte(type);
    if (type == CONSTANT_MethodHandle) {
      out.writeByte(index1);
    }
    else {
      out.writeShort(index1);
    }
    out.writeShort(index2);
  }


  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || !(o instanceof LinkConstant)) return false;

    LinkConstant cn = (LinkConstant)o;
    return this.type == cn.type &&
           this.elementname.equals(cn.elementname) &&
           this.descriptor.equals(cn.descriptor) &&
           (this.type != CONSTANT_NameAndType || this.classname.equals(cn.classname));
  }

  // *****************************************************************************
  // private methods
  // *****************************************************************************

  private void initConstant() {

    if (type == CONSTANT_Methodref ||
        type == CONSTANT_InterfaceMethodref ||
        type == CONSTANT_InvokeDynamic ||
        type == CONSTANT_MethodHandle) {
      resolveDescriptor(descriptor);
    }
    else if (type == CONSTANT_Fieldref) {
      returnCategory2 = ("D".equals(descriptor) || "J".equals(descriptor));
    }
  }

  private void resolveDescriptor(String descr) {
    int parenth = descr.indexOf(')');
    if (descr.length() < 2 || parenth < 0 || descr.charAt(0) != '(') {
      throw new IllegalArgumentException("Invalid descriptor: " + descr);
    }

    int counter = 0;
    if (parenth > 1) { // params
      int index = 1;
      while (index < parenth) {
        char c = descr.charAt(index);
        if (c == 'L') {
          index = descr.indexOf(";", index);
        }
        else if (c == '[') {
          index++;
          continue;
        }
        counter++;
        index++;
      }
    }

    paramCount = counter;
    char retChar = descr.charAt(parenth + 1);
    isVoid = retChar == 'V';
    returnCategory2 = (retChar == 'D') || (retChar == 'J');
   }
}

