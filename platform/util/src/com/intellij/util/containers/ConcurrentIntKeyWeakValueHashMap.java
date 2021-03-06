// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.containers;


import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * Concurrent key:int -> weak value:V map
 * Null values are NOT allowed
 * @deprecated Use {@link com.intellij.concurrency.ConcurrentCollectionFactory#createConcurrentIntObjectWeakValueMap()} instead
 */
@Deprecated
class ConcurrentIntKeyWeakValueHashMap<V> extends ConcurrentIntKeyRefValueHashMap<V> {
  private static final class MyRef<V> extends WeakReference<V> implements IntReference<V> {
    private final int hash;
    private final int key;

    private MyRef(int key, @NotNull V referent, @NotNull ReferenceQueue<V> queue) {
      super(referent, queue);
      this.key = key;
      hash = referent.hashCode();
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      V v = get();
      if (!(obj instanceof MyRef)) {
        return false;
      }
      //noinspection unchecked
      MyRef<V> other = (MyRef<V>)obj;
      return other.hash == hash && key == other.getKey() && Comparing.equal(v, other.get());
    }

    @Override
    public int getKey() {
      return key;
    }
  }

  @NotNull
  @Override
  protected IntReference<V> createReference(int key, @NotNull V value, @NotNull ReferenceQueue<V> queue) {
    return new MyRef<>(key, value, queue);
  }
}
