// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import java.nio.ByteBuffer;

/**
 * The xor filter, a new algorithm that can replace a Bloom filter.
 * <p>
 * It needs 1.23 log(1/fpp) bits per key. It is related to the BDZ algorithm [1]
 * (a minimal perfect hash function algorithm).
 * <p>
 * [1] paper: Simple and Space-Efficient Minimal Perfect Hash Functions -
 * http://cmph.sourceforge.net/papers/wads07.pdf
 */
public final class Xor16 {
  private static final int BITS_PER_FINGERPRINT = 16;
  private static final int HASHES = 3;
  private static final int OFFSET = 32;
  private static final int FACTOR_TIMES_100 = 123;

  private final int blockLength;
  private final long seed;
  private final short[] fingerprints;

  private Xor16(short[] fingerprints, int blockLength, long seed) {
    this.seed = seed;
    this.fingerprints = fingerprints;
    this.blockLength = blockLength;
  }

  public Xor16(ByteBuffer buffer) {
    seed = buffer.getLong();
    fingerprints = new short[buffer.getInt()];
    blockLength = fingerprints.length / HASHES;
    buffer.asShortBuffer().get(fingerprints);
    buffer.position(buffer.position() + (fingerprints.length * Short.BYTES));
  }

  public int sizeInBytes() {
    return Long.BYTES + Integer.BYTES + (fingerprints.length * Short.BYTES);
  }

  public void write(ByteBuffer buffer) {
    buffer.putLong(seed);
    buffer.putInt(fingerprints.length);
    buffer.asShortBuffer().put(fingerprints);
    buffer.position(buffer.position() + (fingerprints.length * Short.BYTES));
  }

  private static int getArrayLength(int size) {
    return (int)(OFFSET + (long)FACTOR_TIMES_100 * size / 100);
  }

    public static int getBlockLength(int keyCount) {
      return getArrayLength(keyCount) / HASHES;
    }

  private static long mix(final long x) {
    long h = x * 0x9E3779B97F4A7C15L;
    h ^= h >>> 32;
    return h ^ (h >>> 16);
  }

  public static Xor16 construct(final long[] keys, final int offset, final int length) {
    final int arrayLength = getArrayLength(length);
    final int blockLength = arrayLength / HASHES;
    final long[] reverseOrder = new long[length];
    final byte[] reverseH = new byte[length];
    int reverseOrderPos;
    // constant seed - reproducible JARs (initial seed just a random number)
    long seed = 1354212L;
    do {
      seed = mix(seed);
      byte[] t2count = new byte[arrayLength];
      long[] t2 = new long[arrayLength];
      for (int i = offset; i < length; i++) {
        long k = keys[i];
        for (int hi = 0; hi < HASHES; hi++) {
          int h = getHash(blockLength, k, seed, hi);
          t2[h] ^= k;
          if (t2count[h] > 120) {
            throw new IllegalArgumentException();
          }
          t2count[h]++;
        }
      }
      int[] alone = new int[arrayLength];
      int alonePos = 0;
      reverseOrderPos = 0;
      for (int nextAloneCheck = 0; nextAloneCheck < arrayLength; ) {
        while (nextAloneCheck < arrayLength) {
          if (t2count[nextAloneCheck] == 1) {
            alone[alonePos++] = nextAloneCheck;
          }
          nextAloneCheck++;
        }
        while (alonePos > 0) {
          int i = alone[--alonePos];
          if (t2count[i] == 0) {
            continue;
          }
          long k = t2[i];
          byte found = -1;
          for (int hi = 0; hi < HASHES; hi++) {
            int h = getHash(blockLength, k, seed, hi);
            int newCount = --t2count[h];
            if (newCount == 0) {
              found = (byte)hi;
            }
            else {
              if (newCount == 1) {
                alone[alonePos++] = h;
              }
              t2[h] ^= k;
            }
          }
          reverseOrder[reverseOrderPos] = k;
          reverseH[reverseOrderPos] = found;
          reverseOrderPos++;
        }
      }
    }
    while (reverseOrderPos != length);
    short[] fingerprints = new short[arrayLength];
    for (int i = reverseOrderPos - 1; i >= 0; i--) {
      long k = reverseOrder[i];
      int found = reverseH[i];
      int change = -1;
      long hash = hash64(k, seed);
      int xor = fingerprint(hash);
      for (int hi = 0; hi < HASHES; hi++) {
        int h = getHash(blockLength, k, seed, hi);
        if (found == hi) {
          change = h;
        }
        else {
          xor ^= fingerprints[h];
        }
      }
      fingerprints[change] = (short)xor;
    }
    return new Xor16(fingerprints, blockLength, seed);
  }

  private static int getHash(int blockLength, long key, @SuppressWarnings("SameParameterValue") long seed, int index) {
    long r = Long.rotateLeft(hash64(key, seed), 21 * index);
    r = reduce((int)r, blockLength);
    r += (long)index * blockLength;
    return (int)r;
  }

  public boolean mightContain(long key) {
    long hash = hash64(key, seed);
    int f = fingerprint(hash);
    int r0 = (int)hash;
    int r1 = (int)Long.rotateLeft(hash, 21);
    int r2 = (int)Long.rotateLeft(hash, 42);
    int h0 = reduce(r0, blockLength);
    int h1 = reduce(r1, blockLength) + blockLength;
    int h2 = reduce(r2, blockLength) + 2 * blockLength;
    f ^= fingerprints[h0] ^ fingerprints[h1] ^ fingerprints[h2];
    return (f & 0xffff) == 0;
  }

  private static int fingerprint(long hash) {
    return (int)(hash & ((1 << BITS_PER_FINGERPRINT) - 1));
  }

  private static long hash64(long x, long seed) {
    x += seed;
    x = (x ^ (x >>> 33)) * 0xff51afd7ed558ccdL;
    x = (x ^ (x >>> 33)) * 0xc4ceb9fe1a85ec53L;
    x = x ^ (x >>> 33);
    return x;
  }

  private static int reduce(int hash, int n) {
    // http://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
    return (int)(((hash & 0xffffffffL) * n) >>> 32);
  }
}