// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * @deprecated use {@link ContainerUtil#createWeakValueMap()} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public final class WeakValueHashMap<K,V> extends RefValueHashMap<K,V> {
  private static final class MyWeakReference<K, T> extends WeakReference<T> implements MyReference<K, T> {
    private final K key;

    private MyWeakReference(@NotNull K key, T referent, ReferenceQueue<? super T> q) {
      super(referent, q);
      this.key = key;
    }

    @NotNull
    @Override
    public K getKey() {
      return key;
    }
  }

  /**
   * @deprecated use {@link ContainerUtil#createWeakValueMap()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public WeakValueHashMap() {
  }

  @Override
  protected MyReference<K, V> createReference(@NotNull K key, V value, @NotNull ReferenceQueue<? super V> queue) {
    return new MyWeakReference<>(key, value, queue);
  }
}
