// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  private static final class WeakKey<T> extends WeakReference<T> implements Key<T> {
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
