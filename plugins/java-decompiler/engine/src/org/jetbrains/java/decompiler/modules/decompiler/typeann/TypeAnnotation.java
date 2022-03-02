// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.decompiler.typeann;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.modules.decompiler.exps.AnnotationExprent;
import org.jetbrains.java.decompiler.struct.StructMember;
import org.jetbrains.java.decompiler.struct.StructTypePathEntry;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructTypeAnnotationAttribute;
import org.jetbrains.java.decompiler.struct.gen.Type;
import org.jetbrains.java.decompiler.util.TextBuffer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
  private final @NotNull List<StructTypePathEntry> paths;
  private final @NotNull AnnotationExprent annotation;

  public TypeAnnotation(
    int targetType,
    TargetInfo targetInfo,
    @NotNull List<StructTypePathEntry> paths,
    @NotNull AnnotationExprent annotation
  ) {
    this.targetType = targetType;
    this.targetInfo = targetInfo;
    this.paths = paths;
    this.annotation = annotation;
  }

  @MagicConstant(valuesFromClass = TypeAnnotation.class)
  public int getTargetType() {
    return targetType;
  }

  public TargetInfo getTargetInfo() {
    return targetInfo;
  }

  public @NotNull AnnotationExprent getAnnotationExpr() {
    return annotation;
  }

  public @NotNull List<StructTypePathEntry> getPaths() {
    return paths;
  }

  @Override
  public String toString() {
    return getAnnotationExpr().toJava(0, BytecodeMappingTracer.DUMMY).toString();
  }

  public void writeTo(@NotNull StringBuilder sb) {
    sb.append(this);
    sb.append(' ');
  }

  public void writeTo(@NotNull TextBuffer sb) {
    sb.append(toString());
    sb.append(' ');
  }

  public static List<TypeAnnotation> listFrom(StructMember md) {
    return Arrays.stream(StructGeneralAttribute.TYPE_ANNOTATION_ATTRIBUTES)
      .flatMap(attrKey -> {
        StructTypeAnnotationAttribute attribute = (StructTypeAnnotationAttribute)md.getAttribute(attrKey);
        if (attribute == null) {
          return Stream.empty();
        } else {
          return attribute.getAnnotations().stream();
        }
      })
      .collect(Collectors.toList());
  }

  public boolean isWrittenBeforeType(Type type) {
    StructTypePathEntry pathEntry = getPaths().stream().findFirst().orElse(null);
    if (pathEntry == null && type.getArrayDim() == 0) {
      return type.isAnnotatable();
    }
    if (pathEntry != null
        && pathEntry.getTypePathEntryKind() == StructTypePathEntry.Kind.ARRAY.getId()
        && getPaths().size() == type.getArrayDim()
    ) return true;
    return false;
  }
}