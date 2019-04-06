// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.lazy.LazyLoader;
import org.jetbrains.java.decompiler.util.DataInputFullStream;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.io.IOException;

/*
  class_file {
    u4 magic;
    u2 minor_version;
    u2 major_version;
    u2 constant_pool_count;
    cp_info constant_pool[constant_pool_count-1];
    u2 access_flags;
    u2 this_class;
    u2 super_class;
    u2 interfaces_count;
    u2 interfaces[interfaces_count];
    u2 fields_count;
    field_info fields[fields_count];
    u2 methods_count;
    method_info methods[methods_count];
    u2 attributes_count;
    attribute_info attributes[attributes_count];
  }
*/
public class StructClass extends StructMember {

  public final String qualifiedName;
  public final PrimitiveConstant superClass;

  private final boolean own;
  private final LazyLoader loader;
  private final int minorVersion;
  private final int majorVersion;
  private final int[] interfaces;
  private final String[] interfaceNames;
  private final VBStyleCollection<StructField, String> fields;
  private final VBStyleCollection<StructMethod, String> methods;

  private ConstantPool pool;

  public StructClass(byte[] bytes, boolean own, LazyLoader loader) throws IOException {
    this(new DataInputFullStream(bytes), own, loader);
  }

  public StructClass(DataInputFullStream in, boolean own, LazyLoader loader) throws IOException {
    this.own = own;
    this.loader = loader;

    in.discard(4);

    minorVersion = in.readUnsignedShort();
    majorVersion = in.readUnsignedShort();

    pool = new ConstantPool(in);

    accessFlags = in.readUnsignedShort();
    int thisClassIdx = in.readUnsignedShort();
    int superClassIdx = in.readUnsignedShort();
    qualifiedName = pool.getPrimitiveConstant(thisClassIdx).getString();
    superClass = pool.getPrimitiveConstant(superClassIdx);

    // interfaces
    int length = in.readUnsignedShort();
    interfaces = new int[length];
    interfaceNames = new String[length];
    for (int i = 0; i < length; i++) {
      interfaces[i] = in.readUnsignedShort();
      interfaceNames[i] = pool.getPrimitiveConstant(interfaces[i]).getString();
    }

    // fields
    length = in.readUnsignedShort();
    fields = new VBStyleCollection<>(length);
    for (int i = 0; i < length; i++) {
      StructField field = new StructField(in, this);
      fields.addWithKey(field, InterpreterUtil.makeUniqueKey(field.getName(), field.getDescriptor()));
    }

    // methods
    length = in.readUnsignedShort();
    methods = new VBStyleCollection<>(length);
    for (int i = 0; i < length; i++) {
      StructMethod method = new StructMethod(in, this);
      methods.addWithKey(method, InterpreterUtil.makeUniqueKey(method.getName(), method.getDescriptor()));
    }

    // attributes
    attributes = readAttributes(in, pool);

    releaseResources();
  }

  public boolean hasField(String name, String descriptor) {
    return getField(name, descriptor) != null;
  }

  public StructField getField(String name, String descriptor) {
    return fields.getWithKey(InterpreterUtil.makeUniqueKey(name, descriptor));
  }

  public StructMethod getMethod(String key) {
    return methods.getWithKey(key);
  }

  public StructMethod getMethod(String name, String descriptor) {
    return methods.getWithKey(InterpreterUtil.makeUniqueKey(name, descriptor));
  }

  public String getInterface(int i) {
    return interfaceNames[i];
  }

  public void releaseResources() {
    if (loader != null) {
      pool = null;
    }
  }

  public ConstantPool getPool() {
    if (pool == null && loader != null) {
      pool = loader.loadPool(qualifiedName);
    }
    return pool;
  }

  public int[] getInterfaces() {
    return interfaces;
  }

  public String[] getInterfaceNames() {
    return interfaceNames;
  }

  public VBStyleCollection<StructMethod, String> getMethods() {
    return methods;
  }

  public VBStyleCollection<StructField, String> getFields() {
    return fields;
  }

  public boolean isOwn() {
    return own;
  }

  public LazyLoader getLoader() {
    return loader;
  }

  public boolean isVersionGE_1_5() {
    return (majorVersion > CodeConstants.BYTECODE_JAVA_LE_4 ||
            (majorVersion == CodeConstants.BYTECODE_JAVA_LE_4 && minorVersion > 0)); // FIXME: check second condition
  }

  public boolean isVersionGE_1_7() {
    return (majorVersion >= CodeConstants.BYTECODE_JAVA_7);
  }

  public int getBytecodeVersion() {
    return majorVersion < CodeConstants.BYTECODE_JAVA_LE_4 ? CodeConstants.BYTECODE_JAVA_LE_4 : majorVersion;
  }

  @Override
  public String toString() {
    return qualifiedName;
  }
}
