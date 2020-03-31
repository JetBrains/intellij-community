/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.keyFMap;

import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static com.intellij.util.keyFMap.ArrayBackedFMap.getKeysByIndices;

final class MapBackedFMap extends TIntObjectHashMap<Object> implements KeyFMap {
  private MapBackedFMap(@NotNull MapBackedFMap oldMap, final int exclude) {
    super(oldMap.size());
    oldMap.forEachEntry(new TIntObjectProcedure<Object>() {
      @Override
      public boolean execute(int key, Object val) {
        if (key != exclude) put(key, val);
        assert key >= 0 : key;
        return true;
      }
    });
    assert size() > ArrayBackedFMap.ARRAY_THRESHOLD;
  }

  MapBackedFMap(int @NotNull [] keys, int newKey, Object @NotNull [] values, @NotNull Object newValue) {
    super(keys.length + 1);
    for (int i = 0; i < keys.length; i++) {
      int key = keys[i];
      Object value = values[i];
      put(key, value);
      assert key >= 0 : key;
    }
    put(newKey, newValue);
    assert newKey >= 0 : newKey;
    assert size() > ArrayBackedFMap.ARRAY_THRESHOLD;
  }

  @NotNull
  @Override
  public <V> KeyFMap plus(@NotNull Key<V> key, @NotNull V value) {
    int keyCode = key.hashCode();
    assert keyCode >= 0 : key;
    @SuppressWarnings("unchecked")
    V oldValue = (V)get(keyCode);
    if (value == oldValue) return this;
    MapBackedFMap newMap = new MapBackedFMap(this, -1);
    newMap.put(keyCode, value);
    return newMap;
  }

  @NotNull
  @Override
  public KeyFMap minus(@NotNull Key<?> key) {
    int oldSize = size();
    int keyCode = key.hashCode();
    if (!containsKey(keyCode)) {
      return this;
    }
    if (oldSize == ArrayBackedFMap.ARRAY_THRESHOLD + 1) {
      int[] keys = keys();
      keys = ArrayUtil.remove(keys, ArrayUtil.indexOf(keys, keyCode));
      Arrays.sort(keys);
      Object[] values = new Object[keys.length];
      for (int i = 0; i < keys.length; i++) {
        values[i] = get(keys[i]);
      }
      return new ArrayBackedFMap(keys, values);
    }
    return new MapBackedFMap(this, keyCode);
  }

  @Override
  public <V> V get(@NotNull Key<V> key) {
    //noinspection unchecked
    return (V)get(key.hashCode());
  }

  @Override
  public Key @NotNull [] getKeys() {
    return getKeysByIndices(keys());
  }

  @Override
  public int getValueIdentityHashCode() {
    final int[] hash = {0};
    forEachEntry(new TIntObjectProcedure<Object>() {
      @Override
      public boolean execute(int key, Object value) {
        hash[0] = (hash[0] * 31 + key) * 31 + System.identityHashCode(value);
        return true;
      }
    });
    return hash[0];
  }

  @Override
  public boolean equalsByReference(KeyFMap other) {
    if(other == this) return true;
    if (!(other instanceof MapBackedFMap) || other.size() != size()) return false;
    final MapBackedFMap map = (MapBackedFMap)other;
    return forEachEntry(new TIntObjectProcedure<Object>() {
      @Override
      public boolean execute(int key, Object value) {
        return map.get(key) == value;
      }
    });
  }

  @Override
  public String toString() {
    final StringBuilder s = new StringBuilder();
    forEachEntry(new TIntObjectProcedure<Object>() {
      @Override
      public boolean execute(int key, Object value) {
        s.append(s.length() == 0 ? "" : ", ").append(Key.getKeyByIndex(key)).append(" -> ").append(value);
        return true;
      }
    });
    return "[" + s.toString() + "]";
  }
}
