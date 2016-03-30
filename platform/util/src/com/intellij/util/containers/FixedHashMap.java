/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.util.*;

public class FixedHashMap<K, V> extends java.util.HashMap<K, V> {
  private final int mySize;
  private final List<K> myKeys = new LinkedList<K>();

  public FixedHashMap(int size) {
    mySize = size;
  }

  @Override
  public V put(K key, V value) {
    if (!myKeys.contains(key)) {
      if (myKeys.size() >= mySize) {
        remove(myKeys.remove(0));
      }
      myKeys.add(key);
    }
    return super.put(key, value);
  }

  @Override
  public V get(Object key) {
    if (myKeys.contains(key)) {
      int index = myKeys.indexOf(key);
      int last = myKeys.size() - 1;
      myKeys.set(index, myKeys.get(last));
      myKeys.set(last, (K)key);
    }
    return super.get(key);
  }
}