// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @deprecated use {@link MultiMap} directly.
 * <p></p>On migration please note that MultiMap has few differences:<ul>
 * <li>{@link MultiMap#get(Object)} method returns non-null value. In case there is no value for the key - empty collection is returned.</li>
 * <li>{@link MultiMap#values} method returns a real values collection, not a copy. Be careful with modifications.</li>
 * <li>Default implementations of {@link MultiMap} may not permit null keys and/or null values</li>
 * </ul></p>
 */
@Debug.Renderer(text = "\"size = \" + myDelegate.size()", hasChildren = "!isEmpty()", childrenArray = "entrySet().toArray()")
@Deprecated
@ApiStatus.ScheduledForRemoval
public class MultiValuesMap<K, V>{
  private final MultiMap<K, V> myDelegate;
  private final boolean myOrdered;

  /**
   * @deprecated Use {@link MultiMap#createSet()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public MultiValuesMap() {
    this(false);
  }

  public MultiValuesMap(boolean ordered) {
    myOrdered = ordered;
    if (ordered) {
      myDelegate = MultiMap.createLinkedSet();
    }
    else {
      myDelegate = new MultiMap<K, V>() {
        @NotNull
        @Override
        protected Collection<V> createCollection() {
          return new HashSet<>();
        }

        @NotNull
        @Override
        protected Collection<V> createEmptyCollection() {
          return Collections.emptySet();
        }
      };
    }
  }

  public void putAll(K key, @NotNull Collection<? extends V> values) {
    for (V value : values) {
      put(key, value);
    }
  }

  public void putAll(K key, V @NotNull ... values) {
    for (V value : values) {
      put(key, value);
    }
  }

  public void put(K key, V value) {
    myDelegate.putValue(key, value);
  }

  @Nullable
  public Collection<V> get(K key){
    Collection<V> collection = myDelegate.get(key);
    return collection.isEmpty() ? null : collection;
  }

  @NotNull
  public Set<K> keySet() {
    return myDelegate.keySet();
  }

  @NotNull
  public Collection<V> values() {
    return myOrdered ? new LinkedHashSet<>(myDelegate.values()) : new HashSet<>(myDelegate.values());
  }

  public void remove(K key, V value) {
    myDelegate.remove(key, value);
  }

  public void clear() {
    myDelegate.clear();
  }

  @Nullable
  public Collection<V> removeAll(final K key) {
    return myDelegate.remove(key);
  }

  @NotNull
  public Set<Map.Entry<K, Collection<V>>> entrySet() {
    return myDelegate.entrySet();
  }

  public boolean isEmpty() {
    return myDelegate.isEmpty();
  }

  public boolean containsKey(final K key) {
    return myDelegate.containsKey(key);
  }

  @Nullable
  public V getFirst(final K key) {
    Collection<V> values = myDelegate.get(key);
    return values.isEmpty() ? null : values.iterator().next();
  }
}
