/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 18.12.2006
 * Time: 20:18:31
 */
package com.intellij.util.containers;

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;

/**
 * Fully copied from java.util.WeakHashMap except "get" method optimization.
 */
public final class ConcurrentWeakHashMap<K, V> extends ConcurrentRefHashMap<K, V> {
  private static class WeakKey<K, V> extends WeakReference<K> implements Key<K, V> {
    private final int myHash; /* Hashcode of key, stored here since the key may be tossed by the GC */
    private final V value;

    private WeakKey(@NotNull K k, final int hash, V v, ReferenceQueue<K> q) {
      super(k, q);
      value = v;
      myHash = hash;
    }

    @Override
    public V getValue() {
      return value;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Object t = get();
      Object u = ((Key)o).get();
      if (t == null || u == null) return false;
      if (t == u) return true;
      return t.equals(u);
    }

    public int hashCode() {
      return myHash;
    }
  }

  @Override
  protected Key<K, V> createKey(@NotNull K key, V value, @NotNull TObjectHashingStrategy<K> hashingStrategy) {
    return new WeakKey<K, V>(key, hashingStrategy.computeHashCode(key), value, myReferenceQueue);
  }

  public ConcurrentWeakHashMap(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  public ConcurrentWeakHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  public ConcurrentWeakHashMap() {
  }

  public ConcurrentWeakHashMap(int initialCapacity,
                               float loadFactor,
                               int concurrencyLevel,
                               @NotNull TObjectHashingStrategy<K> hashingStrategy) {
    super(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  public ConcurrentWeakHashMap(Map<? extends K, ? extends V> t) {
    super(t);
  }

  public ConcurrentWeakHashMap(@NotNull TObjectHashingStrategy<K> hashingStrategy) {
    super(hashingStrategy);
  }
}
