// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructPermittedSubclassesAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructRecordAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.consts.PrimitiveConstant;
import org.jetbrains.java.decompiler.struct.gen.Type;
import org.jetbrains.java.decompiler.struct.lazy.LazyLoader;
import org.jetbrains.java.decompiler.util.DataInputFullStream;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
  public static StructClass create(DataInputFullStream in, boolean own, LazyLoader loader) throws IOException {
    in.discard(4);
    int minorVersion = in.readUnsignedShort();
    int majorVersion = in.readUnsignedShort();
    int bytecodeVersion = Math.max(majorVersion, CodeConstants.BYTECODE_JAVA_LE_4);

    ConstantPool pool = new ConstantPool(in);

    int accessFlags = in.readUnsignedShort();
    int thisClassIdx = in.readUnsignedShort();
    int superClassIdx = in.readUnsignedShort();
    String qualifiedName = pool.getPrimitiveConstant(thisClassIdx).getString();
    PrimitiveConstant superClass = pool.getPrimitiveConstant(superClassIdx);

    int length = in.readUnsignedShort();
    int[] interfaces = new int[length];
    String[] interfaceNames = new String[length];
    for (int i = 0; i < length; i++) {
      interfaces[i] = in.readUnsignedShort();
      interfaceNames[i] = pool.getPrimitiveConstant(interfaces[i]).getString();
    }

    length = in.readUnsignedShort();
    VBStyleCollection<StructField, String>fields = new VBStyleCollection<>(length);
    for (int i = 0; i < length; i++) {
      StructField field = StructField.create(in, pool, qualifiedName);
      fields.addWithKey(field, InterpreterUtil.makeUniqueKey(field.getName(), field.getDescriptor()));
    }

    length = in.readUnsignedShort();
    VBStyleCollection<StructMethod, String>methods = new VBStyleCollection<>(length);
    for (int i = 0; i < length; i++) {
      StructMethod method = StructMethod.create(in, pool, qualifiedName, bytecodeVersion, own);
      methods.addWithKey(method, InterpreterUtil.makeUniqueKey(method.getName(), method.getDescriptor()));
    }

    Map<String, StructGeneralAttribute> attributes = readAttributes(in, pool);

    StructClass cl = new StructClass(
      accessFlags, attributes, qualifiedName, superClass, own, loader, minorVersion, majorVersion, interfaces, interfaceNames, fields, methods);
    if (loader == null) cl.pool = pool;
    return cl;
  }

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

  private StructClass(int accessFlags,
                      Map<String, StructGeneralAttribute> attributes,
                      String qualifiedName,
                      PrimitiveConstant superClass,
                      boolean own,
                      LazyLoader loader,
                      int minorVersion,
                      int majorVersion,
                      int[] interfaces,
                      String[] interfaceNames,
                      VBStyleCollection<StructField, String> fields,
                      VBStyleCollection<StructMethod, String> methods) {
    super(accessFlags, attributes);
    this.qualifiedName = qualifiedName;
    this.superClass = superClass;
    this.own = own;
    this.loader = loader;
    this.minorVersion = minorVersion;
    this.majorVersion = majorVersion;
    this.interfaces = interfaces;
    this.interfaceNames = interfaceNames;
    this.fields = fields;
    this.methods = methods;
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

  /**
   * @return list of record components; null if this class is not a record
   */
  public List<StructRecordComponent> getRecordComponents() {
    StructRecordAttribute recordAttr = getAttribute(StructGeneralAttribute.ATTRIBUTE_RECORD);
    if (recordAttr == null) return null;
    return recordAttr.getComponents();
  }

  public List<String> getPermittedSubclasses() {
    StructPermittedSubclassesAttribute permittedSubClassAttr = getAttribute(StructGeneralAttribute.ATTRIBUTE_PERMITTED_SUBCLASSES);
    if (permittedSubClassAttr == null) return null;
    return permittedSubClassAttr.getClasses();
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

  public boolean isVersion5() {
    return majorVersion > CodeConstants.BYTECODE_JAVA_LE_4 ||
           majorVersion == CodeConstants.BYTECODE_JAVA_LE_4 && minorVersion > 0; // FIXME: check second condition
  }

  public boolean isVersion7() {
    return majorVersion >= CodeConstants.BYTECODE_JAVA_7;
  }

  public boolean isVersion8() {
    return majorVersion >= CodeConstants.BYTECODE_JAVA_8;
  }

  public boolean isVersion14() {
    return majorVersion >= CodeConstants.BYTECODE_JAVA_14;
  }

  public boolean isVersion15() {
    return majorVersion >= CodeConstants.BYTECODE_JAVA_15;
  }

  public boolean isVersion16() {
    return majorVersion >= CodeConstants.BYTECODE_JAVA_16;
  }

  public boolean isVersion17() {
    return majorVersion >= CodeConstants.BYTECODE_JAVA_17;
  }

  public boolean isVersion21() {
    return majorVersion >= CodeConstants.BYTECODE_JAVA_21;
  }

  public boolean isPreviewVersion() {
    return minorVersion == 0xFFFF;
  }

  public boolean hasSealedClassesSupport() {
    return isVersion17() || isVersion15() && isPreviewVersion();
  }

  public boolean hasPatternsInInstanceofSupport() {
    return isVersion16() || isVersion14() && isPreviewVersion();
  }

  public boolean hasEnhancedSwitchSupport() {
    return isVersion14();
  }

  public boolean hasRecordPatternSupport() {
    return isVersion21();
  }

  @Override
  public String toString() {
    return qualifiedName;
  }

  @Override
  protected Type getType() {
    return null;
  }
}
