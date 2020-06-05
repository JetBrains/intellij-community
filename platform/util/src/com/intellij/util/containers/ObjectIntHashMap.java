// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * return -1 instead of 0 if no such mapping exists
 */
public class ObjectIntHashMap<K> extends TObjectIntHashMap<K> {
  public ObjectIntHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  public ObjectIntHashMap(@NotNull TObjectHashingStrategy<? super K> strategy) {
    super((TObjectHashingStrategy<K>)strategy);
  }

  public ObjectIntHashMap(int initialCapacity, @NotNull TObjectHashingStrategy<? super K> strategy) {
    super(initialCapacity, (TObjectHashingStrategy<K>)strategy);
  }

  public ObjectIntHashMap() {
    super();
  }

  @Override
  public final int get(K key) {
    return get(key, -1);
  }

  public final int get(K key, int defaultValue) {
    int index = index(key);
    return index < 0 ? defaultValue : _values[index];
  }

  public int put(K key, int value, int defaultValue) {
    int index = index(key);
    int prev = super.put(key, value);
    return index >= 0 ? prev : defaultValue;
  }
}
