// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.containers;

import java.util.concurrent.ConcurrentMap;

import static com.intellij.util.ObjectUtils.NULL;

/**
 * @author peter
 */
public abstract class SoftFactoryMap<T,V> {
  private final ConcurrentMap<T, V> myMap = ContainerUtil.createConcurrentWeakKeySoftValueMap();

  protected abstract V create(T key);

  public final V get(T key) {
    final V v = myMap.get(key);
    if (v != null) {
      return v == NULL ? null : v;
    }

    final V value = create(key);
    @SuppressWarnings("unchecked")
    V toPut = value == null ? (V)NULL : value;
    V prev = myMap.putIfAbsent(key, toPut);
    return prev == null || prev == NULL ? value : prev;
  }

  public void clear() {
    myMap.clear();
  }
}