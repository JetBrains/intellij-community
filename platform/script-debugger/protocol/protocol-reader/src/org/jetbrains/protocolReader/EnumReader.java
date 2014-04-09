// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.protocolReader;

class EnumReader<T extends Enum<T>> extends ValueReader {
  public static <T extends Enum<T>> EnumReader<T> create(Class<T> enumTypeClass, boolean isNullable) {
    return new EnumReader<>(enumTypeClass, isNullable);
  }

  private final Class<T> enumClass;

  private EnumReader(Class<T> enumClass, boolean nullable) {
    super(nullable);
    this.enumClass = enumClass;
  }

  @Override
  public void appendFinishedValueTypeName(TextOutput out) {
    out.append(enumClass.getCanonicalName());
  }

  @Override
  void writeReadCode(ClassScope scope, boolean subtyping, String fieldName, TextOutput out) {
    beginReadCall("Enum", subtyping, out, fieldName);
    out.comma().append(enumClass.getCanonicalName()).append(".class").append(')');
  }

  @Override
  void writeArrayReadCode(ClassScope scope,
                          boolean subtyping,
                          boolean nullable,
                          String fieldName, TextOutput out) {
    throw new UnsupportedOperationException();
  }
}
