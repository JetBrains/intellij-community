// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

/**
 * Concurrent key:int -> soft value:V map
 * Null values are NOT allowed
 * Use {@link com.intellij.concurrency.ConcurrentCollectionFactory#createConcurrentIntObjectSoftValueMap()} to create this
 */
final class ConcurrentIntKeySoftValueHashMap<V> extends ConcurrentIntKeyRefValueHashMap<V> {
  private static final class MyRef<V> extends SoftReference<V> implements IntReference<V> {
    private final int valueHash;
    private final int key;

    private MyRef(int key, @NotNull V referent, @NotNull ReferenceQueue<V> queue) {
      super(referent, queue);
      this.key = key;
      valueHash = referent.hashCode();
    }

    @Override
    public int hashCode() {
      return valueHash;
    }

    @Override
    public boolean equals(Object obj) {
      V v = get();
      if (!(obj instanceof MyRef)) {
        return false;
      }
      //noinspection unchecked
      MyRef<V> other = (MyRef<V>)obj;
      return other.valueHash == valueHash && key == other.getKey() && Comparing.equal(v, other.get());
    }

    @Override
    public int getKey() {
      return key;
    }
  }

  @Override
  protected @NotNull IntReference<V> createReference(int key, @NotNull V value, @NotNull ReferenceQueue<V> queue) {
    return new MyRef<>(key, value, queue);
  }
}
