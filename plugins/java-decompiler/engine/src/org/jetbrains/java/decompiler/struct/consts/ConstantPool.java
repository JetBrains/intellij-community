// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct.consts;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.ClassFormatException;
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
        case CodeConstants.CONSTANT_Utf8 ->
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Utf8, in.readUTF()));
        case CodeConstants.CONSTANT_Integer ->
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Integer, Integer.valueOf(in.readInt())));
        case CodeConstants.CONSTANT_Float ->
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Float, in.readFloat()));
        case CodeConstants.CONSTANT_Long -> {
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Long, in.readLong()));
          pool.add(null);
          i++;
        }
        case CodeConstants.CONSTANT_Double -> {
          pool.add(new PrimitiveConstant(CodeConstants.CONSTANT_Double, in.readDouble()));
          pool.add(null);
          i++;
        }
        case CodeConstants.CONSTANT_Class, CodeConstants.CONSTANT_String, CodeConstants.CONSTANT_MethodType, CodeConstants.CONSTANT_Module, CodeConstants.CONSTANT_Package -> {
          pool.add(new PrimitiveConstant(tag, in.readUnsignedShort()));
          nextPass[0].set(i);
        }
        case CodeConstants.CONSTANT_NameAndType -> {
          pool.add(new LinkConstant(tag, in.readUnsignedShort(), in.readUnsignedShort()));
          nextPass[0].set(i);
        }
        case CodeConstants.CONSTANT_Fieldref, CodeConstants.CONSTANT_Methodref, CodeConstants.CONSTANT_InterfaceMethodref, CodeConstants.CONSTANT_Dynamic, CodeConstants.CONSTANT_InvokeDynamic -> {
          pool.add(new LinkConstant(tag, in.readUnsignedShort(), in.readUnsignedShort()));
          nextPass[1].set(i);
        }
        case CodeConstants.CONSTANT_MethodHandle -> {
          pool.add(new LinkConstant(tag, in.readUnsignedByte(), in.readUnsignedShort()));
          nextPass[2].set(i);
        }
        default ->
          // Fail-fast on unknown constant pool entry.
          // We have no chance to process this class correctly.
          throw new ClassFormatException(
            String.format("Unsupported constant pool entry type %d at index #%d! ", Byte.toUnsignedInt(tag), i));
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
        case CodeConstants.CONSTANT_Utf8 -> in.readUTF();
        case CodeConstants.CONSTANT_Integer, CodeConstants.CONSTANT_Float, CodeConstants.CONSTANT_Fieldref, CodeConstants.CONSTANT_Methodref, CodeConstants.CONSTANT_InterfaceMethodref, CodeConstants.CONSTANT_NameAndType, CodeConstants.CONSTANT_Dynamic, CodeConstants.CONSTANT_InvokeDynamic ->
          in.discard(4);
        case CodeConstants.CONSTANT_Long, CodeConstants.CONSTANT_Double -> {
          in.discard(8);
          i++;
        }
        case CodeConstants.CONSTANT_Class, CodeConstants.CONSTANT_String, CodeConstants.CONSTANT_MethodType -> in.discard(2);
        case CodeConstants.CONSTANT_MethodHandle -> in.discard(3);
      }
    }
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

    String newName = interceptor.getName(vt.getValue());
    if (newName != null) {
      StringBuilder buffer = new StringBuilder();
      if (vt.getArrayDim() > 0) {
        buffer.append("[".repeat(vt.getArrayDim())).append('L').append(newName).append(';');
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
