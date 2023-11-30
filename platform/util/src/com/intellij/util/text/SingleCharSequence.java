// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

public final class SingleCharSequence implements CharSequence {
  private final char myCh;

  public SingleCharSequence(char ch) {
    myCh = ch;
  }

  @Override
  public int length() {
    return 1;
  }

  @Override
  public char charAt(int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException("Index out of bounds: " + index);
    }
    return myCh;
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    if (start == 0 && end == 1) return this;
    if (start < 0) throw new IndexOutOfBoundsException("Start index out of range:" + start);
    if (end > 1 || end < 0) throw new IndexOutOfBoundsException("End index out of range:" + end);
    if (start > end) throw new IndexOutOfBoundsException("Start index should be less or equal to end index:" + start + " - " + end);
    return "";
  }

  @Override
  public @NotNull String toString() {
    return String.valueOf(myCh);
  }
}
