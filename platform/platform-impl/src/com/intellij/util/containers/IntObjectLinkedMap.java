/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class IntObjectLinkedMap<Entry extends IntObjectLinkedMap.MapEntry> {
  /**
   * The header of the double-linked list, its before = most recently added entry; its after = eldest entry
   */
  private final MapEntry myHeader;
  private final MapEntry[] myArray;
  private final int myCapacity;
  private int mySize;

  public IntObjectLinkedMap(int capacity) {
    myCapacity = capacity;
    myArray = new MapEntry[capacity * 8 / 5];
    myHeader = new MapEntry<>(0, null);
    myHeader.before = myHeader.after = myHeader;
  }

  @Nullable
  public Entry getEntry(int key) {
    MapEntry candidate = myArray[getArrayIndex(key)];
    while (candidate != null) {
      if (candidate.key == key) {
        //noinspection unchecked
        return (Entry)candidate;
      }
      candidate = candidate.next;
    }
    return null;
  }

  private int getArrayIndex(int key) {
    return (key & 0x7fffffff) % myArray.length;
  }

  public void removeEntry(int key) {
    int index = getArrayIndex(key);
    MapEntry candidate = myArray[index];
    MapEntry prev = null;
    while (candidate != null) {
      if (candidate.key == key) {
        if (prev == null) {
          myArray[index] = candidate.next;
        } else {
          prev.next = candidate.next;
        }

        candidate.before.after = candidate.after;
        candidate.after.before = candidate.before;
        candidate.next = candidate.before = candidate.after = null;

        mySize--;
        return;
      }
      prev = candidate;
      candidate = candidate.next;
    }
  }

  public Entry putEntry(Entry entry) {
    removeEntry(entry.key);

    int index = getArrayIndex(entry.key);
    entry.next = myArray[index];
    myArray[index] = entry;

    entry.before = myHeader.before;
    entry.after = myHeader;
    entry.before.after = entry;
    myHeader.before = entry;

    mySize++;
    if (mySize <= myCapacity) {
      return null;
    }

    //noinspection unchecked
    Entry eldest = (Entry)myHeader.after;
    removeEntry(eldest.key);
    return eldest;
  }

  public static class MapEntry<T> {
    public final int key;
    public final T value;
    MapEntry next; // in the list of entries with the same hash
    MapEntry before, after; // in the doubly-linked list reflecting the map's history

    public MapEntry(int key, T value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public String toString() {
      return key + "->" + value;
    }
  }


}
