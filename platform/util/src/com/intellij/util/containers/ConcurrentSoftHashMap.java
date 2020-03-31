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

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

/**
 * Concurrent soft key:K -> strong value:V map
 * Null keys are allowed
 * Null values are NOT allowed
 * Use {@link ContainerUtil#createConcurrentSoftMap()} to create this
 */
final class ConcurrentSoftHashMap<K, V> extends ConcurrentRefHashMap<K, V> {
  ConcurrentSoftHashMap() {
  }

  ConcurrentSoftHashMap(int initialCapacity,
                        float loadFactor,
                        int concurrencyLevel,
                        @NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    super(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  private static class SoftKey<K> extends SoftReference<K> implements KeyReference<K> {
    private final int myHash; // Hashcode of key, stored here since the key may be tossed by the GC
    private final TObjectHashingStrategy<? super K> myStrategy;

    private SoftKey(@NotNull K k,
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
      K u = ((KeyReference<K>)o).get();
      if (t == u) return true;
      if (t == null || u == null) return false;
      return myStrategy.equals(t, u);
    }

    @Override
    public int hashCode() {
      return myHash;
    }
  }

  @NotNull
  @Override
  protected KeyReference<K> createKeyReference(@NotNull K key,
                                               @NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    return new SoftKey<>(key, hashingStrategy.computeHashCode(key), hashingStrategy, myReferenceQueue);
  }
}
