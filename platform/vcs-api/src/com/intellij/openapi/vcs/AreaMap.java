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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AreaMap<Key extends Comparable<Key>, Val> {
  private final List<Key> myKeys;
  private final Map<Key, Val> myMap;
  // [admittedly] parent, [-"-] child
  private final PairProcessor<Key, Key> myKeysResemblance;

  public AreaMap(final PairProcessor<Key, Key> keysResemblance) {
    myKeysResemblance = keysResemblance;
    myKeys = new LinkedList<Key>();
    myMap = new HashMap<Key, Val>();
  }

  public void put(final Key key, final Val val) {
    myMap.put(key, val);

    if (myKeys.isEmpty()) {
      myKeys.add(key);
      return;
    }

    final int idx = Collections.binarySearch(myKeys, key);
    if (idx < 0) {
      // insertion, no copy exist
      final int insertionIdx = - idx - 1;
      // insertionIdx not necessarily exist
      myKeys.add(insertionIdx, key);
    }
  }

  @Nullable
  public Val getExact(final Key key) {
    return myMap.get(key);
  }

  public void removeByValue(@NotNull final Val val) {
    for (Iterator<Key> iterator = myKeys.iterator(); iterator.hasNext();) {
      final Key key = iterator.next();
      final Val current = myMap.get(key);
      if (val.equals(current)) {
        iterator.remove();
        myMap.remove(key);
        return;
      }
    }
  }

  public void getSimiliar(final Key key, final PairProcessor<Key, Val> consumer) {
    final int idx = Collections.binarySearch(myKeys, key);
    if (idx < 0) {
      final int insertionIdx = - idx - 1;
      // take item before
      final int itemBeforeIdx = insertionIdx - 1;
      if (itemBeforeIdx >= 0) {
        for (int i = itemBeforeIdx; i >= 0; -- i) {
          final Key candidate = myKeys.get(i);
          if (! myKeysResemblance.process(candidate, key)) break;
          if (consumer.process(candidate, myMap.get(candidate))) break;     // if need only a part of keys
        }
      }
    } else {
      consumer.process(key, myMap.get(key));
    }
  }
  
  public boolean isEmpty() {
    return myKeys.isEmpty();
  }
}
