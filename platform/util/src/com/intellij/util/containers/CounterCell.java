// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.ApiStatus;

/**
 * A padded cell for distributing counts.  Adapted from LongAdder
 * and Striped64.  See their internal docs for explanation.
 */
@ApiStatus.Internal
public final class CounterCell {
  public static final VarHandleWrapper CELLVALUE;
  // Padding fields to avoid contention
  @SuppressWarnings("unused") volatile long p0, p1, p2, p3, p4, p5, p6;
  public volatile long value;
  // Padding fields to avoid contention
  @SuppressWarnings("unused") volatile long q0, q1, q2, q3, q4, q5, q6;

  public CounterCell(long x) { value = x; }

  static {
    CELLVALUE = VarHandleWrapper.getFactory().create(CounterCell.class, "value", long.class);
  }
}


