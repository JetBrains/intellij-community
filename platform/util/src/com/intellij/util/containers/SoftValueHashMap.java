// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;

/**
 * Use {@link ContainerUtil#createSoftValueMap()} instead
 */
final class SoftValueHashMap<K,V> extends RefValueHashMap<K,V>{
  private static class MySoftReference<K, T> extends SoftReference<T> implements MyReference<K, T> {
    private final K key;

    MySoftReference(@NotNull K key, T referent, @NotNull ReferenceQueue<? super T> q) {
      super(referent, q);
      this.key = key;
    }

    @NotNull
    @Override
    public K getKey() {
      return key;
    }
  }

  @Override
  protected MyReference<K, V> createReference(@NotNull K key, V value, @NotNull ReferenceQueue<? super V> queue) {
    return new MySoftReference<>(key, value, queue);
  }
}
