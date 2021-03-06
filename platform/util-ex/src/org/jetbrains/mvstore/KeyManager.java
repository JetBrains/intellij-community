/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import io.netty.buffer.ByteBuf;

public interface KeyManager<K> {
  /**
   * Perform binary search for the key within the storage.
   * Instead of always starting the search in the middle, the last found index is cached.
   * <p>
   * If the key was found, the returned value is the index in the key array.
   * If not found, the returned value is negative insertion index, where -1 means the provided
   * key is smaller than any keys in this page, -2 means insert between 0 and 1 elements, and so on.
   */
  int binarySearch(K key, MVMap<K, ?> map, int initialGuess);

  K getKey(int index);

  int getKeyCount();

  /**
   * Expand the keys array.
   *
   * @param extraKeyCount number of extra key entries to create
   * @param extraKeys     extra key values
   */
  KeyManager<K> expandKeys(int extraKeyCount, K[] extraKeys, MVMap<K, ?> map);

  KeyManager<K> copy(int startIndex, int endIndex, MVMap<K, ?> map);

  /**
   * Insert a key into the key array.
   */
  KeyManager<K> insertKey(int index, K key, MVMap<K, ?> map);

  /**
   * Remove the key at the given index.
   *
   * @param index the index
   */
  KeyManager<K> remove(int index, MVMap<K, ?> map);

  void write(int count, MVMap<K, ?> map, ByteBuf buf);

  int getSerializedDataSize();
}
