// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.utils;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.Collection;

@ApiStatus.Internal
public final class IntIntMultiMap {
  private final static int[] EMPTY = new int[0];
  private final Int2ObjectMap<int[]> myKeyToArrayMap = new Int2ObjectOpenHashMap<>();

  public void putValue(int key, int value) {
    int[] values = myKeyToArrayMap.get(key);
    int[] newValues;
    if (values == null) {
      newValues = new int[]{value};
    }
    else {
      newValues = new int[values.length + 1];
      for (int i = 0; i < values.length; i++) {
        if (values[i] == value) return;
        newValues[i] = values[i];
      }
      newValues[newValues.length - 1] = value;
    }
    myKeyToArrayMap.put(key, newValues);
  }

  public void remove(int key, int value) {
    int removeIndex = -1;
    int[] values = myKeyToArrayMap.get(key);
    if (values == null) return;
    for (int i = 0; i < values.length; i++) {
      if (values[i] == value) {
        removeIndex = i;
        break;
      }
    }
    if (removeIndex == -1) return;

    if (values.length == 1) {
      myKeyToArrayMap.remove(key);
      return;
    }

    int[] newValues = new int[values.length - 1];
    for (int i = 0; i < newValues.length; i++) {
      if (i >= removeIndex) {
        newValues[i] = values[i + 1];
      }
      else {
        newValues[i] = values[i];
      }
    }
    myKeyToArrayMap.put(key, newValues);
  }

  @NotNull
  public Collection<Integer> get(int key) {
    final int[] asArray = getAsArray(key);
    return new AbstractList<>() {
      @NotNull
      @Override
      public Integer get(int index) {
        return asArray[index];
      }

      @Override
      public int size() {
        return asArray.length;
      }
    };
  }

  public int @NotNull [] getAsArray(int key) {
    int[] result = myKeyToArrayMap.get(key);
    if (result == null) {
      return EMPTY;
    }
    else {
      return result;
    }
  }

  public boolean isEmpty() {
    return myKeyToArrayMap.isEmpty();
  }

  public boolean containsKey(int key) {
    return myKeyToArrayMap.containsKey(key);
  }

  public int @NotNull [] keys() {
    return myKeyToArrayMap.keySet().toIntArray();
  }

  public void clear() {
    myKeyToArrayMap.clear();
  }
}
