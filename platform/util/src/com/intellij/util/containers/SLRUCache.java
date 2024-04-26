// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.util.containers;

import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.hash.EqualityPolicy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

@ApiStatus.Internal
public abstract class SLRUCache<K, V> extends SLRUMap<K, V> {
  protected SLRUCache(int protectedQueueSize, int probationalQueueSize) {
    super(protectedQueueSize, probationalQueueSize);
  }

  protected SLRUCache(int protectedQueueSize, int probationalQueueSize, @NotNull EqualityPolicy<? super K> hashingStrategy) {
    super(protectedQueueSize, probationalQueueSize, hashingStrategy);
  }

  public abstract @NotNull V createValue(K key);

  @Override
  public @NotNull V get(K key) {
    V value = getIfCached(key);
    if (value != null) {
      return value;
    }

    value = createValue(key);
    put(key, value);

    return value;
  }

  public @Nullable V getIfCached(K key) {
    return super.get(key);
  }

  public static @NotNull <K, V> SLRUCache<K, V> slruCache(int protectedQueueSize,
                                                          int probationalQueueSize,
                                                          @NotNull Function<@NotNull K, @NotNull V> valueProducer) {
    return new SLRUCache<K, V>(protectedQueueSize, probationalQueueSize) {
      @Override
      public @NotNull V createValue(K key) {
        return valueProducer.apply(key);
      }
    };
  }

  /**
   * @deprecated Use Caffeine.
   */
  @Deprecated
  public static @NotNull <K, V> SLRUCache<K, V> create(
    int protectedQueueSize,
    int probationalQueueSize,
    @SuppressWarnings("UsagesOfObsoleteApi") @NotNull NotNullFunction<? super K, ? extends V> valueProducer
  ) {
    return slruCache(protectedQueueSize, probationalQueueSize, valueProducer::fun);
  }
}