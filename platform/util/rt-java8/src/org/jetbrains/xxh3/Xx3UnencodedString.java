// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.xxh3;

import org.jetbrains.annotations.ApiStatus;

/**
 * Do not use. Only as a temporary solution for class loader implementation where Java 9+ cannot be used.
 */
@ApiStatus.Internal
public final class Xx3UnencodedString {
  public static long hashUnencodedString(String input) {
    return Xxh3Impl.hash(input, CharSequenceAccess.INSTANCE, 0, input.length() * 2, 0);
  }

  public static long hashUnencodedStringRange(String input, int start, int end) {
    return Xxh3Impl.hash(input, CharSequenceAccess.INSTANCE, start * 2, (end - start) * 2, 0);
  }
}
