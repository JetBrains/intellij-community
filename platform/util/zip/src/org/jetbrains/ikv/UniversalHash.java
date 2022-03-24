// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ikv;

import org.jetbrains.xxh3.Xxh3;

/**
 * An interface that can calculate multiple hash values for an object. The
 * returned hash value of two distinct objects may be the same for a given
 * hash function index, but as more hash functions indices are called for
 * those objects, the returned value must eventually be different
 * (the earlier, the better).
 * <p>
 * The returned value does not need to be uniformly distributed.
 */
public interface UniversalHash<T> {
  /**
   * Calculate the hash of the given object.
   *
   * @param key   the key in the set
   * @param index the hash function index (0, 1, 2,...)
   * @return the universal hash (64 bits)
   */
  long universalHash(T key, long index);

  final class IntHash implements UniversalHash<Integer> {
    public static final IntHash INSTANCE = new IntHash();

    @Override
    public long universalHash(Integer key, long index) {
      return Xxh3.hashInt(key, index);
    }
  }

  final class StringHash implements UniversalHash<String> {
    public static final StringHash INSTANCE = new StringHash();

    @Override
    public long universalHash(String key, long index) {
      return Xxh3.seededHash(key, index);
    }
  }
}