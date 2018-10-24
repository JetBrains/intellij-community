// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;

/*
  attribute_info {
    u2 attribute_name_index;
    u4 attribute_length;
    u1 info[attribute_length];
  }
*/
public class StructGeneralAttribute {
  public static final Key<StructGeneralAttribute> ATTRIBUTE_CODE = new Key<>("Code");
  public static final Key<StructInnerClassesAttribute> ATTRIBUTE_INNER_CLASSES = new Key<>("InnerClasses");
  public static final Key<StructGenericSignatureAttribute> ATTRIBUTE_SIGNATURE = new Key<>("Signature");
  public static final Key<StructAnnDefaultAttribute> ATTRIBUTE_ANNOTATION_DEFAULT = new Key<>("AnnotationDefault");
  public static final Key<StructExceptionsAttribute> ATTRIBUTE_EXCEPTIONS = new Key<>("Exceptions");
  public static final Key<StructEnclosingMethodAttribute> ATTRIBUTE_ENCLOSING_METHOD = new Key<>("EnclosingMethod");
  public static final Key<StructAnnotationAttribute> ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS = new Key<>("RuntimeVisibleAnnotations");
  public static final Key<StructAnnotationAttribute> ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS = new Key<>("RuntimeInvisibleAnnotations");
  public static final Key<StructAnnotationParameterAttribute> ATTRIBUTE_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS = new Key<>("RuntimeVisibleParameterAnnotations");
  public static final Key<StructAnnotationParameterAttribute> ATTRIBUTE_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS = new Key<>("RuntimeInvisibleParameterAnnotations");
  public static final Key<StructTypeAnnotationAttribute> ATTRIBUTE_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = new Key<>("RuntimeVisibleTypeAnnotations");
  public static final Key<StructTypeAnnotationAttribute> ATTRIBUTE_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS = new Key<>("RuntimeInvisibleTypeAnnotations");
  public static final Key<StructLocalVariableTableAttribute> ATTRIBUTE_LOCAL_VARIABLE_TABLE = new Key<>("LocalVariableTable");
  public static final Key<StructLocalVariableTypeTableAttribute> ATTRIBUTE_LOCAL_VARIABLE_TYPE_TABLE = new Key<>("LocalVariableTypeTable");
  public static final Key<StructConstantValueAttribute> ATTRIBUTE_CONSTANT_VALUE = new Key<>("ConstantValue");
  public static final Key<StructBootstrapMethodsAttribute> ATTRIBUTE_BOOTSTRAP_METHODS = new Key<>("BootstrapMethods");
  public static final Key<StructGeneralAttribute> ATTRIBUTE_SYNTHETIC = new Key<>("Synthetic");
  public static final Key<StructGeneralAttribute> ATTRIBUTE_DEPRECATED = new Key<>("Deprecated");
  public static final Key<StructLineNumberTableAttribute> ATTRIBUTE_LINE_NUMBER_TABLE = new Key<>("LineNumberTable");
  public static final Key<StructMethodParametersAttribute> ATTRIBUTE_METHOD_PARAMETERS = new Key<>("MethodParameters");

  public static class Key<T extends StructGeneralAttribute> {
    private final String name;

    public Key(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  private String name;

  public static StructGeneralAttribute createAttribute(String name) {
    StructGeneralAttribute attr;

    if (ATTRIBUTE_INNER_CLASSES.getName().equals(name)) {
      attr = new StructInnerClassesAttribute();
    }
    else if (ATTRIBUTE_CONSTANT_VALUE.getName().equals(name)) {
      attr = new StructConstantValueAttribute();
    }
    else if (ATTRIBUTE_SIGNATURE.getName().equals(name)) {
      attr = new StructGenericSignatureAttribute();
    }
    else if (ATTRIBUTE_ANNOTATION_DEFAULT.getName().equals(name)) {
      attr = new StructAnnDefaultAttribute();
    }
    else if (ATTRIBUTE_EXCEPTIONS.getName().equals(name)) {
      attr = new StructExceptionsAttribute();
    }
    else if (ATTRIBUTE_ENCLOSING_METHOD.getName().equals(name)) {
      attr = new StructEnclosingMethodAttribute();
    }
    else if (ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS.getName().equals(name) || ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS.getName().equals(name)) {
      attr = new StructAnnotationAttribute();
    }
    else if (ATTRIBUTE_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS.getName().equals(name) || ATTRIBUTE_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS.getName().equals(name)) {
      attr = new StructAnnotationParameterAttribute();
    }
    else if (ATTRIBUTE_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.getName().equals(name) || ATTRIBUTE_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS.getName().equals(name)) {
      attr = new StructTypeAnnotationAttribute();
    }
    else if (ATTRIBUTE_LOCAL_VARIABLE_TABLE.getName().equals(name)) {
      attr = new StructLocalVariableTableAttribute();
    }
    else if (ATTRIBUTE_LOCAL_VARIABLE_TYPE_TABLE.getName().equals(name)) {
      attr = new StructLocalVariableTypeTableAttribute();
    }
    else if (ATTRIBUTE_BOOTSTRAP_METHODS.getName().equals(name)) {
      attr = new StructBootstrapMethodsAttribute();
    }
    else if (ATTRIBUTE_SYNTHETIC.getName().equals(name) || ATTRIBUTE_DEPRECATED.getName().equals(name)) {
      attr = new StructGeneralAttribute();
    }
    else if (ATTRIBUTE_LINE_NUMBER_TABLE.getName().equals(name)) {
      attr = new StructLineNumberTableAttribute();
    }
    else if (ATTRIBUTE_METHOD_PARAMETERS.getName().equals(name)) {
      attr = new StructMethodParametersAttribute();
    }
    else {
      // unsupported attribute
      return null;
    }

    attr.name = name;
    return attr;
  }

  public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException { }

  public String getName() {
    return name;
  }
}