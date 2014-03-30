/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.util.ObjectUtils.NULL;

/**
 * @author peter
 */
public abstract class WeakFactoryMap<T,V> {
  private final ConcurrentMap<T, WeakReference<V>> myMap = new ConcurrentWeakHashMap<T, WeakReference<V>>();

  protected abstract V create(T key);

  public final V get(T key) {
    final WeakReference<V> reference = myMap.get(key);
    final V v = SoftReference.dereference(reference);
    if (v != null) {
      return v == NULL ? null : v;
    }

    final V value = create(key);
    WeakReference<V> valueRef = new WeakReference<V>(value == null ? (V)NULL : value);
    WeakReference<V> prevRef = myMap.putIfAbsent(key, valueRef);
    V prev = SoftReference.dereference(prevRef);
    return prev == null || prev == NULL ? value : prev;
  }

  public final boolean containsKey(T key) {
    return myMap.containsKey(key);
  }

  public void clear() {
    myMap.clear();
  }
}
