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
package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;

public final class WeakHashMap<K, V> extends RefHashMap<K, V> {
  public WeakHashMap(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  public WeakHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  public WeakHashMap() {
    super();
  }

  public WeakHashMap(@NotNull Map<K, V> t) {
    super(t);
  }

  @Override
  protected <T> Key<T> createKey(@NotNull T k, @NotNull ReferenceQueue<? super T> q) {
    return new WeakKey<T>(k, q);
  }

  private static class WeakKey<T> extends WeakReference<T> implements Key<T> {
    private final int myHash;	/* Hashcode of key, stored here since the key may be tossed by the GC */

    private WeakKey(@NotNull T k, @NotNull ReferenceQueue<? super T> q) {
      super(k, q);
      myHash = k.hashCode();
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Object t = get();
      Object u = ((Key)o).get();
      return myHash == o.hashCode() && (t == u || Comparing.equal(t, u));
    }

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
