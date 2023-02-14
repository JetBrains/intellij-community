// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.util.containers;

import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.hash.EqualityPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SLRUCache<K, V> extends SLRUMap<K, V> {
  protected SLRUCache(final int protectedQueueSize, final int probationalQueueSize) {
    super(protectedQueueSize, probationalQueueSize);
  }

  protected SLRUCache(final int protectedQueueSize, final int probationalQueueSize, @NotNull EqualityPolicy<? super K> hashingStrategy) {
    super(protectedQueueSize, probationalQueueSize, hashingStrategy);
  }

  @NotNull
  public abstract V createValue(K key);

  @Override
  @NotNull
  public V get(K key) {
    V value = getIfCached(key);
    if (value != null) {
      return value;
    }

    value = createValue(key);
    put(key, value);

    return value;
  }

  @Nullable
  public V getIfCached(K key) {
    return super.get(key);
  }

  @NotNull
  public static <K, V> SLRUCache<K, V> create(int protectedQueueSize,
                                              int probationalQueueSize,
                                              @NotNull final NotNullFunction<? super K, ? extends V> valueProducer) {
    return new SLRUCache<K, V>(protectedQueueSize, probationalQueueSize) {
      @NotNull
      @Override
      public V createValue(K key) {
        return valueProducer.fun(key);
      }
    };
  }
}