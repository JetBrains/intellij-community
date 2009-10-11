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

package com.intellij.openapi.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.LinkedHashMap;

public class MutualMap<Key, Value> {

  private final Map<Key, Value> myKey2Value;
  private final Map<Value, Key> myValue2Key;

  public MutualMap(boolean ordered) {
    if (ordered) {
      myKey2Value = new LinkedHashMap<Key, Value>();
      myValue2Key = new LinkedHashMap<Value, Key>();
    } else {
      myKey2Value = new HashMap<Key, Value>();
      myValue2Key = new HashMap<Value, Key>();
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
