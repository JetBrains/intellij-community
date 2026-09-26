// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

public final class EnumDataDescriptor<T extends Enum> extends InlineKeyDescriptor<T> {
  private final Class<T> myEnumClass;

  public EnumDataDescriptor(Class<T> enumClass) {
    myEnumClass = enumClass;
  }

  @Override
  public T fromInt(int n) {
    return myEnumClass.getEnumConstants()[n];
  }

  @Override
  public int toInt(T t) {
    return t.ordinal();
  }
}
