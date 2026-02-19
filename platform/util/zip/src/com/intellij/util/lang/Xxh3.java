// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Characters are encoded using UTF-8. Not optimized for non-ASCII string.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public final class Xxh3 {
  public static long hash(byte @NotNull [] input) {
    return Xxh3Impl.hash(input, ByteArrayAccess.INSTANCE, 0, input.length, 0);
  }

  public static long hash(byte @NotNull [] input, int offset, int length) {
    return Xxh3Impl.hash(input, ByteArrayAccess.INSTANCE, offset, length, 0);
  }
}