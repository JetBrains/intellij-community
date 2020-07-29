// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

/**
 * StringBuilderSpinAllocator reuses StringBuilder instances performing non-blocking allocation and dispose.
 * @deprecated Use {@code new} {@link StringBuilder} and don't be smarter than necessary
 */
@Deprecated
public final class StringBuilderSpinAllocator {
  private StringBuilderSpinAllocator() {
  }

  public static StringBuilder alloc() {
    return new StringBuilder();
  }

  public static void dispose(StringBuilder instance) {
  }
}
