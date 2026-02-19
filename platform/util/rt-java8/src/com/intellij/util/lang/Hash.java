/*
 * Copyright (C) 2002-2020 Sebastiano Vigna
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;

// we don't use mix - not required as a key it is a XXH3 hash
@ApiStatus.Internal
public final class Hash {
  public static final float FAST_LOAD_FACTOR = .5f;
  public static final int DEFAULT_INITIAL_SIZE = 16;

  public static int arraySize(final int expected, final float f) {
    final long s = Math.max(2, nextPowerOfTwo((long)Math.ceil(expected / f)));
    if (s > (1 << 30)) {
      throw new IllegalArgumentException("Too large (" + expected + " expected elements with load factor " + f + ")");
    }
    return (int)s;
  }

  private static long nextPowerOfTwo(long x) {
    return 1L << (64 - Long.numberOfLeadingZeros(x - 1));
  }

  public static int maxFill(int n, float f) {
    /* We must guarantee that there is always at least
     * one free entry (even with pathological load factors). */
    return Math.min((int)Math.ceil(n * f), n - 1);
  }
}
