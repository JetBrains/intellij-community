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

import com.intellij.util.DeprecatedMethodException;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * Weak keys hash map.
 * Custom TObjectHashingStrategy is supported.
 * Null keys are NOT allowed
 * Null values are allowed
 *
 * Use this class if you need custom TObjectHashingStrategy.
 * Do not use this class if you have null keys (shame on you).
 * Otherwise it's the same as java.util.WeakHashMap, you are free to use either.
 *
 * @deprecated use {@link ContainerUtil#createWeakMap()} instead
 */
@Deprecated
public final class WeakHashMap<K, V> extends RefHashMap<K, V> {
  public WeakHashMap(int initialCapacity) {
    super(initialCapacity);
    DeprecatedMethodException.report("Use ContainerUtil.createWeakMap() instead");
  }

  public WeakHashMap() {
    DeprecatedMethodException.report("Use ContainerUtil.createWeakMap() instead");
  }

  WeakHashMap(int initialCapacity, float loadFactor, @NotNull TObjectHashingStrategy<? super K> strategy) {
    super(initialCapacity, loadFactor, strategy);
  }

  @NotNull
  @Override
  protected <T> Key<T> createKey(@NotNull T k, @NotNull TObjectHashingStrategy<? super T> strategy, @NotNull ReferenceQueue<? super T> q) {
    return new WeakKey<>(k, strategy, q);
  }

  private static class WeakKey<T> extends WeakReference<T> implements Key<T> {
    private final int myHash; // Hashcode of key, stored here since the key may be tossed by the GC
    @NotNull private final TObjectHashingStrategy<? super T> myStrategy;

    private WeakKey(@NotNull T k, @NotNull TObjectHashingStrategy<? super T> strategy, @NotNull ReferenceQueue<? super T> q) {
      super(k, q);
      myStrategy = strategy;
      myHash = strategy.computeHashCode(k);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      T t = get();
      T u = ((Key<T>)o).get();
      return keyEqual(t,u,myStrategy);
    }

    @Override
    public int hashCode() {
      return myHash;
    }

    @NonNls
    @Override
    public String toString() {
      Object t = get();
      return "WeakKey(" + t + ", " + myHash + ")";
    }
  }
}
