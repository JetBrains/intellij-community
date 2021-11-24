// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package org.minperf.universal;

/**
 * An interface that can calculate multiple hash values for an object. The
 * returned hash value of two distinct objects may be the same for a given
 * hash function index, but as more hash functions indices are called for
 * those objects, the returned value must eventually be different
 * (the earlier, the better).
 * <p>
 * The returned value does not need to be uniformly distributed.
 *
 * @param <T> the type
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
}
