// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * @deprecated use {@link Int2IntOpenHashMap} instead
 */
@Deprecated
public final class IntIntHashMap extends Int2IntOpenHashMap {
  private static final int DEFAULT_NULL_VALUE = -1;

  public IntIntHashMap(int initialCapacity, int null_value) {
    super(initialCapacity);
    defaultReturnValue(null_value);
  }

  public IntIntHashMap(int initialCapacity) {
    this(initialCapacity, DEFAULT_NULL_VALUE);
  }

  public IntIntHashMap() {
    defaultReturnValue(DEFAULT_NULL_VALUE);
  }
}