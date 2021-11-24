// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package org.minperf;

final class Mix {
  public static int supplementalHashWeyl(long hash, long index) {
    long x = hash + (index * 0xbf58476d1ce4e5b9L);
    x = (x ^ (x >>> 32)) * 0xbf58476d1ce4e5b9L;
    x = ((x >>> 32) ^ x);
    return (int)x;
  }

  public static int hash32(int x) {
    x = ((x >>> 16) ^ x) * 0x45d9f3b;
    x = ((x >>> 16) ^ x) * 0x45d9f3b;
    x = (x >>> 16) ^ x;
    return x;
  }

  public static int unhash32(int x) {
    x = ((x >>> 16) ^ x) * 0x119de1f3;
    x = ((x >>> 16) ^ x) * 0x119de1f3;
    x = (x >>> 16) ^ x;
    return x;
  }

  public static long hash64(long x) {
    x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
    x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
    x = x ^ (x >>> 31);
    return x;
  }

  public static long unhash64(long x) {
    x = (x ^ (x >>> 31) ^ (x >>> 62)) * 0x319642b2d24d8ec3L;
    x = (x ^ (x >>> 27) ^ (x >>> 54)) * 0x96de1b173f119089L;
    x = x ^ (x >>> 30) ^ (x >>> 60);
    return x;
  }
}
