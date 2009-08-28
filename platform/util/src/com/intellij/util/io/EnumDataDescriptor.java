/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author peter
 */
public class EnumDataDescriptor<T extends Enum> implements KeyDescriptor<T> {
  private final Class<T> myEnumClass;

  public EnumDataDescriptor(Class<T> enumClass) {
    myEnumClass = enumClass;
  }

  public int getHashCode(final T value) {
    return value.hashCode();
  }

  public boolean isEqual(final T val1, final T val2) {
    return val1.equals(val2);
  }

  public void save(final DataOutput out, final T value) throws IOException {
    out.writeInt(value.ordinal());
  }

  public T read(final DataInput in) throws IOException {
    return myEnumClass.getEnumConstants()[in.readInt()];
  }
}