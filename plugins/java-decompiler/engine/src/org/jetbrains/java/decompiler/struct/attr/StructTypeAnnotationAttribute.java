// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.modules.decompiler.exps.AnnotationExprent;
import org.jetbrains.java.decompiler.modules.decompiler.typeann.*;
import org.jetbrains.java.decompiler.struct.StructTypePath;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StructTypeAnnotationAttribute extends StructGeneralAttribute {
  private List<TypeAnnotation> annotations = Collections.emptyList();

  @Override
  public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException {
    int len = data.readUnsignedShort();
    if (len > 0) {
      annotations = new ArrayList<>(len);
      for (int i = 0; i < len; i++) {
        annotations.add(parse(data, pool));
      }
    }
    else {
      annotations = Collections.emptyList();
    }
  }

  private static TypeAnnotation parse(DataInputStream data, ConstantPool pool) throws IOException {
    int targetType = data.readUnsignedByte();
    TargetInfo targetInfo;

    switch (targetType) {
      case TypeAnnotation.CLASS_TYPE_PARAMETER:
      case TypeAnnotation.METHOD_TYPE_PARAMETER:
        targetInfo = new TypeParameterTarget(data.readUnsignedByte());
        break;
      case TypeAnnotation.SUPER_TYPE_REFERENCE:
        targetInfo = new SupertypeTarget(data.readUnsignedShort());
        break;
      case TypeAnnotation.CLASS_TYPE_PARAMETER_BOUND:
      case TypeAnnotation.METHOD_TYPE_PARAMETER_BOUND:
        targetInfo = new TypeParameterBoundTarget(data.readUnsignedByte(), data.readUnsignedByte());
        break;
      case TypeAnnotation.FIELD:
      case TypeAnnotation.METHOD_RETURN_TYPE:
      case TypeAnnotation.METHOD_RECEIVER:
        targetInfo = new EmptyTarget();
        break;
      case TypeAnnotation.METHOD_PARAMETER:
        targetInfo = new FormalParameterTarget(data.readUnsignedByte());
        break;
      case TypeAnnotation.THROWS_REFERENCE:
        targetInfo = new ThrowsTarget(data.readUnsignedShort());
        break;
      case TypeAnnotation.LOCAL_VARIABLE:
      case TypeAnnotation.RESOURCE_VARIABLE:
        int tableLength = data.readUnsignedShort();
        LocalvarTarget.Offsets[] offsets = new LocalvarTarget.Offsets[tableLength];
        for (int i = 0; i < tableLength; i++) {
          offsets[i] = new LocalvarTarget.Offsets(data.readUnsignedShort(), data.readUnsignedShort(), data.readUnsignedShort());
        }
        targetInfo = new LocalvarTarget(offsets);
        break;

      case TypeAnnotation.CATCH_CLAUSE:
        targetInfo = new CatchTarget(data.readUnsignedShort());
        break;
      case TypeAnnotation.EXPR_INSTANCEOF:
      case TypeAnnotation.EXPR_NEW:
      case TypeAnnotation.EXPR_CONSTRUCTOR_REF:
      case TypeAnnotation.EXPR_METHOD_REF:
        targetInfo = new OffsetTarget(data.readUnsignedShort());
        break;

      case TypeAnnotation.TYPE_ARG_CAST:
      case TypeAnnotation.TYPE_ARG_CONSTRUCTOR_CALL:
      case TypeAnnotation.TYPE_ARG_METHOD_CALL:
      case TypeAnnotation.TYPE_ARG_CONSTRUCTOR_REF:
      case TypeAnnotation.TYPE_ARG_METHOD_REF:
        targetInfo = new TypeArgumentTarget(data.readUnsignedShort(), data.readUnsignedByte());
        break;
      default:
        throw new RuntimeException("unknown target type: " + targetType);
    }

    int pathLength = data.readUnsignedByte();
    List<StructTypePath> paths = new ArrayList<>(pathLength);
    for (int i = 0; i < pathLength; i++) {
      paths.add(i, new StructTypePath(data.readUnsignedByte(), data.readUnsignedByte()));
    }

    AnnotationExprent annotation = StructAnnotationAttribute.parseAnnotation(data, pool);

    return new TypeAnnotation(targetType, targetInfo, paths, annotation);
  }

  public List<TypeAnnotation> getAnnotations() {
    return annotations;
  }
}