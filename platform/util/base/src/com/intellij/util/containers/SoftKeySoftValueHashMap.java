// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.*;

final class SoftKeySoftValueHashMap<K,V> implements Map<K,V>, ReferenceQueueable {
  private final RefHashMap<K, ValueReference<K,V>> mySoftKeyMap = new SoftHashMap<>(4);
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<>();

  SoftKeySoftValueHashMap() {
  }

  private static final class ValueReference<K,V> extends SoftReference<V> {
    private final RefHashMap.Key<K> key;

    private ValueReference(RefHashMap.Key<K> key, V referent, ReferenceQueue<? super V> q) {
      super(referent, q);
      this.key = key;
    }
  }

  // returns true if some refs were tossed
  @Override
  public boolean processQueue() {
    boolean processed = mySoftKeyMap.processQueue();
    while(true) {
      //noinspection unchecked
      ValueReference<K,V> ref = (ValueReference<K, V>)myQueue.poll();
      if (ref == null) break;
      RefHashMap.Key<K> key = ref.key;
      mySoftKeyMap.removeKey(key);
      processed = true;
    }
    return processed;
  }

  @Override
  public V get(Object key) {
    ValueReference<K,V> ref = mySoftKeyMap.get(key);
    return com.intellij.reference.SoftReference.dereference(ref);
  }

  @Override
  public V put(K key, V value) {
    processQueue();
    RefHashMap.Key<K> softKey = mySoftKeyMap.createKey(key);
    ValueReference<K, V> reference = new ValueReference<>(softKey, value, myQueue);
    ValueReference<K,V> oldRef = mySoftKeyMap.putKey(softKey, reference);
    return com.intellij.reference.SoftReference.dereference(oldRef);
  }

  @Override
  public V remove(Object key) {
    processQueue();
    ValueReference<K,V> ref = mySoftKeyMap.remove(key);
    return com.intellij.reference.SoftReference.dereference(ref);
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> t) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    mySoftKeyMap.clear();
    processQueue();
  }

  @Override
  public int size() {
    return mySoftKeyMap.size(); //?
  }

  @Override
  public boolean isEmpty() {
    return mySoftKeyMap.isEmpty(); //?
  }

  @Override
  public boolean containsKey(Object key) {
    throw RefValueHashMapUtil.pointlessContainsKey();
  }

  @Override
  public boolean containsValue(Object value) {
    throw RefValueHashMapUtil.pointlessContainsValue();
  }

  @Override
  public @NotNull Set<K> keySet() {
    return mySoftKeyMap.keySet();
  }

  @Override
  public @NotNull Collection<V> values() {
    List<V> result = new ArrayList<>();
    Collection<ValueReference<K, V>> refs = mySoftKeyMap.values();
    for (ValueReference<K, V> ref : refs) {
      V value = ref.get();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @Override
  public @NotNull Set<Entry<K, V>> entrySet() {
    throw new UnsupportedOperationException();
  }
}
