// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

public final class UnorderedPair<T> {
  public final T first;
  public final T second;

  public UnorderedPair(T first, T second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public int hashCode() {
    int hc1 = first == null ? 0 : first.hashCode();
    int hc2 = second == null ? 0 : second.hashCode();
    return hc1 * hc1 + hc2 * hc2;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) return false;
    if (obj.getClass() != getClass()) return false;

    final UnorderedPair other = (UnorderedPair)obj;
    if (Comparing.equal(other.first, first) && Comparing.equal(other.second, second)) return true;
    if (Comparing.equal(other.first, second) && Comparing.equal(other.second, first)) return true;
    return false;
  }

  @Override
  public String toString() {
    return "<" + first + ", " + second + '>';
  }
}
