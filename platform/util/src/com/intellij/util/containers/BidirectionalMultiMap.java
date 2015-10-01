/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class BidirectionalMultiMap<K, V> {
  private final Map<K, Set<V>> myKey2Values;
  private final Map<V, Set<K>> myValue2Keys;

  public BidirectionalMultiMap() {
    this(new HashMap<K, Set<V>>(), new HashMap<V, Set<K>>());
  }

  public BidirectionalMultiMap(final Map<K, Set<V>> key2Values, final Map<V, Set<K>> value2Keys) {
    myKey2Values = key2Values;
    myValue2Keys = value2Keys;
  }

  @NotNull
  public Set<V> getValues(K key) {
    Set<V> set = myKey2Values.get(key);
    return set != null ? set : Collections.<V>emptySet();
  }

  @NotNull
  public Set<K> getKeys(V value) {
    Set<K> set = myValue2Keys.get(value);
    return set != null ? set : Collections.<K>emptySet();
  }

  public boolean containsKey(K key) {
    return myKey2Values.containsKey(key);
  }

  public boolean containsValue(V value) {
    return myValue2Keys.containsKey(value);
  }

  public boolean put(K key, V value) {
    Set<K> ks = myValue2Keys.get(value);
    if (ks == null) {
      ks = createKeysSet();
      myValue2Keys.put(value, ks);
    }
    ks.add(key);

    Set<V> vs = myKey2Values.get(key);
    if (vs == null) {
      vs = createValuesSet();
      myKey2Values.put(key, vs);
    }
    return vs.add(value);
  }

  @NotNull
  protected Set<V> createValuesSet() {
    return new HashSet<V>();
  }

  @NotNull
  protected Set<K> createKeysSet() {
    return new HashSet<K>();
  }

  public boolean removeKey(K key) {
    final Set<V> vs = myKey2Values.get(key);
    if (vs == null) return false;

    for (V v : vs) {
      final Set<K> ks = myValue2Keys.get(v);
      ks.remove(key);
      if (ks.isEmpty()) {
        myValue2Keys.remove(v);
      }
    }
    myKey2Values.remove(key);
    return true;
  }

  public void remove(K key, V value) {
    Set<V> vs = myKey2Values.get(key);
    Set<K> ks = myValue2Keys.get(value);
    if (ks != null && vs != null) {
      ks.remove(key);
      vs.remove(value);
      if (ks.isEmpty()) {
        myValue2Keys.remove(value);
      }
      if (vs.isEmpty()) {
        myKey2Values.remove(key);
      }
    }
  }

  public boolean isEmpty() {
    return myKey2Values.isEmpty() && myValue2Keys.isEmpty();
  }

  public boolean removeValue(V value) {
    final Set<K> ks = myValue2Keys.get(value);
    if (ks == null) return false;

    for (K k : ks) {
      final Set<V> vs = myKey2Values.get(k);
      vs.remove(value);
      if (vs.isEmpty()) {
        myKey2Values.remove(k);
      }
    }
    myValue2Keys.remove(value);
    return true;
  }

  public void clear() {
    myKey2Values.clear();
    myValue2Keys.clear();
  }

  public Set<K> getKeys() {
    return myKey2Values.keySet();
  }

  public Set<V> getValues() {
    return myValue2Keys.keySet();
  }
}
