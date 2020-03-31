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


import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

/**
 * Concurrent key:int -> soft value:V map
 * Null values are NOT allowed
 * Use {@link ContainerUtil#createConcurrentIntObjectSoftValueMap()} to create this
 */
class ConcurrentIntKeySoftValueHashMap<V> extends ConcurrentIntKeyRefValueHashMap<V> {
  private static class MyRef<V> extends SoftReference<V> implements IntReference<V> {
    private final int valueHash;
    private final int key;

    private MyRef(int key, @NotNull V referent, @NotNull ReferenceQueue<V> queue) {
      super(referent, queue);
      this.key = key;
      valueHash = referent.hashCode();
    }

    @Override
    public int hashCode() {
      return valueHash;
    }

    @Override
    public boolean equals(Object obj) {
      V v = get();
      if (!(obj instanceof MyRef)) {
        return false;
      }
      //noinspection unchecked
      MyRef<V> other = (MyRef<V>)obj;
      return other.valueHash == valueHash && key == other.getKey() && Comparing.equal(v, other.get());
    }

    @Override
    public int getKey() {
      return key;
    }
  }

  @NotNull
  @Override
  protected IntReference<V> createReference(int key, @NotNull V value, @NotNull ReferenceQueue<V> queue) {
    return new MyRef<>(key, value, queue);
  }
}
