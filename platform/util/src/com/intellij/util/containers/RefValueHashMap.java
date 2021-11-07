// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.util.HashMap;
import java.util.*;
import java.util.function.Supplier;

@Debug.Renderer(text = "\"size = \" + size()", hasChildren = "!isEmpty()", childrenArray = "childrenArray()")
abstract class RefValueHashMap<K, V> implements Map<K, V> {
  private final Map<K, MyReference<K, V>> myMap;
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<>();

  protected interface MyReference<K,T> extends Supplier<T> {
    @NotNull
    K getKey();
  }

  RefValueHashMap() {
    myMap = new HashMap<>();
  }

  protected abstract MyReference<K,V> createReference(@NotNull K key, V value, @NotNull ReferenceQueue<? super V> queue);

  private void processQueue() {
    while (true) {
      //noinspection unchecked
      MyReference<K,V> ref = (MyReference<K,V>)myQueue.poll();
      if (ref == null) {
        return;
      }
      K key = ref.getKey();
      if (myMap.get(key) == ref) {
        myMap.remove(key);
      }
    }
  }

  @Override
  public V get(@NotNull Object key) {
    MyReference<K,V> ref = myMap.get(key);
    return SoftReference.deref(ref);
  }

  @Override
  public V put(@NotNull K key, V value) {
    processQueue();
    MyReference<K, V> reference = createReference(key, value, myQueue);
    MyReference<K,V> oldRef = myMap.put(key, reference);
    return SoftReference.deref(oldRef);
  }

  @Override
  public V remove(@NotNull Object key) {
    processQueue();
    MyReference<K,V> ref = myMap.remove(key);
    return SoftReference.deref(ref);
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> t) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    myMap.clear();
  }

  @Override
  public int size() {
    return myMap.size(); //?
  }

  @Override
  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    throw RefValueHashMapUtil.pointlessContainsKey();
  }

  @Override
  public boolean containsValue(Object value) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    return myMap.keySet();
  }

  @NotNull
  @Override
  public Collection<V> values() {
    List<V> result = new ArrayList<>();
    final Collection<MyReference<K, V>> refs = myMap.values();
    for (MyReference<K, V> ref : refs) {
      final V value = ref.get();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @NotNull
  @Override
  public Set<Entry<K, V>> entrySet() {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unused")
  // used in debugger renderer
  private Map.Entry<K,V>[] childrenArray() {
    //noinspection unchecked
    return myMap.entrySet().stream()
      .map(entry -> {
        Object val = SoftReference.deref(entry.getValue());
        return val != null ? new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), val) : null;
      })
      .filter(Objects::nonNull)
      .toArray(Entry[]::new);
  }
}
