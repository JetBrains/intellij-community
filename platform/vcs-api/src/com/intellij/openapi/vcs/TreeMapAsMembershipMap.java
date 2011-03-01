/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.Pair;
import com.intellij.util.PairProcessor;

import java.util.*;

/**
 * @author irengrig
 *         Date: 2/11/11
 *         Time: 7:36 PM
 */
public class TreeMapAsMembershipMap<Key, Val> implements MembershipMapI<Key, Val> {
  private final TreeMap<Key, Val> myMap;
  private final PairProcessor<Key, Key> myKeysResemblance;

  public TreeMapAsMembershipMap(final PairProcessor<Key, Key> keysResemblance, final Comparator<Key> comparator) {
    myKeysResemblance = keysResemblance;
    myMap = new TreeMap<Key, Val>(comparator);
  }

  @Override
  public void putOptimal(Key key, Val val) {
    if (getMapping(key) == null) {
      myMap.put(key, val);
      final SortedMap<Key, Val> tailMap = myMap.tailMap(key, false);
      if (tailMap != null) {
        final Iterator<Key> iterator = tailMap.keySet().iterator();
        while (iterator.hasNext()) {
          final Key next = iterator.next();
          if (myKeysResemblance.process(key, next)) {
            iterator.remove();
          } else {
            return;
          }
        }
      }
    }
  }

  @Override
  public void optimizeMap(PairProcessor<Val, Val> valuesAreas) {
    final Iterator<Key> childIterator = myMap.keySet().iterator();
    while (childIterator.hasNext()) {
      final Key key = childIterator.next();
      final Val value = myMap.get(key);

      // go for parents
      final NavigableMap<Key, Val> parentsMap = myMap.headMap(key, false);
      final Iterator<Key> parentIterator = parentsMap.descendingKeySet().iterator();
      while (parentIterator.hasNext()) {
        final Key innerKey = parentIterator.next();
        if (myKeysResemblance.process(innerKey, key)) {
          if (valuesAreas.process(myMap.get(innerKey), value)) {
            childIterator.remove();
          }
          // otherwise we found a "parent", and do not remove the child
          break;
        }
      }
    }
  }

  @Override
  public Pair<Key, Val> getMapping(Key key) {
    final Map.Entry<Key, Val> entry = myMap.floorEntry(key);
    if (entry == null) return null;
    if (entry.getKey().equals(key) || myKeysResemblance.process(entry.getKey(), key)) {
      return new Pair<Key, Val>(entry.getKey(), entry.getValue());
    }
    return null;
  }

  @Override
  public void putAll(AreaMapI<Key, Val> other) {
    myMap.putAll(((TreeMapAsMembershipMap) other).myMap);
  }

  @Override
  public void put(Key key, Val val) {
    myMap.put(key, val);
  }

  @Override
  public Collection<Val> values() {
    return myMap.values();
  }

  @Override
  public Collection<Key> keySet() {
    return myMap.keySet();
  }

  @Override
  public Val getExact(Key key) {
    return myMap.get(key);
  }

  @Override
  public void remove(Key key) {
    myMap.remove(key);
  }

  @Override
  public boolean contains(Key key) {
    return myMap.containsKey(key);
  }

  @Override
  public void clear() {
    myMap.clear();
  }
}
