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

import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Map;

final class WeakKeySoftValueHashMap<K,V> extends RefKeyRefValueHashMap<K,V> implements Map<K,V>{
  WeakKeySoftValueHashMap() {
    super((RefHashMap<K, ValueReference<K, V>>)ContainerUtil.<K, ValueReference<K, V>>createWeakMap());
  }

  private static class SoftValueReference<K,V> extends SoftReference<V> implements ValueReference<K,V> {
    @NotNull private final RefHashMap.Key<K> key;

    private SoftValueReference(@NotNull RefHashMap.Key<K> key, V referent, ReferenceQueue<? super V> q) {
      super(referent, q);
      this.key = key;
    }

    @NotNull
    @Override
    public RefHashMap.Key<K> getKey() {
      return key;
    }
  }

  @NotNull
  @Override
  protected ValueReference<K, V> createValueReference(@NotNull RefHashMap.Key<K> key,
                                                      V referent,
                                                      ReferenceQueue<? super V> q) {
    return new SoftValueReference<K, V>(key, referent, q);
  }
}
