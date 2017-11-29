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

import com.intellij.reference.SoftReference;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.util.Map;

/**
 * Soft keys hash map.
 * Null keys are NOT allowed
 * Null values are allowed
 *
 * @deprecated use {@link ContainerUtil#createSoftMap()} instead
 */
@Deprecated
public final class SoftHashMap<K,V> extends RefHashMap<K,V> {
  public SoftHashMap(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  public SoftHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  public SoftHashMap() {
  }

  public SoftHashMap(@NotNull Map<K, V> t) {
    super(t);
  }

  public SoftHashMap(@NotNull TObjectHashingStrategy<K> hashingStrategy) {
    super(hashingStrategy);
  }

  public SoftHashMap(int initialCapacity, float loadFactor, @NotNull TObjectHashingStrategy<K> strategy) {
    super(initialCapacity, loadFactor, strategy);
  }

  @NotNull
  @Override
  protected <T> Key<T> createKey(@NotNull T k, @NotNull TObjectHashingStrategy<T> strategy, @NotNull ReferenceQueue<? super T> q) {
    return new SoftKey<T>(k, strategy, q);
  }

  private static class SoftKey<T> extends SoftReference<T> implements Key<T> {
    private final int myHash;  /* Hash code of key, stored here since the key may be tossed by the GC */
    @NotNull private final TObjectHashingStrategy<T> myStrategy;

    private SoftKey(@NotNull T k, @NotNull TObjectHashingStrategy<T> strategy, @NotNull ReferenceQueue<? super T> q) {
      super(k, q);
      myStrategy = strategy;
      myHash = strategy.computeHashCode(k);
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      if (myHash != o.hashCode()) return false;
      T t = get();
      T u = ((Key<T>)o).get();
      if (t == null || u == null) return false;
      return keyEqual(t, u, myStrategy);
    }

    public int hashCode() {
      return myHash;
    }

    @NonNls
    @Override
    public String toString() {
      return "SoftHashMap.SoftKey(" + get() + ")";
    }
  }
}
