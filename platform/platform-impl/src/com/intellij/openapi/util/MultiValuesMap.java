// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @deprecated use {@link MultiMap} directly.
 * <p></p>On migration please note that MultiMap has few differences:<ul>
 * <li>{@link MultiMap#get(Object)} method returns non-null value. In case there is no value for the key - empty collection is returned.</li>
 * <li>{@link MultiMap#values} method returns a real values collection, not a copy. Be careful with modifications.</li>
 * </ul></p>
 */
@Debug.Renderer(text = "\"size = \" + myDelegate.size()", hasChildren = "!isEmpty()", childrenArray = "entrySet().toArray()")
@Deprecated
public class MultiValuesMap<K, V>{
  private final MultiMap<K, V> myDelegate;
  private final boolean myOrdered;

  /**
   * @deprecated Use {@link MultiMap#createSet()}
   */
  @Deprecated(forRemoval = true)
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
        @Override
        protected @NotNull Collection<V> createCollection() {
          return new HashSet<>();
        }

        @Override
        protected @NotNull Collection<V> createEmptyCollection() {
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

  public @Nullable Collection<V> get(K key){
    Collection<V> collection = myDelegate.get(key);
    return collection.isEmpty() ? null : collection;
  }

  public @NotNull Set<K> keySet() {
    return myDelegate.keySet();
  }

  public @NotNull Collection<V> values() {
    return myOrdered ? new LinkedHashSet<>(myDelegate.values()) : new HashSet<>(myDelegate.values());
  }

  public void remove(K key, V value) {
    myDelegate.remove(key, value);
  }

  public void clear() {
    myDelegate.clear();
  }

  public @Nullable Collection<V> removeAll(final K key) {
    return myDelegate.remove(key);
  }

  public @NotNull Set<Map.Entry<K, Collection<V>>> entrySet() {
    return myDelegate.entrySet();
  }

  public boolean isEmpty() {
    return myDelegate.isEmpty();
  }

  public boolean containsKey(final K key) {
    return myDelegate.containsKey(key);
  }

  public @Nullable V getFirst(final K key) {
    Collection<V> values = myDelegate.get(key);
    return values.isEmpty() ? null : values.iterator().next();
  }
}
