// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.protocolReader;

class FieldLoader {
  public static final char FIELD_PREFIX = '_';

  private final String fieldName;
  final ValueReader valueReader;

  FieldLoader(String fieldName, ValueReader valueReader) {
    this.fieldName = fieldName;
    this.valueReader = valueReader;
  }

  public String getFieldName() {
    return fieldName;
  }

  public void writeFieldDeclaration(TextOutput out) {
    out.append("private ");
    valueReader.appendFinishedValueTypeName(out);
    out.append(' ').append(FIELD_PREFIX).append(fieldName);
    if (valueReader instanceof PrimitiveValueReader) {
      String defaultValue = ((PrimitiveValueReader)valueReader).defaultValue;
      if (defaultValue != null) {
        out.append(" = ").append(defaultValue);
      }
    }
    out.semi();
  }
}
