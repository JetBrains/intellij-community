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

import com.intellij.util.EventDispatcher;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.Iterator;

public class ObjectIntCache<K> implements Iterable {

  public static final int defaultSize = 8192;
  public static final int minSize = 4;

  protected int myTop;
  protected int myBack;
  final protected CacheEntry[] myCache;
  final protected int[] myHashTable;
  protected int myHashTableSize;
  protected int myCount;
  protected int myFirstFree;

  final protected EventDispatcher<DeletedPairsListener> myEventDispatcher = EventDispatcher.create(DeletedPairsListener.class);

  private static final int[] tableSizes =
    new int[]{5, 11, 23, 47, 101, 199, 397, 797, 1597, 3191, 6397, 12799, 25589, 51199,
      102397, 204793, 409579, 819157, 2295859, 4591721, 9183457, 18366923, 36733847,
      73467739, 146935499, 293871013, 587742049, 1175484103};
  private long myAttempts;
  private long myHits;

  protected static class CacheEntry {
    public Object key;
    public int value;
    public int prev;
    public int next;
    public int hash_next;
  }

  public ObjectIntCache() {
    this(defaultSize);
  }

  public ObjectIntCache(int cacheSize) {
    if (cacheSize < minSize) {
      cacheSize = minSize;
    }
    myTop = myBack = 0;
    myCache = new CacheEntry[cacheSize + 1];
    for (int i = 0; i < myCache.length; ++i) {
      myCache[i] = new CacheEntry();
    }
    myHashTableSize = cacheSize;
    int i = 0;
    for (; myHashTableSize > tableSizes[i];) ++i;
    myHashTableSize = tableSizes[i];
    myHashTable = new int[myHashTableSize];
    myAttempts = 0;
    myHits = 0;
    myCount = myFirstFree = 0;
  }

  // Some AbstractMap functions started

  public boolean isEmpty() {
    return count() == 0;
  }

  public boolean containsKey(K key) {
    return isCached(key);
  }

  public int get(K key) {
    return tryKey(key);
  }

  public int put(K key, int value) {
    int oldValue = tryKey(key);
    if (oldValue != Integer.MIN_VALUE) {
      remove(key);
    }
    cacheObject(key, value);
    return oldValue;
  }

  public void remove(K key) {
    int index = searchForCacheEntry(key);
    if (index != 0) {
      removeEntry(index);
      removeEntryFromHashTable(index);
      myCache[index].hash_next = myFirstFree;
      myFirstFree = index;
      fireListenersAboutDeletion(index);
      myCache[index].key = null;
    }
  }

  public void removeAll() {
    final ArrayList<K> keys = new ArrayList<K>(count());
    for (int current = myTop; current > 0;) {
      if (myCache[current].key != null) {
        keys.add((K)myCache[current].key);
      }
      current = myCache[current].next;
    }
    for (K key : keys) {
      remove(key);
    }
  }

  // Some AbstractMap functions finished

  final public void cacheObject(K key, int x) {
    int index = myFirstFree;
    if (myCount < myCache.length - 1) {
      if (index == 0) {
        index = myCount;
        ++index;
      }
      else {
        myFirstFree = myCache[index].hash_next;
      }
      if (myCount == 0) {
        myBack = index;
      }
    }
    else {
      index = myBack;
      removeEntryFromHashTable(index);
      fireListenersAboutDeletion(index);
      myCache[myBack = myCache[index].prev].next = 0;
    }
    myCache[index].key = key;
    myCache[index].value = x;
    addEntry2HashTable(index);
    add2Top(index);
  }

  final public int tryKey(K key) {
    ++myAttempts;
    int index = searchForCacheEntry(key);
    if (index == 0) {
      return Integer.MIN_VALUE;
    }
    ++myHits;
    if (index != myTop) {
      removeEntry(index);
      add2Top(index);
    }
    return myCache[index].value;
  }

  final public boolean isCached(K key) {
    return searchForCacheEntry(key) != 0;
  }

  public int count() {
    return myCount;
  }

  public int size() {
    return myCache.length - 1;
  }

  public double hitRate() {
    return (myAttempts > 0) ? ((double)myHits / (double)myAttempts) : 0;
  }

  private void add2Top(int index) {
    myCache[index].next = myTop;
    myCache[index].prev = 0;
    myCache[myTop].prev = index;
    myTop = index;
  }

  private void removeEntry(int index) {
    if (index == myBack) {
      myBack = myCache[index].prev;
    }
    else {
      myCache[myCache[index].next].prev = myCache[index].prev;
    }
    if (index == myTop) {
      myTop = myCache[index].next;
    }
    else {
      myCache[myCache[index].prev].next = myCache[index].next;
    }
  }

  private void addEntry2HashTable(int index) {
    int hash_index = (myCache[index].key.hashCode() & 0x7fffffff) % myHashTableSize;
    myCache[index].hash_next = myHashTable[hash_index];
    myHashTable[hash_index] = index;
    ++myCount;
  }

  private void removeEntryFromHashTable(int index) {
    int hash_index = (myCache[index].key.hashCode() & 0x7fffffff) % myHashTableSize;
    int current = myHashTable[hash_index];
    int previous = 0;
    int next;
    while (current != 0) {
      next = myCache[current].hash_next;
      if (current == index) {
        if (previous != 0) {
          myCache[previous].hash_next = next;
        }
        else {
          myHashTable[hash_index] = next;
        }
        --myCount;
        break;
      }
      previous = current;
      current = next;
    }
  }

  private int searchForCacheEntry(K key) {
    int index = (key.hashCode() & 0x7fffffff) % myHashTableSize;
    int current = myHashTable[index];
    myCache[0].key = key;
    while (!key.equals(myCache[current].key)) {
      current = myCache[current].hash_next;
    }
    return current;
  }

  // start of Iterable implementation

  public Iterator iterator() {
    return new ObjectCacheIterator<K>(this);
  }

  protected class ObjectCacheIterator<K> implements Iterator {
    private final ObjectIntCache<K> myCache;
    private int myCurrentEntry;

    public ObjectCacheIterator(ObjectIntCache<K> cache) {
      myCache = cache;
      myCurrentEntry = 0;
      cache.myCache[0].next = cache.myTop;
    }

    public boolean hasNext() {
      return (myCurrentEntry = myCache.myCache[myCurrentEntry].next) != 0;
    }

    public Object next() {
      return myCache.myCache[myCurrentEntry].value;
    }

    public void remove() {
      myCache.remove((K)myCache.myCache[myCurrentEntry].key);
    }
  }

  // end of Iterable implementation

  // start of listening features

  public interface DeletedPairsListener extends EventListener {
    void objectRemoved(Object key, Object value);
  }

  public void addDeletedPairsListener(DeletedPairsListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeDeletedPairsListener(DeletedPairsListener listener) {
    myEventDispatcher.addListener(listener);
  }

  private void fireListenersAboutDeletion(int index) {
    final CacheEntry cacheEntry = myCache[index];
    myEventDispatcher.getMulticaster().objectRemoved(cacheEntry.key, cacheEntry.value);
  }

  // end of listening features
}
