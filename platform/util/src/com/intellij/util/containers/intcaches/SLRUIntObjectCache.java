// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.containers.intcaches;

import com.intellij.util.containers.intcaches.SLRUIntObjectMap.EvictionCallback;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.IntFunction;

/** Specialization of {@link com.intellij.util.containers.SLRUCache} for int keys. */
@ApiStatus.Internal
public class SLRUIntObjectCache<V> {

  private final SLRUIntObjectMap<V> map;

  private final @NotNull IntFunction<? extends @NotNull V> valueProducer;

  public SLRUIntObjectCache(int protectedQueueSize,
                            int probationalQueueSize,
                            @NotNull IntFunction<? extends @NotNull V> valueProducer,
                            @NotNull EvictionCallback<? super V> evictionCallback) {
    this.map = new SLRUIntObjectMap<>(
      protectedQueueSize,
      probationalQueueSize,
      evictionCallback
    );
    this.valueProducer = valueProducer;
  }

  public @Nullable V getIfCached(int key) {
    return map.get(key);
  }

  public @NotNull V getOrCreate(int key) {
    V value = getIfCached(key);
    if (value != null) {
      return value;
    }

    value = valueProducer.apply(key);
    map.put(key, value);

    return value;
  }

  public Collection<V> values() {
    return map.values();
  }

  public void evictAll() {
    map.clear();
  }
}