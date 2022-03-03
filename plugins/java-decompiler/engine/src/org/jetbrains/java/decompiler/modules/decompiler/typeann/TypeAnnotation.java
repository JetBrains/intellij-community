// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler.typeann;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.modules.decompiler.exps.AnnotationExprent;
import org.jetbrains.java.decompiler.struct.StructTypePath;

import java.lang.annotation.Target;
import java.util.List;

public class TypeAnnotation {
  public static final int CLASS_TYPE_PARAMETER = 0x00;
  public static final int METHOD_TYPE_PARAMETER = 0x01;
  public static final int SUPER_TYPE_REFERENCE = 0x10;
  public static final int CLASS_TYPE_PARAMETER_BOUND = 0x11;
  public static final int METHOD_TYPE_PARAMETER_BOUND = 0x12;
  public static final int FIELD = 0x13;
  public static final int METHOD_RETURN_TYPE = 0x14;
  public static final int METHOD_RECEIVER = 0x15;
  public static final int METHOD_PARAMETER = 0x16;
  public static final int THROWS_REFERENCE = 0x17;
  public static final int LOCAL_VARIABLE = 0x40;
  public static final int RESOURCE_VARIABLE = 0x41;
  public static final int CATCH_CLAUSE = 0x42;
  public static final int EXPR_INSTANCEOF = 0x43;
  public static final int EXPR_NEW = 0x44;
  public static final int EXPR_CONSTRUCTOR_REF = 0x45;
  public static final int EXPR_METHOD_REF = 0x46;
  public static final int TYPE_ARG_CAST = 0x47;
  public static final int TYPE_ARG_CONSTRUCTOR_CALL = 0x48;
  public static final int TYPE_ARG_METHOD_CALL = 0x49;
  public static final int TYPE_ARG_CONSTRUCTOR_REF = 0x4A;
  public static final int TYPE_ARG_METHOD_REF = 0x4B;

  private final int targetType;
  private final TargetInfo targetInfo;
  private final @NotNull List<StructTypePath> paths;
  private final @NotNull AnnotationExprent annotation;

  public TypeAnnotation(int targetType, TargetInfo targetInfo, @NotNull List<StructTypePath> paths, @NotNull AnnotationExprent annotation) {
    this.targetType = targetType;
    this.targetInfo = targetInfo;
    this.paths = paths;
    this.annotation = annotation;
  }

  public int getTargetType() {
    return targetType;
  }

  public TargetInfo getTargetInfo() {
    return targetInfo;
  }

  /**
   * @param dims The amount of dimensions if the annotated type is an array type, 0 otherwise.
   * @return Whether the type annotations is top level.
   */
  public boolean isTopLevel(int dims) {
    return (dims == 0 && paths.isEmpty()) || (dims == paths.size());
  }

  public @NotNull AnnotationExprent getAnnotationExpr() {
    return annotation;
  }

  public @NotNull List<StructTypePath> getPaths() {
    return paths;
  }
}