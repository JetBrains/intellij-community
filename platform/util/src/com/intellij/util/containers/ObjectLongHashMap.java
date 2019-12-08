// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * return -1 instead of 0 if no such mapping exists
 */
public final class ObjectLongHashMap<K> extends TObjectLongHashMap<K> {
  public ObjectLongHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  public ObjectLongHashMap(@NotNull TObjectHashingStrategy<K> strategy) {
    super(strategy);
  }

  public ObjectLongHashMap(int initialCapacity, @NotNull TObjectHashingStrategy<K> strategy) {
    super(initialCapacity, strategy);
  }

  public ObjectLongHashMap() {
    super();
  }

  @Override
  public final long get(K key) {
    int index = index(key);
    return index < 0 ? -1 : _values[index];
  }

  public void putAll(@NotNull ObjectLongHashMap<K> other) {
    ensureCapacity(other.size());

    other.forEachEntry((k, v) -> {
      put(k, v);
      return true;
    });
  }

  @Override
  public String toString() {
    Object[] keys = keys();
    if (keys.length > 1 && keys[0] instanceof Comparable) {
      Arrays.sort(keys);
    }

    StringBuilder sb = new StringBuilder();
    sb.append('{');
    for (Object key : keys) {
      //noinspection unchecked
      long value = get((K)key);

      sb.append('\n');
      sb.append(key == this ? "(this Map)" : key);
      sb.append('=');
      sb.append(value);
      sb.append(',');
    }
    sb.append('\n');
    sb.append('}');
    return sb.toString();
  }
}