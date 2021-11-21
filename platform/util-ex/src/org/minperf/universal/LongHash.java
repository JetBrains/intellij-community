// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package org.minperf.universal;

/**
 * A sample hash implementation for long keys.
 */
public final class LongHash implements UniversalHash<Long> {
  public static long universalHash(long x, long index) {
    long v0 = index ^ 0x736f6d6570736575L;
    long v1 = index ^ 0x646f72616e646f6dL;
    long v2 = index ^ 0x6c7967656e657261L;
    long v3 = index ^ 0x7465646279746573L;
    v3 ^= x;
    for (int i = 0; i < 4; i++) {
      v0 += v1;
      v2 += v3;
      v1 = Long.rotateLeft(v1, 13);
      v3 = Long.rotateLeft(v3, 16);
      v1 ^= v0;
      v3 ^= v2;
      v0 = Long.rotateLeft(v0, 32);
      v2 += v1;
      v0 += v3;
      v1 = Long.rotateLeft(v1, 17);
      v3 = Long.rotateLeft(v3, 21);
      v1 ^= v2;
      v3 ^= v0;
      v2 = Long.rotateLeft(v2, 32);
    }
    v0 ^= x;
    return v0 ^ v1 ^ v2 ^ v3;
  }

  @Override
  public long universalHash(Long key, long index) {
    return universalHash((long)key, index);
  }

  @Override
  public String toString() {
    return "LongHash (SipHash)";
  }
}