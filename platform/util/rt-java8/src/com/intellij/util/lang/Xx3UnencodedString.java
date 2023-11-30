// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

/**
 * Do not use. Only as a temporary solution for class loader implementation where Java 9+ cannot be used.
 */
public final class Xx3UnencodedString {
  public static long hashUnencodedString(String input) {
    return Xxh3Impl.hash(input, CharSequenceAccess.INSTANCE, 0, input.length() * 2, 0);
  }

  public static long hashUnencodedStringRange(String input, int end) {
    return Xxh3Impl.hash(input, CharSequenceAccess.INSTANCE, 0, end * 2, 0);
  }
}
