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
    fields = new VBStyleCollection<>();
    for (int i = 0; i < length; i++) {
      StructField field = new StructField(in, this);
      fields.addWithKey(field, InterpreterUtil.makeUniqueKey(field.getName(), field.getDescriptor()));
    }

    // methods
    length = in.readUnsignedShort();
    methods = new VBStyleCollection<>();
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
    return (majorVersion > 48 || (majorVersion == 48 && minorVersion > 0)); // FIXME: check second condition
  }

  public boolean isVersionGE_1_7() {
    return (majorVersion >= 51);
  }

  public int getBytecodeVersion() {
    switch (majorVersion) {
      case 53:
        return CodeConstants.BYTECODE_JAVA_9;
      case 52:
        return CodeConstants.BYTECODE_JAVA_8;
      case 51:
        return CodeConstants.BYTECODE_JAVA_7;
      case 50:
        return CodeConstants.BYTECODE_JAVA_6;
      case 49:
        return CodeConstants.BYTECODE_JAVA_5;
    }

    return CodeConstants.BYTECODE_JAVA_LE_4;
  }

  @Override
  public String toString() {
    return qualifiedName;
  }
}
