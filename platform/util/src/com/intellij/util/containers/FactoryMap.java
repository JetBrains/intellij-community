// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.util.DeprecatedMethodException;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.*;
import java.util.function.Supplier;

/**
 * Map which computes the value associated with the key (via {@link #create(Object)} method) on first {@link #get(Object)} access.
 * This map is NOT THREAD SAFE.
 * For the thread-safe alternative please use {@link ConcurrentFactoryMap} instead.
 */
public abstract class FactoryMap<K,V> implements Map<K, V> {
  private Map<K, V> myMap;

  /**
   * @deprecated Use {@link #create(Function)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public FactoryMap() {
    DeprecatedMethodException.report("Use FactoryMap.create*() instead");
  }

  private FactoryMap(boolean safe) {
  }

  @NotNull
  protected Map<K, V> createMap() {
    return new HashMap<>();
  }

  @Nullable
  protected abstract V create(K key);

  @Override
  public V get(Object key) {
    Map<K, V> map = getMap();
    K k = notNull(key);
    V value = map.get(k);
    if (value == null) {
      RecursionGuard.StackStamp stamp = RecursionManager.markStack();
      //noinspection unchecked
      value = create((K)key);
      if (stamp.mayCacheNow()) {
        V v = notNull(value);
        map.put(k, v);
      }
    }
    return nullize(value);
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
    return key == null ? FAKE_NULL() : (T)key;
  }
  @Nullable
  private static <T> T nullize(T value) {
    return value == FAKE_NULL() ? null : value;
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
    return nullize(v);
  }

  @Override
  public V remove(Object key) {
    V v = getMap().remove(key);
    return nullize(v);
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    final Set<K> ts = getMap().keySet();
    K nullKey = FAKE_NULL();
    if (ts.contains(nullKey)) {
      Set<K> hashSet = new HashSet<>(ts);
      hashSet.remove(nullKey);
      hashSet.add(null);
      return hashSet;
    }
    return ts;
  }

  public boolean removeValue(Object value) {
    Object t = notNull(value);
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
    return ContainerUtil.map(getMap().values(), FactoryMap::nullize);
  }

  @NotNull
  @Override
  public Set<Entry<K, V>> entrySet() {
    return ContainerUtil.map2Set(getMap().entrySet(),
                                 entry -> new AbstractMap.SimpleEntry<>(nullize(entry.getKey()), nullize(entry.getValue())));
  }

  @Override
  public String toString() {
    return String.valueOf(myMap);
  }

  @NotNull
  public static <K, V> Map<K, V> create(@NotNull final Function<? super K, ? extends V> computeValue) {
    return new FactoryMap<K, V>(true) {
      @Nullable
      @Override
      protected V create(K key) {
        return computeValue.fun(key);
      }
    };
  }

  @NotNull
  public static <K, V> Map<K, V> createMap(@NotNull final Function<? super K, ? extends V> computeValue, @NotNull final Supplier<? extends Map<K, V>> mapCreator) {
    return new FactoryMap<K, V>(true) {
      @Nullable
      @Override
      protected V create(K key) {
        return computeValue.fun(key);
      }

      @NotNull
      @Override
      protected Map<K, V> createMap() {
        return mapCreator.get();
      }
    };
  }
}
