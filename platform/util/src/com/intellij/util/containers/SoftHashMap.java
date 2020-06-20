// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.reference.SoftReference;
import com.intellij.util.DeprecatedMethodException;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;

/**
 * Soft keys hash map.
 * Null keys are NOT allowed
 * Null values are allowed
 *
 * @deprecated use {@link ContainerUtil#createSoftMap()} instead
 */
@Deprecated
public final class SoftHashMap<K,V> extends RefHashMap<K,V> {
  /**
   * Soft keys hash map.
   * Null keys are NOT allowed
   * Null values are allowed
   *
   * @deprecated use {@link ContainerUtil#createSoftMap()} instead
   */
  @Deprecated
  public SoftHashMap() {
    DeprecatedMethodException.report("Use ContainerUtil.createSoftMap() instead");
  }

  SoftHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  SoftHashMap(@NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    super(hashingStrategy);
  }


  @NotNull
  @Override
  protected <T> Key<T> createKey(@NotNull T k, @NotNull TObjectHashingStrategy<? super T> strategy, @NotNull ReferenceQueue<? super T> q) {
    return new SoftKey<>(k, strategy, q);
  }

  private static final class SoftKey<T> extends SoftReference<T> implements Key<T> {
    private final int myHash;  /* Hash code of key, stored here since the key may be tossed by the GC */
    @NotNull private final TObjectHashingStrategy<? super T> myStrategy;

    private SoftKey(@NotNull T k, @NotNull TObjectHashingStrategy<? super T> strategy, @NotNull ReferenceQueue<? super T> q) {
      super(k, q);
      myStrategy = strategy;
      myHash = strategy.computeHashCode(k);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      if (myHash != o.hashCode()) return false;
      T t = get();
      T u = ((Key<T>)o).get();
      if (t == null || u == null) return false;
      return keyEqual(t, u, myStrategy);
    }

    @Override
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
