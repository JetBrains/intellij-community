// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.minperf.universal;

import static java.lang.Long.rotateLeft;

public final class LongHash implements UniversalHash<Long> {
  private final static long PRIME64_1 = 0x9E3779B185EBCA87L;
  private final static long PRIME64_2 = 0xC2B2AE3D27D4EB4FL;
  private final static long PRIME64_3 = 0x165667B19E3779F9L;
  private final static long PRIME64_4 = 0x85EBCA77C2b2AE63L;
  private final static long PRIME64_5 = 0x27D4EB2F165667C5L;

  public static long universalHash(long x, long seed) {
    long hash = seed + PRIME64_5 + Long.BYTES;
    long temp = hash ^ rotateLeft(x * PRIME64_2, 31) * PRIME64_1;
    hash = rotateLeft(temp, 27) * PRIME64_1 + PRIME64_4;
    hash ^= hash >>> 33;
    hash *= PRIME64_2;
    hash ^= hash >>> 29;
    hash *= PRIME64_3;
    hash ^= hash >>> 32;
    return hash;
  }

  @Override
  public long universalHash(Long key, long index) {
    return universalHash((long)key, index);
  }

  @Override
  public String toString() {
    return "LongHash (XXHash64)";
  }
}