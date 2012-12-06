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
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author peter
 */
public class ConcurrentSoftArrayHashMap<T,V> implements Cloneable {
  private ConcurrentSoftHashMap<T, ConcurrentSoftArrayHashMap<T,V>> myContinuationMap;
  private ConcurrentSoftHashMap<T, SoftReference<V>> myValuesMap;
  private SoftReference<V> myEmptyValue;
  private final TObjectHashingStrategy<T> myStrategy;

  public ConcurrentSoftArrayHashMap() {
    this(ContainerUtil.<T>canonicalStrategy());
  }

  public ConcurrentSoftArrayHashMap(@NotNull TObjectHashingStrategy<T> strategy) {
    myStrategy = strategy;
  }

  @Nullable
  private V get(T[] array, int index) {
    if (index == array.length - 1) {
      final ConcurrentSoftHashMap<T, SoftReference<V>> valuesMap = myValuesMap;
      if (valuesMap != null) {
        final SoftReference<V> softReference = valuesMap.get(array[index]);
        if (softReference != null) {
          return softReference.get();
        }
      }
      return null;
    }

    final ConcurrentSoftHashMap<T, ConcurrentSoftArrayHashMap<T, V>> continuationMap = myContinuationMap;
    if (continuationMap != null) {
      final ConcurrentSoftArrayHashMap<T, V> map = continuationMap.get(array[index]);
      if (map != null) {
        return map.get(array, index + 1);
      }
    }

    return null;
  }

  @Nullable
  public final V get(T[] key) {
    if (key.length == 0) {
      final SoftReference<V> emptyValue = myEmptyValue;
      return emptyValue == null ? null : emptyValue.get();
    }
    return get(key, 0);
  }

  private void put(T[] array, int index, V value) {
    final T key = array[index];
    if (index == array.length - 1) {
      if (myValuesMap == null) {
        myValuesMap = new ConcurrentSoftHashMap<T, SoftReference<V>>(myStrategy);
      }
      myValuesMap.put(key, new SoftReference<V>(value));
    } else {
      if (myContinuationMap == null) {
        myContinuationMap = new ConcurrentSoftHashMap<T, ConcurrentSoftArrayHashMap<T,V>>(myStrategy);
      }
      ConcurrentSoftArrayHashMap<T, V> softArrayHashMap = myContinuationMap.get(key);
      if (softArrayHashMap == null) {
        myContinuationMap.put(key, softArrayHashMap = new ConcurrentSoftArrayHashMap<T, V>(myStrategy));
      }
      softArrayHashMap.put(array, index + 1, value);
    }
  }

  public final synchronized void put(T[] key, V value) {
    if (key.length == 0) {
      myEmptyValue = new SoftReference<V>(value);
    }
    else {
      put(key, 0, value);
    }
  }

  public final synchronized void clear() {
    myContinuationMap = null;
    myValuesMap = null;
    myEmptyValue = null;
  }

  public final boolean containsKey(final T[] path) {
    return get(path) != null;
  }

  @Override
  public final synchronized ConcurrentSoftArrayHashMap<T,V> clone() {
    final ConcurrentSoftArrayHashMap<T, V> copy = new ConcurrentSoftArrayHashMap<T, V>(myStrategy);
    copy.myContinuationMap = copyMap(myContinuationMap);
    copy.myValuesMap = copyMap(myValuesMap);
    copy.myEmptyValue = myEmptyValue;
    return copy;
  }

  private <X> ConcurrentSoftHashMap<T, X> copyMap(final ConcurrentSoftHashMap<T, X> map) {
    final ConcurrentSoftHashMap<T, X> copy = new ConcurrentSoftHashMap<T, X>();
    for (final Map.Entry<T, X> entry : map.entrySet()) {
      copy.put(entry.getKey(), entry.getValue());
    }
    return copy;
  }
}