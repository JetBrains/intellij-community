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
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.modules.decompiler.exps.AnnotationExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.TypeAnnotation;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StructTypeAnnotationAttribute extends StructGeneralAttribute {
  private List<TypeAnnotation> annotations = Collections.emptyList();

  @Override
  public void initContent(ConstantPool pool) throws IOException {
    DataInputStream data = stream();

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
    int target = targetType << 24;

    switch (targetType) {
      case TypeAnnotation.CLASS_TYPE_PARAMETER:
      case TypeAnnotation.METHOD_TYPE_PARAMETER:
      case TypeAnnotation.METHOD_PARAMETER:
        target |= data.readUnsignedByte();
        break;

      case TypeAnnotation.SUPER_TYPE_REFERENCE:
      case TypeAnnotation.CLASS_TYPE_PARAMETER_BOUND:
      case TypeAnnotation.METHOD_TYPE_PARAMETER_BOUND:
      case TypeAnnotation.THROWS_REFERENCE:
      case TypeAnnotation.CATCH_CLAUSE:
      case TypeAnnotation.EXPR_INSTANCEOF:
      case TypeAnnotation.EXPR_NEW:
      case TypeAnnotation.EXPR_CONSTRUCTOR_REF:
      case TypeAnnotation.EXPR_METHOD_REF:
        target |= data.readUnsignedShort();
        break;

      case TypeAnnotation.TYPE_ARG_CAST:
      case TypeAnnotation.TYPE_ARG_CONSTRUCTOR_CALL:
      case TypeAnnotation.TYPE_ARG_METHOD_CALL:
      case TypeAnnotation.TYPE_ARG_CONSTRUCTOR_REF:
      case TypeAnnotation.TYPE_ARG_METHOD_REF:
        data.skipBytes(3);
        break;

      case TypeAnnotation.LOCAL_VARIABLE:
      case TypeAnnotation.RESOURCE_VARIABLE:
        data.skipBytes(data.readUnsignedShort() * 6);
        break;

      case TypeAnnotation.FIELD:
      case TypeAnnotation.METHOD_RETURN_TYPE:
      case TypeAnnotation.METHOD_RECEIVER:
        break;

      default:
        throw new RuntimeException("unknown target type: " + targetType);
    }

    int pathLength = data.readUnsignedByte();
    byte[] path = null;
    if (pathLength > 0) {
      path = new byte[2 * pathLength];
      data.readFully(path);
    }

    AnnotationExprent annotation = StructAnnotationAttribute.parseAnnotation(data, pool);

    return new TypeAnnotation(target, path, annotation);
  }

  public List<TypeAnnotation> getAnnotations() {
    return annotations;
  }
}