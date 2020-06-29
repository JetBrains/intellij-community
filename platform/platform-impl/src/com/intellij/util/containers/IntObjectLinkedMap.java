// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public final class IntObjectLinkedMap<T> {
  /**
   * The header of the double-linked list, its before = most recently added entry; its after = eldest entry
   */
  private final MapEntry<T> myHeader;
  private final MapEntry<T>[] myArray;
  private final int myCapacity;
  private int mySize;

  public IntObjectLinkedMap(int capacity) {
    myCapacity = capacity;
    //noinspection unchecked
    myArray = new MapEntry[capacity * 8 / 5];
    myHeader = new MapEntry<>(0, null);
    myHeader.before = myHeader.after = myHeader;
  }

  @Nullable
  public MapEntry<T> getEntry(int key) {
    MapEntry<T> candidate = myArray[getArrayIndex(key)];
    while (candidate != null) {
      if (candidate.key == key) {
        return candidate;
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
    MapEntry<T> candidate = myArray[index];
    MapEntry<T> prev = null;
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

  public MapEntry<T> putEntry(@NotNull MapEntry<T> entry) {
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

    MapEntry<T> eldest = myHeader.after;
    removeEntry(eldest.key);
    return eldest;
  }

  public static class MapEntry<T> {
    public final int key;
    public final T value;
    MapEntry<T> next; // in the list of entries with the same hash
    MapEntry<T> before, after; // in the doubly-linked list reflecting the map's history

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
