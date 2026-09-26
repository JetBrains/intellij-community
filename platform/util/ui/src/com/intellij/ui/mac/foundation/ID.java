// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.foundation;

import java.lang.foreign.MemorySegment;

/**
 * Could be an address in memory (if pointer to a class or method) or a value (like 0 or 1)
 */
public final class ID extends Number {
  private final long value;

  public ID() {
    this(0);
  }

  public ID(long peer) {
    value = peer;
  }

  public static final ID NIL = new ID(0L);

  public MemorySegment asMemorySegment() {
    return MemorySegment.ofAddress(value);
  }

  @Override
  public int intValue() {
    return (int)value;
  }

  @Override
  public long longValue() {
    return value;
  }

  @Override
  public float floatValue() {
    return value;
  }

  @Override
  public double doubleValue() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ID id && value == id.value;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(value);
  }

  @Override
  public String toString() {
    return Long.toString(value);
  }

  public boolean booleanValue() {
    return intValue() != 0;
  }
}
