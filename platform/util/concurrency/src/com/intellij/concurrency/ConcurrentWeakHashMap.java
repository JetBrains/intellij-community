// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.Nullable;

/**
 * Concurrent map with weak keys and strong values.
 * Null keys and values are not allowed.
 */
final class ConcurrentWeakHashMap<K, V> extends ConcurrentRefHashMap<K, V> {
  ConcurrentWeakHashMap(int initialCapacity,
                        float loadFactor,
                        @Nullable HashingStrategy<? super K> hashingStrategy,
                        @Nullable CollectionFactory.EvictionListener<K, V, ? super V> keyEvictionListener) {
    super(initialCapacity, loadFactor, ReferenceStrength.WEAK, hashingStrategy, keyEvictionListener);
  }
}
