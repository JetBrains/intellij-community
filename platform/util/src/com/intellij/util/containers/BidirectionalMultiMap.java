// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @see MultiMap
 */
public final class BidirectionalMultiMap<K, V> {
  private final Map<K, Set<V>> keyToValues;
  private final Map<V, Set<K>> valueToKeys;

  public BidirectionalMultiMap() {
    this(new HashMap<>(), new HashMap<>());
  }

  public BidirectionalMultiMap(@NotNull Map<K, Set<V>> key2Values, @NotNull Map<V, Set<K>> value2Keys) {
    keyToValues = key2Values;
    valueToKeys = value2Keys;
  }

  public @NotNull Set<V> getValues(K key) {
    Set<V> set = keyToValues.get(key);
    return set == null ? Collections.emptySet() : set;
  }

  public @NotNull Set<K> getKeys(V value) {
    Set<K> set = valueToKeys.get(value);
    return set == null ? Collections.emptySet() : set;
  }

  public boolean containsKey(K key) {
    return keyToValues.containsKey(key);
  }

  public boolean containsValue(V value) {
    return valueToKeys.containsKey(value);
  }

  public boolean put(K key, V value) {
    Set<K> keys = valueToKeys.get(value);
    if (keys == null) {
      keys = createKeysSet();
      valueToKeys.put(value, keys);
    }
    keys.add(key);

    Set<V> values = keyToValues.get(key);
    if (values == null) {
      values = createValuesSet();
      keyToValues.put(key, values);
    }
    return values.add(value);
  }

  private @NotNull Set<V> createValuesSet() {
    return new HashSet<>();
  }

  private @NotNull Set<K> createKeysSet() {
    return new HashSet<>();
  }

  public boolean removeKey(K key) {
    Set<V> values = keyToValues.get(key);
    if (values == null) {
      return false;
    }

    for (V v : values) {
      Set<K> keys = valueToKeys.get(v);
      keys.remove(key);
      if (keys.isEmpty()) {
        valueToKeys.remove(v);
      }
    }
    keyToValues.remove(key);
    return true;
  }

  public void remove(K key, V value) {
    Set<V> values = keyToValues.get(key);
    Set<K> keys = valueToKeys.get(value);
    if (keys != null && values != null) {
      keys.remove(key);
      values.remove(value);
      if (keys.isEmpty()) {
        valueToKeys.remove(value);
      }
      if (values.isEmpty()) {
        keyToValues.remove(key);
      }
    }
  }

  public boolean isEmpty() {
    return keyToValues.isEmpty() && valueToKeys.isEmpty();
  }

  public boolean removeValue(V value) {
    Set<K> keys = valueToKeys.get(value);
    if (keys == null) {
      return false;
    }

    for (K k : keys) {
      Set<V> values = keyToValues.get(k);
      values.remove(value);
      if (values.isEmpty()) {
        keyToValues.remove(k);
      }
    }
    valueToKeys.remove(value);
    return true;
  }

  public void clear() {
    keyToValues.clear();
    valueToKeys.clear();
  }

  public Set<K> getKeys() {
    return keyToValues.keySet();
  }

  public Set<V> getValues() {
    return valueToKeys.keySet();
  }
}
