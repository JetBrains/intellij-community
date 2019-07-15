// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.containers;

import com.intellij.util.ArrayUtilRt;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;

/**
 * @author max
 */
public class IntToIntSetMap {
  private final TIntIntHashMap mySingle;
  private final TIntObjectHashMap<TIntHashSet> myMulti;


  public IntToIntSetMap(int initialCapacity, float loadfactor) {
    mySingle = new TIntIntHashMap(initialCapacity, loadfactor);
    myMulti = new TIntObjectHashMap<>(initialCapacity, loadfactor);
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
    if (items == null) return ArrayUtilRt.EMPTY_INT_ARRAY;
    return items.toArray();
  }
}
