// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.foundation;

import java.lang.foreign.MemorySegment;

public final class Selector {

  private String myName;
  private final long value;

  public Selector() {
    this("undefined selector", 0);
  }

  public Selector(String name, long value) {
    this.value = value;
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public long longValue() {
    return value;
  }

  public MemorySegment asMemorySegment() {
    return MemorySegment.ofAddress(value);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Selector selector && value == selector.value;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(value);
  }

  @Override
  public String toString() {
    return String.format("[Selector %s]", myName);
  }

  public Selector initName(final String name) {
    myName = name;
    return this;
  }
}
