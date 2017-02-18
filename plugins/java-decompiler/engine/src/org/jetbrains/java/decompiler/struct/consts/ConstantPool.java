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
package org.jetbrains.java.decompiler.struct.consts;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.renamer.PoolInterceptor;
import org.jetbrains.java.decompiler.struct.gen.FieldDescriptor;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.NewClassNameBuilder;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

@SuppressWarnings("AssignmentToForLoopParameter")
public class ConstantPool implements NewClassNameBuilder {
  public static final int FIELD = 1;
  public static final int METHOD = 2;

  private final List<PooledConstant> pool;
  private final PoolInterceptor interceptor;

  public ConstantPool(DataInputStream in) throws IOException {
    int size = in.readUnsignedShort();
    pool = new ArrayList<>(size);
    BitSet[] nextPass = {new BitSet(size), new BitSet(size), new BitSet(size)};

    // first dummy constant
    pool.add(null);

    // first pass: read the elements
    for (int i = 1; i < size; i++) {
      byte tag = (byte)in.readUnsignedByte();

      switch (tag) {
        case CodeConstants.CONSTANT_Utf8:
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Utf8, in.readUTF()));
          break;

        case CodeConstants.CONSTANT_Integer:
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Integer, new Integer(in.readInt())));
          break;

        case CodeConstants.CONSTANT_Float:
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Float, new Float(in.readFloat())));
          break;

        case CodeConstants.CONSTANT_Long:
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Long, new Long(in.readLong())));
          pool.add(null);
          i++;
          break;

        case CodeConstants.CONSTANT_Double:
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Double, new Double(in.readDouble())));
          pool.add(null);
          i++;
          break;

        case CodeConstants.CONSTANT_Class:
        case CodeConstants.CONSTANT_String:
        case CodeConstants.CONSTANT_MethodType:
          pool.add(new PrimitiveConstant(tag, in.readUnsignedShort()));
          nextPass[0].set(i);
          break;

        case CodeConstants.CONSTANT_NameAndType:
          pool.add(new LinkConstant(tag, in.readUnsignedShort(), in.readUnsignedShort()));
          nextPass[0].set(i);
          break;

        case CodeConstants.CONSTANT_Fieldref:
        case CodeConstants.CONSTANT_Methodref:
        case CodeConstants.CONSTANT_InterfaceMethodref:
        case CodeConstants.CONSTANT_InvokeDynamic:
          pool.add(new LinkConstant(tag, in.readUnsignedShort(), in.readUnsignedShort()));
          nextPass[1].set(i);
          break;

        case CodeConstants.CONSTANT_MethodHandle:
          pool.add(new LinkConstant(tag, in.readUnsignedByte(), in.readUnsignedShort()));
          nextPass[2].set(i);
          break;
      }
    }

    // resolving complex pool elements
    for (BitSet pass : nextPass) {
      int idx = 0;
      while ((idx = pass.nextSetBit(idx + 1)) > 0) {
        pool.get(idx).resolveConstant(this);
      }
    }

    // get global constant pool interceptor instance, if any available
    interceptor = DecompilerContext.getPoolInterceptor();
  }

  public static void skipPool(DataInputFullStream in) throws IOException {
    int size = in.readUnsignedShort();

    for (int i = 1; i < size; i++) {
      switch (in.readUnsignedByte()) {
        case CodeConstants.CONSTANT_Utf8:
          in.readUTF();
          break;

        case CodeConstants.CONSTANT_Integer:
        case CodeConstants.CONSTANT_Float:
        case CodeConstants.CONSTANT_Fieldref:
        case CodeConstants.CONSTANT_Methodref:
        case CodeConstants.CONSTANT_InterfaceMethodref:
        case CodeConstants.CONSTANT_NameAndType:
        case CodeConstants.CONSTANT_InvokeDynamic:
          in.discard(4);
          break;

        case CodeConstants.CONSTANT_Long:
        case CodeConstants.CONSTANT_Double:
          in.discard(8);
          i++;
          break;

        case CodeConstants.CONSTANT_Class:
        case CodeConstants.CONSTANT_String:
        case CodeConstants.CONSTANT_MethodType:
          in.discard(2);
          break;

        case CodeConstants.CONSTANT_MethodHandle:
          in.discard(3);
      }
    }
  }

  public int size() {
    return pool.size();
  }

  public String[] getClassElement(int elementType, String className, int nameIndex, int descriptorIndex) {
    String elementName = ((PrimitiveConstant)getConstant(nameIndex)).getString();
    String descriptor = ((PrimitiveConstant)getConstant(descriptorIndex)).getString();

    if (interceptor != null) {
      String oldClassName = interceptor.getOldName(className);
      if (oldClassName != null) {
        className = oldClassName;
      }

      String newElement = interceptor.getName(className + ' ' + elementName + ' ' + descriptor);
      if (newElement != null) {
        elementName = newElement.split(" ")[1];
      }

      String newDescriptor = buildNewDescriptor(elementType == FIELD, descriptor);
      if (newDescriptor != null) {
        descriptor = newDescriptor;
      }
    }

    return new String[]{elementName, descriptor};
  }

  public PooledConstant getConstant(int index) {
    return pool.get(index);
  }

  public PrimitiveConstant getPrimitiveConstant(int index) {
    PrimitiveConstant cn = (PrimitiveConstant)getConstant(index);

    if (cn != null && interceptor != null) {
      if (cn.type == CodeConstants.CONSTANT_Class) {
        String newName = buildNewClassname(cn.getString());
        if (newName != null) {
          cn = new PrimitiveConstant(CodeConstants.CONSTANT_Class, newName);
        }
      }
    }

    return cn;
  }

  public LinkConstant getLinkConstant(int index) {
    LinkConstant ln = (LinkConstant)getConstant(index);

    if (ln != null && interceptor != null &&
        (ln.type == CodeConstants.CONSTANT_Fieldref ||
         ln.type == CodeConstants.CONSTANT_Methodref ||
         ln.type == CodeConstants.CONSTANT_InterfaceMethodref)) {
      String newClassName = buildNewClassname(ln.classname);
      String newElement = interceptor.getName(ln.classname + ' ' + ln.elementname + ' ' + ln.descriptor);
      String newDescriptor = buildNewDescriptor(ln.type == CodeConstants.CONSTANT_Fieldref, ln.descriptor);
      //TODO: Fix newElement being null caused by ln.classname being a leaf class instead of the class that declared the field/method.
      //See the comments of IDEA-137253 for more information.
      if (newClassName != null || newElement != null || newDescriptor != null) {
        String className = newClassName == null ? ln.classname : newClassName;
        String elementName = newElement == null ? ln.elementname : newElement.split(" ")[1];
        String descriptor = newDescriptor == null ? ln.descriptor : newDescriptor;
        ln = new LinkConstant(ln.type, className, elementName, descriptor);
      }
    }

    return ln;
  }

  @Override
  public String buildNewClassname(String className) {
    VarType vt = new VarType(className, true);

    String newName = interceptor.getName(vt.value);
    if (newName != null) {
      StringBuilder buffer = new StringBuilder();

      if (vt.arrayDim > 0) {
        for (int i = 0; i < vt.arrayDim; i++) {
          buffer.append('[');
        }

        buffer.append('L').append(newName).append(';');
      }
      else {
        buffer.append(newName);
      }

      return buffer.toString();
    }

    return null;
  }

  private String buildNewDescriptor(boolean isField, String descriptor) {
    if (isField) {
      return FieldDescriptor.parseDescriptor(descriptor).buildNewDescriptor(this);
    }
    else {
      return MethodDescriptor.parseDescriptor(descriptor).buildNewDescriptor(this);
    }
  }
}