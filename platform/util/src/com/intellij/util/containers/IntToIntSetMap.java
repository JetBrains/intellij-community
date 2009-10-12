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

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import com.intellij.util.ArrayUtil;

/**
 * @author max
 */
public class IntToIntSetMap {
  private final TIntIntHashMap mySingle;
  private final TIntObjectHashMap<TIntHashSet> myMulti;


  public IntToIntSetMap(int initialCapacity, float loadfactor) {
    mySingle = new TIntIntHashMap(initialCapacity, loadfactor);
    myMulti = new TIntObjectHashMap<TIntHashSet>(initialCapacity, loadfactor);
  }

  public void addOccurence(int key, int value) {
    if (mySingle.containsKey(key)) {
      int old = mySingle.get(key);
      TIntHashSet items = new TIntHashSet(3);
      items.add(old);
      items.add(value);
      mySingle.remove(key);
      myMulti.put(key, items);
      return;
    }
    final TIntHashSet items = myMulti.get(key);
    if (items != null) {
      items.add(value);
      return;
    }
    mySingle.put(key, value);
  }

  public void removeOccurence(int key, int value) {
    if (mySingle.containsKey(key)) {
      mySingle.remove(key);
      return;
    }
    TIntHashSet items = myMulti.get(key);
    if (items != null) {
      items.remove(value);
      if (items.size() == 1) {
        mySingle.put(key, items.toArray()[0]);
        myMulti.remove(key);
      }
    }
  }

  public int[] get(int key) {
    if (mySingle.containsKey(key)) {
      return new int[]{mySingle.get(key)};
    }
    TIntHashSet items = myMulti.get(key);
    if (items == null) return ArrayUtil.EMPTY_INT_ARRAY;
    return items.toArray();
  }
}
