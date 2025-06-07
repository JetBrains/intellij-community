// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.vcs.log.graph.utils.Flags;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Objects;

public class BitSetFlags implements Flags {

  private final int mySize;

  private final @NotNull BitSet myBitSet;

  // default value is false
  public BitSetFlags(int size) {
    if (size < 0) throw new NegativeArraySizeException("size < 0: " + size);
    mySize = size;
    myBitSet = new BitSet();
  }

  public BitSetFlags(int size, boolean defaultValue) {
    this(size);
    if (defaultValue) setAll(true);
  }

  @Override
  public int size() {
    return mySize;
  }

  @Override
  public boolean get(int index) {
    checkRange(index);
    return myBitSet.get(index);
  }

  @Override
  public void set(int index, boolean value) {
    checkRange(index);
    myBitSet.set(index, value);
  }

  @Override
  public void setAll(boolean value) {
    myBitSet.set(0, mySize, value);
  }

  private void checkRange(int index) {
    if (index < 0) throw new IndexOutOfBoundsException("index is " + index + " which is less than zero");
    if (index >= mySize) throw new IndexOutOfBoundsException("index is " + index + " and set size is " + mySize);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BitSetFlags flags = (BitSetFlags)o;
    return mySize == flags.mySize &&
           myBitSet.equals(flags.myBitSet);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mySize, myBitSet);
  }

  @Override
  public String toString() {
    return myBitSet + ", size = " + mySize; // NON-NLS
  }
}
