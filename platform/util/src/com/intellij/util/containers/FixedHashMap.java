// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link Map} which stores not more than {@link #maxSize} entries.
 * On attempt to put more, the eldest element is removed.
 */
public final class FixedHashMap<K, V> extends LinkedHashMap<K, V> {
  private final int maxSize;

  public FixedHashMap(int maxSize) {
    this.maxSize = maxSize;
  }

  public FixedHashMap(int maxSize, int initialCapacity, float loadFactor, boolean accessOrder) {
    super(initialCapacity, loadFactor, accessOrder);

    this.maxSize = maxSize;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > maxSize;
  }
}