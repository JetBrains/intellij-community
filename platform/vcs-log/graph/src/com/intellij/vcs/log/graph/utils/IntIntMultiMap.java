/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.utils;

import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.Collection;

public class IntIntMultiMap {

  private final static int[] EMPTY = new int[0];
  private final TIntObjectHashMap<int[]> myKeyToArrayMap = new TIntObjectHashMap<>();


  public void putValue(int key, int value) {
    int[] values = myKeyToArrayMap.get(key);
    if (values == null) {
      int[] newValues = {value};
      myKeyToArrayMap.put(key, newValues);
    }
    else {
      int[] newValues = new int[values.length + 1];
      for (int i = 0; i < values.length; i++) {
        if (values[i] == value) return;
        newValues[i] = values[i];
      }
      newValues[newValues.length - 1] = value;
      myKeyToArrayMap.put(key, newValues);
    }
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
    return new AbstractList<Integer>() {
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

  @NotNull
  public int[] getAsArray(int key) {
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

  @NotNull
  public int[] keys() {
    return myKeyToArrayMap.keys();
  }

  public void clear() {
    myKeyToArrayMap.clear();
  }
}
