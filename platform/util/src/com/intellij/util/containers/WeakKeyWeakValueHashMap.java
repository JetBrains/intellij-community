// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;

final class WeakKeyWeakValueHashMap<K,V> extends RefKeyRefValueHashMap<K,V> implements Map<K,V>{
  WeakKeyWeakValueHashMap() {
    super((RefHashMap<K, ValueReference<K, V>>)ContainerUtil.<K, ValueReference<K, V>>createWeakMap());
  }

  private static final class WeakValueReference<K,V> extends WeakReference<V> implements ValueReference<K,V> {
    @NotNull private final RefHashMap.Key<K> key;

    private WeakValueReference(@NotNull RefHashMap.Key<K> key, V referent, ReferenceQueue<? super V> q) {
      super(referent, q);
      this.key = key;
    }

    @NotNull
    @Override
    public RefHashMap.Key<K> getKey() {
      return key;
    }
  }

  @NotNull
  @Override
  protected ValueReference<K, V> createValueReference(@NotNull RefHashMap.Key<K> key,
                                                      V referent,
                                                      ReferenceQueue<? super V> q) {
    return new WeakValueReference<>(key, referent, q);
  }
}
