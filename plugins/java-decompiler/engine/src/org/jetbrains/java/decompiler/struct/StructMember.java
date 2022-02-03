// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.modules.decompiler.exps.AnnotationExprent;
import org.jetbrains.java.decompiler.modules.decompiler.typeann.TargetInfo;
import org.jetbrains.java.decompiler.modules.decompiler.typeann.TypeAnnotation;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTableAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTypeTableAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructTypeAnnotationAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class StructMember {
  private final int accessFlags;
  private final Map<String, StructGeneralAttribute> attributes;

  protected StructMember(int accessFlags, Map<String, StructGeneralAttribute> attributes) {
    this.accessFlags = accessFlags;
    this.attributes = attributes;
  }

  public int getAccessFlags() {
    return accessFlags;
  }

  public <T extends StructGeneralAttribute> T getAttribute(StructGeneralAttribute.Key<T> attribute) {
    @SuppressWarnings("unchecked") T t = (T)attributes.get(attribute.name);
    return t;
  }

  public boolean hasAttribute(StructGeneralAttribute.Key<?> attribute) {
    return attributes.containsKey(attribute.name);
  }

  public boolean hasModifier(int modifier) {
    return (accessFlags & modifier) == modifier;
  }

  public boolean isSynthetic() {
    return hasModifier(CodeConstants.ACC_SYNTHETIC) || hasAttribute(StructGeneralAttribute.ATTRIBUTE_SYNTHETIC);
  }

  protected abstract int getArrayDimensions();

  public boolean memberAnnCollidesWithTypeAnnotation(AnnotationExprent typeAnnotationExpr) {
    Set<AnnotationExprent> typeAnnotations = TargetInfo.EmptyTarget.extract(getPossibleTypeAnnotationCollisions(getArrayDimensions()))
      .stream()
      .map(typeAnnotation-> typeAnnotation.getAnnotationExpr())
      .collect(Collectors.toUnmodifiableSet());
    return typeAnnotations.contains(typeAnnotationExpr);
  }

  public boolean paramAnnCollidesWithTypeAnnotation(AnnotationExprent typeAnnotationExpr, int arrayDim, int param) {
    Set<AnnotationExprent> typeAnnotations = TargetInfo.FormalParameterTarget
      .extract(getPossibleTypeAnnotationCollisions(arrayDim), param).stream()
      .map(typeAnnotation-> typeAnnotation.getAnnotationExpr())
      .collect(Collectors.toUnmodifiableSet());
    return typeAnnotations.contains(typeAnnotationExpr);
  }

  private List<TypeAnnotation> getPossibleTypeAnnotationCollisions(int arrayDim) {
    return Arrays.stream(StructGeneralAttribute.TYPE_ANNOTATION_ATTRIBUTES)
      .flatMap(attrKey -> {
        StructTypeAnnotationAttribute attribute = (StructTypeAnnotationAttribute)getAttribute(attrKey);
        if (attribute == null) {
          return Stream.empty();
        } else {
          return attribute.getAnnotations().stream();
        }
      })
      .filter(ta -> ta.isForDeepestArrayComponent(arrayDim))
      .collect(Collectors.toList());
  }

  public static Map<String, StructGeneralAttribute> readAttributes(DataInputFullStream in, ConstantPool pool) throws IOException {
    int length = in.readUnsignedShort();
    Map<String, StructGeneralAttribute> attributes = new HashMap<>(length);

    for (int i = 0; i < length; i++) {
      int nameIndex = in.readUnsignedShort();
      String name = pool.getPrimitiveConstant(nameIndex).getString();

      StructGeneralAttribute attribute = StructGeneralAttribute.createAttribute(name);
      int attLength = in.readInt();
      if (attribute == null) {
        in.discard(attLength);
      }
      else {
        attribute.initContent(in, pool);
        if (StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TABLE.name.equals(name) && attributes.containsKey(name)) {
          // merge all variable tables
          StructLocalVariableTableAttribute table = (StructLocalVariableTableAttribute)attributes.get(name);
          table.add((StructLocalVariableTableAttribute)attribute);
        }
        else if (StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TYPE_TABLE.name.equals(name) && attributes.containsKey(name)) {
          // merge all variable tables
          StructLocalVariableTypeTableAttribute table = (StructLocalVariableTypeTableAttribute)attributes.get(name);
          table.add((StructLocalVariableTypeTableAttribute)attribute);
        }
        else {
          attributes.put(name, attribute);
        }
      }
    }

    return attributes;
  }
}
