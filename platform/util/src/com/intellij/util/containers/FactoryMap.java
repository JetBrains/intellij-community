/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public abstract class FactoryMap<K,V> implements Map<K, V> {
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("factoryMap");
  protected Map<K, V> myMap;

  @NotNull
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
    K k = notNull(key);
    V value = map.get(k);
    if (value == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      value = create((K)key);
      if (stamp.mayCacheNow()) {
        V v = notNull(value);
        map.put(k, v);
      }
    }
    return value == FAKE_NULL() ? null : value;
  }

  private static <T> T FAKE_NULL() {
    //noinspection unchecked
    return (T)ObjectUtils.NULL;
  }

  private static <T> T notNull(final Object key) {
    //noinspection unchecked
    return key == null ? FactoryMap.<T>FAKE_NULL() : (T)key;
  }

  @Override
  public final boolean containsKey(Object key) {
    return myMap != null && myMap.containsKey(notNull(key));
  }

  @Override
  public V put(K key, V value) {
    K k = notNull(key);
    V v = notNull(value);
    v = getMap().put(k, v);
    return v == FAKE_NULL() ? null : v;
  }

  @Override
  public V remove(Object key) {
    if (myMap == null) return null;
    V v = myMap.remove(key);
    return v == FAKE_NULL() ? null : v;
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    if (myMap == null) return Collections.emptySet();
    final Set<K> ts = myMap.keySet();
    K nullKey = FAKE_NULL();
    if (ts.contains(nullKey)) {
      final HashSet<K> hashSet = new HashSet<K>(ts);
      hashSet.remove(nullKey);
      hashSet.add(null);
      return hashSet;
    }
    return ts;
  }

  @NotNull
  public Collection<V> notNullValues() {
    if (myMap == null) return Collections.emptyList();
    final Collection<V> values = ContainerUtil.newArrayList(myMap.values());
    for (Iterator<V> iterator = values.iterator(); iterator.hasNext();) {
      if (iterator.next() == FAKE_NULL()) {
        iterator.remove();
      }
    }
    return values;
  }

  public boolean removeValue(Object value) {
    if (myMap == null) return false;
    Object t = ObjectUtils.notNull(value, FAKE_NULL());
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

  @NotNull
  public static <K, V> FactoryMap<K, V> createMap(@NotNull final Function<K, V> computeValue) {
    return new FactoryMap<K, V>() {
      @Nullable
      @Override
      protected V create(K key) {
        return computeValue.fun(key);
      }
    };
  }
}
