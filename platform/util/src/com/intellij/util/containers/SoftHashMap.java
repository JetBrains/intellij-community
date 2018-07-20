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
  public SoftHashMap() {
    DeprecatedMethodException.report("Use ContainerUtil.createSoftMap() instead");
  }

  SoftHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  SoftHashMap(@NotNull TObjectHashingStrategy<K> hashingStrategy) {
    super(hashingStrategy);
  }


  @NotNull
  @Override
  protected <T> Key<T> createKey(@NotNull T k, @NotNull TObjectHashingStrategy<? super T> strategy, @NotNull ReferenceQueue<? super T> q) {
    return new SoftKey<T>(k, strategy, q);
  }

  private static class SoftKey<T> extends SoftReference<T> implements Key<T> {
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
