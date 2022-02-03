// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

public class Range {
  public final int start1;
  public final int end1;
  public final int start2;
  public final int end2;

  public Range(int start1, int end1, int start2, int end2) {
    assert start1 <= end1 && start2 <= end2;
    this.start1 = start1;
    this.end1 = end1;
    this.start2 = start2;
    this.end2 = end2;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Range range = (Range)o;

    if (start1 != range.start1) return false;
    if (end1 != range.end1) return false;
    if (start2 != range.start2) return false;
    if (end2 != range.end2) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = start1;
    result = 31 * result + end1;
    result = 31 * result + start2;
    result = 31 * result + end2;
    return result;
  }

  @Override
  public String toString() {
    return "[" + start1 + ", " + end1 + ") - [" + start2 + ", " + end2 + ")";
  }

  public boolean isEmpty() {
    return start1 == end1 && start2 == end2;
  }
}
