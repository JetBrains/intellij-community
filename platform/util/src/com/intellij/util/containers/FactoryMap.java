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

import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.util.ObjectUtils;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.NULL;

/**
 * @author peter
 */
public abstract class FactoryMap<K,V> implements Map<K, V> {
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("factoryMap");
  protected Map<K, V> myMap;

  protected Map<K, V> createMap() {
    return new THashMap<K, V>();
  }

  @Nullable
  protected abstract V create(K key);

  private Map<K, V> getMap() {
    if (myMap == null) {
      myMap = createMap();
    }
    return myMap;
  }
  
  @Override
  public V get(Object key) {
    final Map<K, V> map = getMap();
    V value = map.get(getKey(key));
    if (value == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      value = create((K)key);
      if (stamp.mayCacheNow()) {
        map.put((K)getKey(key), value == null ? (V)NULL : value);
      }
    }
    return value == NULL ? null : value;
  }

  private static <K> K getKey(final K key) {
    return key == null ? (K)NULL : key;
  }

  @Override
  public final boolean containsKey(Object key) {
    return myMap != null && myMap.containsKey(getKey(key));
  }

  @Override
  public V put(K key, V value) {
    V v = getMap().put(getKey(key), value == null ? (V)NULL : value);
    return v == NULL ? null : v;
  }

  @Override
  public V remove(Object key) {
    if (myMap == null) return null;
    V v = myMap.remove(key);
    return v == NULL ? null : v;
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    if (myMap == null) return Collections.emptySet();
    final Set<K> ts = myMap.keySet();
    //noinspection SuspiciousMethodCalls
    if (ts.contains(NULL)) {
      final HashSet<K> hashSet = new HashSet<K>(ts);
      //noinspection SuspiciousMethodCalls
      hashSet.remove(NULL);
      hashSet.add(null);
      return hashSet;
    }
    return ts;
  }

  public Collection<V> notNullValues() {
    if (myMap == null) return Collections.emptyList();
    final Collection<V> values = ContainerUtil.newArrayList(myMap.values());
    for (Iterator<V> iterator = values.iterator(); iterator.hasNext();) {
      if (iterator.next() == NULL) {
        iterator.remove();
      }
    }
    return values;
  }

  public boolean removeValue(Object value) {
    if (myMap == null) return false;
    Object t = ObjectUtils.notNull(value, NULL);
    //noinspection SuspiciousMethodCalls
    return myMap.values().remove(t);
  }


  @Override
  public void clear() {
    if (myMap != null) {
      myMap.clear();
    }
  }

  @Override
  public int size() {
    if (myMap == null) return 0;
    return myMap.size();
  }

  @Override
  public boolean isEmpty() {
    return myMap == null || myMap.isEmpty();
  }

  @Override
  public boolean containsValue(final Object value) {
    return myMap != null && myMap.containsValue(value);
  }

  @Override
  public void putAll(@NotNull final Map<? extends K, ? extends V> m) {
    for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  @NotNull
  @Override
  public Collection<V> values() {
    if (myMap == null) return Collections.emptyList();
    return myMap.values();
  }

  @NotNull
  @Override
  public Set<Entry<K, V>> entrySet() {
    if (myMap == null) return Collections.emptySet();
    return myMap.entrySet();
  }
}
