// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public abstract class ImmutableCharSequence implements CharSequence {

  @Contract(pure = true)
  public static CharSequence asImmutable(final @NotNull CharSequence cs) {
    return isImmutable(cs) ? cs : cs.toString();
  }

  private static boolean isImmutable(final @NotNull CharSequence cs) {
    return cs instanceof ImmutableCharSequence ||
           cs instanceof CharSequenceSubSequence && isImmutable(((CharSequenceSubSequence)cs).getBaseSequence());
  }

  @Contract(pure = true)
  public abstract @NotNull ImmutableCharSequence concat(@NotNull CharSequence sequence);

  @Contract(pure = true)
  public abstract @NotNull ImmutableCharSequence insert(int index, @NotNull CharSequence seq);

  @Contract(pure = true)
  public abstract @NotNull ImmutableCharSequence delete(int start, int end);

  @Contract(pure = true)
  public abstract @NotNull ImmutableCharSequence subtext(int start, int end);

  @Contract(pure = true)
  public @NotNull ImmutableCharSequence replace(int start, int end, @NotNull CharSequence seq) {
    return delete(start, end).insert(start, seq);
  }

  @Override
  public abstract @NotNull String toString();
}
