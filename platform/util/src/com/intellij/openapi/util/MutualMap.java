// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MutualMap<Key, Value> {
  private final Map<Key, Value> myKey2Value;
  private final Map<Value, Key> myValue2Key;

  public MutualMap(boolean ordered) {
    if (ordered) {
      myKey2Value = new LinkedHashMap<>();
      myValue2Key = new LinkedHashMap<>();
    } else {
      myKey2Value = new HashMap<>();
      myValue2Key = new HashMap<>();
    }
  }

  public MutualMap() {
    this(false);
  }

  public void put(Key key, Value value) {
    myKey2Value.put(key, value);
    myValue2Key.put(value, key);
  }

  public Value getValue(Key key) {
    return myKey2Value.get(key);
  }

  public Key getKey(Value value) {
    return myValue2Key.get(value);
  }

  public int size() {
    return myValue2Key.size();
  }

  public boolean containsKey(final Key key) {
    return myKey2Value.containsKey(key);
  }

  public void remove(final Key key) {
    final Value value = myKey2Value.get(key);
    myKey2Value.remove(key);
    myValue2Key.remove(value);
  }

  public Collection<Value> getValues() {
    return myKey2Value.values();
  }

  public Collection<Key> getKeys() {
    return myKey2Value.keySet();
  }

  public void clear() {
    myKey2Value.clear();
    myValue2Key.clear();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MutualMap mutualMap = (MutualMap)o;
    return myKey2Value.equals(mutualMap.myKey2Value) && myValue2Key.equals(mutualMap.myValue2Key);
  }

  @Override
  public int hashCode() {
    return 31 * myKey2Value.hashCode() + myValue2Key.hashCode();
  }

  @Override
  public String toString() {
    return myKey2Value.toString();
  }
}
