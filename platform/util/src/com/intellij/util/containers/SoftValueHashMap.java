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

import com.intellij.reference.SoftReference;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;

public final class SoftValueHashMap<K,V> extends RefValueHashMap<K,V>{
  private static class MySoftReference<K, T> extends SoftReference<T> implements MyReference<K, T> {
    private final K key;

    public MySoftReference(@NotNull K key, T referent, @NotNull ReferenceQueue<? super T> q) {
      super(referent, q);
      this.key = key;
    }

    @Override
    public K getKey() {
      return key;
    }
  }

  public SoftValueHashMap() {
  }

  public SoftValueHashMap(@NotNull TObjectHashingStrategy<K> strategy) {
    super(strategy);
  }

  @Override
  protected MyReference<K, V> createReference(@NotNull K key, V value, @NotNull ReferenceQueue<V> queue) {
    return new MySoftReference<K, V>(key, value, queue);
  }
}
