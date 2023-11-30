// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

public final class ReverseCharSequence implements CharSequence{
  private final CharSequence mySequence;

  public ReverseCharSequence(@NotNull CharSequence sequence) {
    mySequence = sequence;
  }

  @Override
  public int length() {
    return mySequence.length();
  }

  @Override
  public char charAt(int index) {
    return mySequence.charAt(mySequence.length()-index-1);
  }

  @Override
  public @NotNull CharSequence subSequence(int start, int end) {
    int length = mySequence.length();
    return new ReverseCharSequence(mySequence.subSequence(length - end, length - start));
  }
}
