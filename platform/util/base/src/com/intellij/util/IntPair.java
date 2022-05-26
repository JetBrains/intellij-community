// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

public final class IntPair {
  public final int first;
  public final int second;

  public IntPair(int first, int second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public int hashCode() {
    return 31 * first + second;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IntPair that = (IntPair)o;
    return first == that.first && second == that.second;
  }

  @Override
  public String toString() {
    return "first=" + first + ", second=" + second;
  }
}
