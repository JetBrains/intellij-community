// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.utils;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;

@ApiStatus.Internal
public class UnsignedBitSet implements Cloneable {
  private final @NotNull BitSet myPositiveSet;
  private final @NotNull BitSet myNegativeSet;

  public UnsignedBitSet(@NotNull BitSet positiveSet, @NotNull BitSet negativeSet) {
    myPositiveSet = positiveSet;
    myNegativeSet = negativeSet;
  }

  public UnsignedBitSet() {
    this(new BitSet(), new BitSet());
  }

  private static int toPositive(int bitIndex) {
    return -bitIndex;
  }

  public void set(int bitIndex, boolean value) {
    if (bitIndex >= 0) {
      myPositiveSet.set(bitIndex, value);
    }
    else {
      myNegativeSet.set(toPositive(bitIndex), value);
    }
  }

  // boundaries included
  public void set(int fromIndex, int toIndex, boolean value) {
    if (fromIndex > toIndex) {
      throw new IndexOutOfBoundsException("fromIndex: " + fromIndex +
                                          " > toIndex: " + toIndex);
    }
    if (fromIndex >= 0) {
      myPositiveSet.set(fromIndex, toIndex + 1, value);
    }
    else if (toIndex < 0) {
      myNegativeSet.set(toPositive(toIndex), toPositive(fromIndex) + 1, value);
    }
    else {
      myPositiveSet.set(0, toIndex + 1, value);
      myNegativeSet.set(toPositive(-1), toPositive(fromIndex) + 1, value);
    }
  }

  public boolean get(int bitIndex) {
    if (bitIndex >= 0) {
      return myPositiveSet.get(bitIndex);
    }
    else {
      return myNegativeSet.get(toPositive(bitIndex));
    }
  }

  @Override
  public UnsignedBitSet clone() {
    return new UnsignedBitSet((BitSet)myPositiveSet.clone(), (BitSet)myNegativeSet.clone());
  }
}
