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

/**
 * @author irengrig
 */
public class AreaMap<Key, Val> {
  protected final List<Key> myKeys;
  protected final Map<Key, Val> myMap;
  // [admittedly] parent, [-"-] child
  protected final PairProcessor<Key, Key> myKeysResemblance;
  private final Comparator<Key> myComparator;

  public static<Key extends Comparable<Key>, Val> AreaMap<Key, Val> create(final PairProcessor<Key, Key> keysResemblance) {
    return new AreaMap<>(keysResemblance, new ComparableComparator<>());
  }

  public static<Key, Val> AreaMap<Key, Val> create(final PairProcessor<Key, Key> keysResemblance, final Comparator<Key> comparator) {
    return new AreaMap<>(keysResemblance, comparator);
  }

  protected AreaMap(final PairProcessor<Key, Key> keysResemblance, final Comparator<Key> comparator) {
    myKeysResemblance = keysResemblance;
    myComparator = comparator;
    myKeys = new LinkedList<>();
    myMap = new HashMap<>();
  }

  public void putAll(final AreaMap<Key, Val> other) {
    myKeys.addAll(other.myKeys);
    myMap.putAll(other.myMap);
  }

  public void put(final Key key, final Val val) {
    putImpl(key, val);
  }

  protected int putIfNoParent(final Key key, final Val val) {
    if(myMap.put(key, val) != null) {
      return -1;
    }

    if (myKeys.isEmpty()) {
      myKeys.add(key);
      return 0;
    }

    final int idx = Collections.binarySearch(myKeys, key, myComparator);
    if (idx < 0) {
      // insertion, no copy exist
      final int insertionIdx = - idx - 1;
      // check parent
      for (final ListIterator<Key> listIterator = myKeys.listIterator(insertionIdx); listIterator.hasPrevious();) {
        final Key previous = listIterator.previous();
        if (myKeysResemblance.process(previous, key)) {
          myMap.remove(key);
          return -1;
        }
      }
      // insertionIdx not necessarily exist
      myKeys.add(insertionIdx, key);
      return insertionIdx;
    }
    assert true;
    myMap.remove(key);
    return -1;
  }

  protected int putImpl(final Key key, final Val val) {
    myMap.put(key, val);

    if (myKeys.isEmpty()) {
      myKeys.add(key);
      return 0;
    }

    final int idx = Collections.binarySearch(myKeys, key, myComparator);
    if (idx < 0) {
      // insertion, no copy exist
      final int insertionIdx = - idx - 1;
      // insertionIdx not necessarily exist
      myKeys.add(insertionIdx, key);
      return insertionIdx;
    }
    return idx;
  }

  public Collection<Val> values() {
    return Collections.unmodifiableCollection(myMap.values());
  }

  public Collection<Key> keySet() {
    return Collections.unmodifiableCollection(myKeys);
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

  public void remove(final Key key) {
    myKeys.remove(key);
    myMap.remove(key);
  }

  public boolean contains(final Key key) {
    return myKeys.contains(key);
  }

  public void getSimiliar(final Key key, final PairProcessor<Key, Val> consumer) {
    final int idx = Collections.binarySearch(myKeys, key, myComparator);
    if (idx < 0) {
      final int insertionIdx = - idx - 1;
      // take item before
      final int itemBeforeIdx = insertionIdx - 1;
      if (itemBeforeIdx >= 0) {
        for (ListIterator<Key> iterator = myKeys.listIterator(itemBeforeIdx + 1); iterator.hasPrevious(); ) {
          final Key candidate = iterator.previous();
          if (! myKeysResemblance.process(candidate, key)) continue;
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

  public void clear() {
    myKeys.clear();
    myMap.clear();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AreaMap areaMap = (AreaMap)o;

    if (!myKeys.equals(areaMap.myKeys)) return false;
    if (!myMap.equals(areaMap.myMap)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myKeys.hashCode();
    result = 31 * result + myMap.hashCode();
    return result;
  }
}
