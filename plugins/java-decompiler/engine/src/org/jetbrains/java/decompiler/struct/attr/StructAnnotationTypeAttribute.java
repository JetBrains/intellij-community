/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StructAnnotationTypeAttribute extends StructGeneralAttribute {

  private static final int ANNOTATION_TARGET_TYPE_GENERIC_CLASS = 0x00;
  private static final int ANNOTATION_TARGET_TYPE_GENERIC_METHOD = 0x01;
  private static final int ANNOTATION_TARGET_TYPE_EXTENDS_IMPLEMENTS = 0x10;
  private static final int ANNOTATION_TARGET_TYPE_GENERIC_CLASS_BOUND = 0x11;
  private static final int ANNOTATION_TARGET_TYPE_GENERIC_METHOD_BOUND = 0x12;
  private static final int ANNOTATION_TARGET_TYPE_FIELD = 0x13;
  private static final int ANNOTATION_TARGET_TYPE_RETURN = 0x14;
  private static final int ANNOTATION_TARGET_TYPE_RECEIVER = 0x15;
  private static final int ANNOTATION_TARGET_TYPE_FORMAL = 0x16;
  private static final int ANNOTATION_TARGET_TYPE_THROWS = 0x17;
  private static final int ANNOTATION_TARGET_TYPE_LOCAL_VARIABLE = 0x40;
  private static final int ANNOTATION_TARGET_TYPE_RESOURCE_VARIABLE = 0x41;
  private static final int ANNOTATION_TARGET_TYPE_EXCEPTION = 0x42;
  private static final int ANNOTATION_TARGET_TYPE_INSTANCEOF = 0x43;
  private static final int ANNOTATION_TARGET_TYPE_NEW = 0x44;
  private static final int ANNOTATION_TARGET_TYPE_DOUBLE_COLON_NEW = 0x45;
  private static final int ANNOTATION_TARGET_TYPE_DOUBLE_COLON_ID = 0x46;
  private static final int ANNOTATION_TARGET_TYPE_CAST = 0x47;
  private static final int ANNOTATION_TARGET_TYPE_INVOCATION_CONSTRUCTOR = 0x48;
  private static final int ANNOTATION_TARGET_TYPE_INVOCATION_METHOD = 0x49;
  private static final int ANNOTATION_TARGET_TYPE_GENERIC_DOUBLE_COLON_NEW = 0x4A;
  private static final int ANNOTATION_TARGET_TYPE_GENERIC_DOUBLE_COLON_ID = 0x4B;

  private static final int ANNOTATION_TARGET_UNION_TYPE_PARAMETER = 1;
  private static final int ANNOTATION_TARGET_UNION_SUPERTYPE = 2;
  private static final int ANNOTATION_TARGET_UNION_TYPE_PARAMETER_BOUND = 3;
  private static final int ANNOTATION_TARGET_UNION_EMPTY = 4;
  private static final int ANNOTATION_TARGET_UNION_FORMAL_PARAMETER = 5;
  private static final int ANNOTATION_TARGET_UNION_THROWS = 6;
  private static final int ANNOTATION_TARGET_UNION_LOCAL_VAR = 7;
  private static final int ANNOTATION_TARGET_UNION_CATCH = 8;
  private static final int ANNOTATION_TARGET_UNION_OFFSET = 9;
  private static final int ANNOTATION_TARGET_UNION_TYPE_ARGUMENT = 10;

  @SuppressWarnings("FieldCanBeLocal") private List<AnnotationLocation> locations;
  @SuppressWarnings("FieldCanBeLocal") private List<AnnotationExprent> annotations;

  @Override
  public void initContent(ConstantPool pool) throws IOException {
    DataInputStream data = stream();

    int len = data.readUnsignedByte();
    if (len > 0) {
      locations = new ArrayList<AnnotationLocation>(len);
      annotations = new ArrayList<AnnotationExprent>(len);
      for (int i = 0; i < len; i++) {
        locations.add(parseAnnotationLocation(data));
        annotations.add(StructAnnotationAttribute.parseAnnotation(data, pool));
      }
    }
    else {
      locations = Collections.emptyList();
      annotations = Collections.emptyList();
    }
  }

  private static AnnotationLocation parseAnnotationLocation(DataInputStream data) throws IOException {
    AnnotationLocation ann_location = new AnnotationLocation();

    // target type
    ann_location.target_type = data.readUnsignedByte();

    // target union
    switch (ann_location.target_type) {
      case ANNOTATION_TARGET_TYPE_GENERIC_CLASS:
      case ANNOTATION_TARGET_TYPE_GENERIC_METHOD:
        ann_location.target_union = ANNOTATION_TARGET_UNION_TYPE_PARAMETER;
        break;
      case ANNOTATION_TARGET_TYPE_EXTENDS_IMPLEMENTS:
        ann_location.target_union = ANNOTATION_TARGET_UNION_SUPERTYPE;
        break;
      case ANNOTATION_TARGET_TYPE_GENERIC_CLASS_BOUND:
      case ANNOTATION_TARGET_TYPE_GENERIC_METHOD_BOUND:
        ann_location.target_union = ANNOTATION_TARGET_UNION_TYPE_PARAMETER_BOUND;
        break;
      case ANNOTATION_TARGET_TYPE_FIELD:
      case ANNOTATION_TARGET_TYPE_RETURN:
      case ANNOTATION_TARGET_TYPE_RECEIVER:
        ann_location.target_union = ANNOTATION_TARGET_UNION_EMPTY;
        break;
      case ANNOTATION_TARGET_TYPE_FORMAL:
        ann_location.target_union = ANNOTATION_TARGET_UNION_FORMAL_PARAMETER;
        break;
      case ANNOTATION_TARGET_TYPE_THROWS:
        ann_location.target_union = ANNOTATION_TARGET_UNION_THROWS;
        break;
      case ANNOTATION_TARGET_TYPE_LOCAL_VARIABLE:
      case ANNOTATION_TARGET_TYPE_RESOURCE_VARIABLE:
        ann_location.target_union = ANNOTATION_TARGET_UNION_LOCAL_VAR;
        break;
      case ANNOTATION_TARGET_TYPE_EXCEPTION:
        ann_location.target_union = ANNOTATION_TARGET_UNION_CATCH;
        break;
      case ANNOTATION_TARGET_TYPE_INSTANCEOF:
      case ANNOTATION_TARGET_TYPE_NEW:
      case ANNOTATION_TARGET_TYPE_DOUBLE_COLON_NEW:
      case ANNOTATION_TARGET_TYPE_DOUBLE_COLON_ID:
        ann_location.target_union = ANNOTATION_TARGET_UNION_OFFSET;
        break;
      case ANNOTATION_TARGET_TYPE_CAST:
      case ANNOTATION_TARGET_TYPE_INVOCATION_CONSTRUCTOR:
      case ANNOTATION_TARGET_TYPE_INVOCATION_METHOD:
      case ANNOTATION_TARGET_TYPE_GENERIC_DOUBLE_COLON_NEW:
      case ANNOTATION_TARGET_TYPE_GENERIC_DOUBLE_COLON_ID:
        ann_location.target_union = ANNOTATION_TARGET_UNION_TYPE_ARGUMENT;
        break;
      default:
        throw new RuntimeException("Unknown target type in a type annotation!");
    }

    // target union data

    switch (ann_location.target_union) {
      case ANNOTATION_TARGET_UNION_TYPE_PARAMETER:
      case ANNOTATION_TARGET_UNION_FORMAL_PARAMETER:
        ann_location.data = new int[]{data.readUnsignedByte()};
        break;
      case ANNOTATION_TARGET_UNION_SUPERTYPE:
      case ANNOTATION_TARGET_UNION_THROWS:
      case ANNOTATION_TARGET_UNION_CATCH:
      case ANNOTATION_TARGET_UNION_OFFSET:
        ann_location.data = new int[]{data.readUnsignedShort()};
        break;
      case ANNOTATION_TARGET_UNION_TYPE_PARAMETER_BOUND:
        ann_location.data = new int[]{data.readUnsignedByte(), data.readUnsignedByte()};
        break;
      case ANNOTATION_TARGET_UNION_EMPTY:
        break;
      case ANNOTATION_TARGET_UNION_LOCAL_VAR:
        int table_length = data.readUnsignedShort();

        ann_location.data = new int[table_length * 3 + 1];
        ann_location.data[0] = table_length;

        for (int i = 0; i < table_length; ++i) {
          ann_location.data[3 * i + 1] = data.readUnsignedShort();
          ann_location.data[3 * i + 2] = data.readUnsignedShort();
          ann_location.data[3 * i + 3] = data.readUnsignedShort();
        }
        break;
      case ANNOTATION_TARGET_UNION_TYPE_ARGUMENT:
        ann_location.data = new int[]{data.readUnsignedShort(), data.readUnsignedByte()};
    }

    // target path
    int path_length = data.readUnsignedByte();

    ann_location.target_path_kind = new int[path_length];
    ann_location.target_argument_index = new int[path_length];

    for (int i = 0; i < path_length; ++i) {
      ann_location.target_path_kind[i] = data.readUnsignedByte();
      ann_location.target_argument_index[i] = data.readUnsignedByte();
    }

    return ann_location;
  }

  private static class AnnotationLocation {
    public int target_type;
    public int target_union;
    public int[] data;
    public int[] target_path_kind;
    public int[] target_argument_index;
  }
}
