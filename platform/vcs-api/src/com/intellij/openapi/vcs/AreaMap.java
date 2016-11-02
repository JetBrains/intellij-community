/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.util.PairProcessor;

import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import static com.intellij.util.containers.ContainerUtil.newHashMap;
import static com.intellij.util.containers.ContainerUtil.newLinkedList;
import static java.util.Collections.binarySearch;

public class AreaMap<Key extends Comparable<Key>, Val> {
  private final List<Key> myKeys = newLinkedList();
  private final Map<Key, Val> myMap = newHashMap();

  public void put(final Key key, final Val val) {
    myMap.put(key, val);

    if (myKeys.isEmpty()) {
      myKeys.add(key);
    }
    else {
      int idx = binarySearch(myKeys, key);
      if (idx < 0) {
        int insertionIdx = -idx - 1;
        myKeys.add(insertionIdx, key);
      }
    }
  }

  public void getSimiliar(Key key, PairProcessor<Key, Key> keysResemblance, PairProcessor<Key, Val> consumer) {
    int idx = binarySearch(myKeys, key);
    if (idx < 0) {
      int insertionIdx = -idx - 1;
      if (insertionIdx - 1 >= 0) {
        for (ListIterator<Key> iterator = myKeys.listIterator(insertionIdx); iterator.hasPrevious(); ) {
          Key candidate = iterator.previous();
          if (!keysResemblance.process(candidate, key)) continue;
          if (consumer.process(candidate, myMap.get(candidate))) break;     // if need only a part of keys
        }
      }
    } else {
      consumer.process(key, myMap.get(key));
    }
  }
}
