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
import com.intellij.util.Producer;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * a Map which computes the value associated with the key (via {@link #create(Object)} method) on first {@link #get(Object)} access.
 * NOT THREAD SAFE.
 * For thread-safe alternative please use {@link ConcurrentFactoryMap}
 */
public abstract class FactoryMap<K,V> implements Map<K, V> {
  private static final RecursionGuard ourGuard = RecursionManager.createGuard("factoryMap");

  private Map<K, V> myMap;

  /**
   * Use {@link #createMap(Function)} instead
   */
  @Deprecated
  public FactoryMap() {
  }

  @NotNull
  protected Map<K, V> createMap() {
    return new THashMap<K, V>();
  }

  @Nullable
  protected abstract V create(K key);

  @Override
  public V get(Object key) {
    Map<K, V> map = getMap();
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

  private Map<K, V> getMap() {
    Map<K, V> map = myMap;
    if (map == null) {
      myMap = map = createMap();
    }
    return map;
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
    return getMap().containsKey(notNull(key));
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
    V v = getMap().remove(key);
    return v == FAKE_NULL() ? null : v;
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    final Set<K> ts = getMap().keySet();
    K nullKey = FAKE_NULL();
    if (ts.contains(nullKey)) {
      final HashSet<K> hashSet = new HashSet<K>(ts);
      hashSet.remove(nullKey);
      hashSet.add(null);
      return hashSet;
    }
    return ts;
  }

  public boolean removeValue(Object value) {
    Object t = ObjectUtils.notNull(value, FAKE_NULL());
    //noinspection SuspiciousMethodCalls
    return getMap().values().remove(t);
  }


  @Override
  public void clear() {
    getMap().clear();
  }

  @Override
  public int size() {
    return getMap().size();
  }

  @Override
  public boolean isEmpty() {
    return getMap().isEmpty();
  }

  @Override
  public boolean containsValue(final Object value) {
    return getMap().containsValue(value);
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
    return ContainerUtil.map(getMap().values(), new Function<V, V>() {
      @Override
      public V fun(V v) {
        return v == FAKE_NULL() ? null : v;
      }
    });
  }

  @NotNull
  @Override
  public Set<Entry<K, V>> entrySet() {
    return getMap().entrySet();
  }

  @NotNull
  public static <K, V> Map<K, V> createMap(@NotNull final Function<K, V> computeValue) {
    //noinspection deprecation
    return new FactoryMap<K, V>() {
      @Nullable
      @Override
      protected V create(K key) {
        return computeValue.fun(key);
      }
    };
  }

  @NotNull
  public static <K, V> Map<K, V> createMap(@NotNull final Function<K, V> computeValue, @NotNull final Producer<Map<K,V>> mapCreator) {
    //noinspection deprecation
    return new FactoryMap<K, V>() {
      @Nullable
      @Override
      protected V create(K key) {
        return computeValue.fun(key);
      }

      @NotNull
      @Override
      protected Map<K, V> createMap() {
        return mapCreator.produce();
      }
    };
  }
}
