// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

public final class MergingCharSequence implements CharSequence {
  private final CharSequence s1;
  private final CharSequence s2;

  public MergingCharSequence(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    this.s1 = s1;
    this.s2 = s2;
  }

  @Override
  public int length() {
    return s1.length() + s2.length();
  }

  @Override
  public char charAt(int index) {
    if (index < s1.length()) return s1.charAt(index);
    return s2.charAt(index - s1.length());
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    if (start == 0 && end == length()) return this;
    if (start < s1.length() && end < s1.length()) return s1.subSequence(start, end);
    if (start >= s1.length() && end >= s1.length()) return s2.subSequence(start - s1.length(), end - s1.length());
    return new MergingCharSequence(s1.subSequence(start, s1.length()), s2.subSequence(0, end - s1.length()));
  }

  @Override
  public @NotNull String toString() {
    return s1 + s2.toString();
  }
}
