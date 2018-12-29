/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.util.DeprecatedMethodException;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * Concurrent weak key:K -> strong value:V map.
 * Null keys are allowed
 * Null values are NOT allowed
 * @deprecated Use {@link ContainerUtil#createConcurrentWeakMap()} instead
 */
@Deprecated
public final class ConcurrentWeakHashMap<K, V> extends ConcurrentRefHashMap<K, V> {
  private static class WeakKey<K, V> extends WeakReference<K> implements KeyReference<K, V> {
    private final int myHash; /* Hashcode of key, stored here since the key may be tossed by the GC */
    @NotNull private final TObjectHashingStrategy<? super K> myStrategy;

    private WeakKey(@NotNull K k,
                    final int hash,
                    @NotNull TObjectHashingStrategy<? super K> strategy,
                    @NotNull ReferenceQueue<K> q) {
      super(k, q);
      myStrategy = strategy;
      myHash = hash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof KeyReference)) return false;
      K t = get();
      K u = ((KeyReference<K,V>)o).get();
      if (t == null || u == null) return false;
      return t == u || myStrategy.equals(t, u);
    }

    @Override
    public int hashCode() {
      return myHash;
    }
  }

  @NotNull
  @Override
  protected KeyReference<K, V> createKeyReference(@NotNull K key,
                                                  @NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    return new WeakKey<K, V>(key, hashingStrategy.computeHashCode(key), hashingStrategy, myReferenceQueue);
  }

  @Deprecated
  public ConcurrentWeakHashMap(int initialCapacity) {
    super(initialCapacity);
    DeprecatedMethodException.report("Use com.intellij.util.containers.ConcurrentFactoryMap.createConcurrentWeakMap instead");
  }

  @Deprecated
  public ConcurrentWeakHashMap() {
    DeprecatedMethodException.report("Use com.intellij.util.containers.ConcurrentFactoryMap.createConcurrentWeakMap instead");
  }

  ConcurrentWeakHashMap(float loadFactor) {
    this(DEFAULT_CAPACITY, loadFactor, DEFAULT_CONCURRENCY_LEVEL, ContainerUtil.canonicalStrategy());
  }

  ConcurrentWeakHashMap(int initialCapacity,
                        float loadFactor,
                        int concurrencyLevel,
                        @NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    super(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  ConcurrentWeakHashMap(@NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    super(hashingStrategy);
  }
}
