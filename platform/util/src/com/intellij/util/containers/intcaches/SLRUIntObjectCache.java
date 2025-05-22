// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.containers.intcaches;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;

/** Specialization of {@link com.intellij.util.containers.SLRUCache} for int keys */
@ApiStatus.Internal
public abstract class SLRUIntObjectCache<V> extends SLRUIntObjectMap<V> {

  //TODO RC: use delegation instead of inheritance

  protected SLRUIntObjectCache(int protectedQueueSize, int probationalQueueSize) {
    super(protectedQueueSize, probationalQueueSize);
  }

  public abstract @NotNull V createValue(int key);

  @Override
  public @NotNull V get(int key) {
    V value = getIfCached(key);
    if (value != null) {
      return value;
    }

    value = createValue(key);
    put(key, value);

    return value;
  }

  public @Nullable V getIfCached(int key) {
    return super.get(key);
  }

  public static @NotNull <V> SLRUIntObjectCache<V> slruCache(int protectedQueueSize,
                                                             int probationalQueueSize,
                                                             @NotNull IntFunction<? extends @NotNull V> valueProducer) {
    return new SLRUIntObjectCache<V>(protectedQueueSize, probationalQueueSize) {
      @Override
      public @NotNull V createValue(int key) {
        return valueProducer.apply(key);
      }
    };
  }
}