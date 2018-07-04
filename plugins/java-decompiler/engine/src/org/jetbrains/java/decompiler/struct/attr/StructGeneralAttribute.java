/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
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
  public static final String ATTRIBUTE_CODE = "Code";
  public static final String ATTRIBUTE_INNER_CLASSES = "InnerClasses";
  public static final String ATTRIBUTE_SIGNATURE = "Signature";
  public static final String ATTRIBUTE_ANNOTATION_DEFAULT = "AnnotationDefault";
  public static final String ATTRIBUTE_EXCEPTIONS = "Exceptions";
  public static final String ATTRIBUTE_ENCLOSING_METHOD = "EnclosingMethod";
  public static final String ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS = "RuntimeVisibleAnnotations";
  public static final String ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS = "RuntimeInvisibleAnnotations";
  public static final String ATTRIBUTE_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS = "RuntimeVisibleParameterAnnotations";
  public static final String ATTRIBUTE_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS = "RuntimeInvisibleParameterAnnotations";
  public static final String ATTRIBUTE_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = "RuntimeVisibleTypeAnnotations";
  public static final String ATTRIBUTE_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS = "RuntimeInvisibleTypeAnnotations";
  public static final String ATTRIBUTE_LOCAL_VARIABLE_TABLE = "LocalVariableTable";
  public static final String ATTRIBUTE_LOCAL_VARIABLE_TYPE_TABLE = "LocalVariableTypeTable";
  public static final String ATTRIBUTE_CONSTANT_VALUE = "ConstantValue";
  public static final String ATTRIBUTE_BOOTSTRAP_METHODS = "BootstrapMethods";
  public static final String ATTRIBUTE_SYNTHETIC = "Synthetic";
  public static final String ATTRIBUTE_DEPRECATED = "Deprecated";
  public static final String ATTRIBUTE_LINE_NUMBER_TABLE = "LineNumberTable";
  public static final String ATTRIBUTE_METHOD_PARAMETERS = "MethodParameters";

  private String name;

  public static StructGeneralAttribute createAttribute(String name) {
    StructGeneralAttribute attr;

    if (ATTRIBUTE_INNER_CLASSES.equals(name)) {
      attr = new StructInnerClassesAttribute();
    }
    else if (ATTRIBUTE_CONSTANT_VALUE.equals(name)) {
      attr = new StructConstantValueAttribute();
    }
    else if (ATTRIBUTE_SIGNATURE.equals(name)) {
      attr = new StructGenericSignatureAttribute();
    }
    else if (ATTRIBUTE_ANNOTATION_DEFAULT.equals(name)) {
      attr = new StructAnnDefaultAttribute();
    }
    else if (ATTRIBUTE_EXCEPTIONS.equals(name)) {
      attr = new StructExceptionsAttribute();
    }
    else if (ATTRIBUTE_ENCLOSING_METHOD.equals(name)) {
      attr = new StructEnclosingMethodAttribute();
    }
    else if (ATTRIBUTE_RUNTIME_VISIBLE_ANNOTATIONS.equals(name) || ATTRIBUTE_RUNTIME_INVISIBLE_ANNOTATIONS.equals(name)) {
      attr = new StructAnnotationAttribute();
    }
    else if (ATTRIBUTE_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS.equals(name) || ATTRIBUTE_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS.equals(name)) {
      attr = new StructAnnotationParameterAttribute();
    }
    else if (ATTRIBUTE_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.equals(name) || ATTRIBUTE_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS.equals(name)) {
      attr = new StructTypeAnnotationAttribute();
    }
    else if (ATTRIBUTE_LOCAL_VARIABLE_TABLE.equals(name)) {
      attr = new StructLocalVariableTableAttribute();
    }
    else if (ATTRIBUTE_LOCAL_VARIABLE_TYPE_TABLE.equals(name)) {
      attr = new StructLocalVariableTypeTableAttribute();
    }
    else if (ATTRIBUTE_BOOTSTRAP_METHODS.equals(name)) {
      attr = new StructBootstrapMethodsAttribute();
    }
    else if (ATTRIBUTE_SYNTHETIC.equals(name) || ATTRIBUTE_DEPRECATED.equals(name)) {
      attr = new StructGeneralAttribute();
    }
    else if (ATTRIBUTE_LINE_NUMBER_TABLE.equals(name)) {
      attr = new StructLineNumberTableAttribute();
    }
    else if (ATTRIBUTE_METHOD_PARAMETERS.equals(name)) {
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