/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.containers;

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;

/**
 * Concurrent strong key:K -> weak value:V map
 * Null keys are NOT allowed
 * Null values are NOT allowed
 * @deprecated Use {@link ContainerUtil#createConcurrentWeakValueMap()} instead
 */
public final class ConcurrentWeakValueHashMap<K,V> extends ConcurrentRefValueHashMap<K,V> {
  public ConcurrentWeakValueHashMap(@NotNull Map<K, V> map) {
    super(map);
  }

  public ConcurrentWeakValueHashMap() {
  }

  public ConcurrentWeakValueHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    super(initialCapacity, loadFactor, concurrencyLevel);
  }

  public ConcurrentWeakValueHashMap(int initialCapacity, float loadFactor, int concurrencyLevel, @NotNull TObjectHashingStrategy<K> hashingStrategy) {
    super(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  private static class MyWeakReference<K, V> extends WeakReference<V> implements ValueReference<K, V> {
    private final K key;
    private MyWeakReference(@NotNull K key, @NotNull V referent, @NotNull ReferenceQueue<V> q) {
      super(referent, q);
      this.key = key;
    }

    @NotNull
    @Override
    public K getKey() {
      return key;
    }

    // When referent is collected, equality should be identity-based (for the processQueues() remove this very same SoftValue)
    // otherwise it's just canonical equals on referents for replace(K,V,V) to work
    public final boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      @SuppressWarnings("unchecked")
      ValueReference<K,V> that = (ValueReference)o;

      V v = get();
      V thatV = that.get();
      return key.equals(that.getKey()) && v != null && thatV != null && v.equals(thatV);
    }
  }

  @NotNull
  @Override
  protected ValueReference<K, V> createValueReference(@NotNull K key, @NotNull V value) {
    return new MyWeakReference<K,V>(key, value, myQueue);
  }
}
