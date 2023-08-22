// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.BiPredicate;

public final class SoftArrayHashMap<T,V> implements Cloneable {
  private Map<T, SoftArrayHashMap<T,V>> myContinuationMap;
  private Map<T,V> myValuesMap;
  private V myEmptyValue;
  private final HashingStrategy<T> myStrategy;

  public SoftArrayHashMap() {
    this(HashingStrategy.canonical());
  }

  private SoftArrayHashMap(@NotNull HashingStrategy<T> strategy) {
    myStrategy = strategy;
  }

  private V get(T[] array, int index) {
    if (index == array.length - 1) {
      return myValuesMap != null ? myValuesMap.get(array[index]) : null;
    }

    if (myContinuationMap != null) {
      final SoftArrayHashMap<T, V> map = myContinuationMap.get(array[index]);
      if (map != null) {
        return map.get(array, index + 1);
      }
    }

    return null;
  }

  public V get(T[] key) {
    if (key.length == 0) {
      return myEmptyValue;
    }
    return get(key, 0);
  }

  public boolean processLeafEntries(@NotNull BiPredicate<? super T, ? super V> processor) {
    if (myValuesMap != null) {
      for (T t : myValuesMap.keySet()) {
        if (!processor.test(t, myValuesMap.get(t))) {
          return false;
        }
      }
    }
    if (myContinuationMap != null) {
      for (SoftArrayHashMap<T, V> map : myContinuationMap.values()) {
        if (!map.processLeafEntries(processor)) return false;
      }
    }
    return true;
  }

  private void put(T[] array, int index, V value) {
    final T key = array[index];
    if (index == array.length - 1) {
      if (myValuesMap == null) {
        //noinspection deprecation
        myValuesMap = new SoftHashMap<>(myStrategy);
      }
      myValuesMap.put(key, value);
    } else {
      if (myContinuationMap == null) {
        //noinspection deprecation
        myContinuationMap = new SoftHashMap<>(myStrategy);
      }
      SoftArrayHashMap<T, V> softArrayHashMap = myContinuationMap.get(key);
      if (softArrayHashMap == null) {
        myContinuationMap.put(key, softArrayHashMap = new SoftArrayHashMap<>(myStrategy));
      }
      softArrayHashMap.put(array, index + 1, value);
    }
  }

  public void put(T[] key, V value) {
    if (key.length == 0) {
      myEmptyValue = value;
    } else {
      put(key, 0, value);
    }
  }

  public void clear() {
    myContinuationMap = null;
    myValuesMap = null;
    myEmptyValue = null;
  }

  public boolean containsKey(final T[] path) {
    return get(path) != null;
  }

  @Override
  public SoftArrayHashMap<T,V> clone() {
    final SoftArrayHashMap<T, V> copy = new SoftArrayHashMap<>(myStrategy);
    copy.myContinuationMap = copyMap(myContinuationMap);
    copy.myValuesMap = copyMap(myValuesMap);
    copy.myEmptyValue = myEmptyValue;
    return copy;
  }

  private <X> Map<T, X> copyMap(final Map<T, X> map) {
    //noinspection deprecation
    Map<T, X> copy = new SoftHashMap<>(map.size());
    copy.putAll(map);
    return copy;
  }
}
