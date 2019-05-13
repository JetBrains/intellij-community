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

import java.util.EventListener;
import java.util.Iterator;

/**
 * @author lvo
 */
public class IntObjectCache<T> extends ObjectCacheBase implements Iterable<T> {

  public static final int DEFAULT_SIZE = 8192;
  public static final int MIN_SIZE = 4;

  private int myTop;
  private int myBack;
  private CacheEntry<T>[] myCache;
  private int[] myHashTable;
  private int myHashTableSize;
  private int myCount;
  private int myFirstFree;
  private DeletedPairsListener[] myListeners;
  private int myAttempts;
  private int myHits;

  protected static class CacheEntry<T> {
    public int key;
    public T value;
    public int prev;
    public int next;
    public int hash_next;
  }

  public IntObjectCache() {
    this(DEFAULT_SIZE);
  }

  public IntObjectCache(int cacheSize) {
    if (cacheSize < MIN_SIZE) {
      cacheSize = MIN_SIZE;
    }
    myTop = myBack = 0;
    myCache = new CacheEntry[cacheSize + 1];
    for (int i = 0; i < myCache.length; ++i) {
      myCache[i] = new CacheEntry<T>();
    }
    myHashTableSize = getAdjustedTableSize(cacheSize);
    myHashTable = new int[myHashTableSize];
    myAttempts = 0;
    myHits = 0;
    myCount = myFirstFree = 0;
  }

  // Some AbstractMap functions started

  public boolean isEmpty() {
    return count() == 0;
  }

  public boolean containsKey(int key) {
    return isCached(key);
  }

  public T get(int key) {
    return tryKey(key);
  }

  public T put(int key, T value) {
    T oldValue = tryKey(key);
    if (oldValue != null) {
      remove(key);
    }
    cacheObject(key, value);
    return oldValue;
  }

  public void remove(int key) {
    int index = searchForCacheEntry(key);
    if (index != 0) {
      removeEntry(index);
      removeEntryFromHashTable(index);
      myCache[index].hash_next = myFirstFree;
      myFirstFree = index;

      final CacheEntry cacheEntry = myCache[index];
      final int deletedKey = cacheEntry.key;
      final Object deletedValue = cacheEntry.value;

      myCache[index].value = null;

      fireListenersAboutDeletion(deletedKey, deletedValue);
    }
  }

  public void removeAll() {
    final IntArrayList keys = new IntArrayList(count());
    int current = myTop;
    while (current > 0) {
      if (myCache[current].value != null) {
        keys.add(myCache[current].key);
      }
      current = myCache[current].next;
    }
    for (int i = 0; i < keys.size(); ++ i) {
      remove(keys.get(i));
    }
  }

  // Some AbstractMap functions finished

  public final void cacheObject(int key, T x) {
    int deletedKey = 0;
    Object deletedValue = null;

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

      final CacheEntry cacheEntry = myCache[index];
      deletedKey = cacheEntry.key;
      deletedValue = cacheEntry.value;

      myCache[myBack = myCache[index].prev].next = 0;
    }
    myCache[index].key = key;
    myCache[index].value = x;
    addEntry2HashTable(index);
    add2Top(index);
    
    if (deletedValue != null) {
      fireListenersAboutDeletion(deletedKey, deletedValue);
    }
  }

  public final T tryKey(int key) {
    ++myAttempts;
    final int index = searchForCacheEntry(key);
    if (index == 0) {
      return null;
    }
    ++myHits;
    final CacheEntry<T> cacheEntry = myCache[index];
    final int top = myTop;
    if (index != top) {
      final int prev = cacheEntry.prev;
      final int next = cacheEntry.next;
      if (index == myBack) {
        myBack = prev;
      }
      else {
        myCache[next].prev = prev;
      }
      myCache[prev].next = next;
      cacheEntry.next = top;
      cacheEntry.prev = 0;
      myCache[top].prev = index;
      myTop = index;
    }
    return cacheEntry.value;
  }

  public final boolean isCached(int key) {
    return searchForCacheEntry(key) != 0;
  }

  public int count() {
    return myCount;
  }

  public int size() {
    return myCache.length - 1;
  }

  public void resize(int newSize) {
    final IntObjectCache<T> newCache = new IntObjectCache<T>(newSize);
    final CacheEntry<T>[] cache = myCache;
    int back = myBack;
    while (back != 0) {
      final CacheEntry<T> cacheEntry = cache[back];
      newCache.cacheObject(cacheEntry.key, cacheEntry.value);
      back = cacheEntry.prev;
    }
    myTop = newCache.myTop;
    myBack = newCache.myBack;
    myCache = newCache.myCache;
    myHashTable = newCache.myHashTable;
    myHashTableSize = newCache.myHashTableSize;
    myCount = newCache.myCount;
    myFirstFree = newCache.myFirstFree;
  }

  public double hitRate() {
    return myAttempts > 0 ? (double)myHits / (double)myAttempts : 0;
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
    int hash_index = (myCache[index].key & 0x7fffffff) % myHashTableSize;
    myCache[index].hash_next = myHashTable[hash_index];
    myHashTable[hash_index] = index;
    ++myCount;
  }

  private void removeEntryFromHashTable(int index) {
    final int hash_index = (myCache[index].key & 0x7fffffff) % myHashTableSize;
    int current = myHashTable[hash_index];
    int previous = 0;
    while (current != 0) {
      int next = myCache[current].hash_next;
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

  private int searchForCacheEntry(int key) {
    myCache[0].key = key;
    int current = myHashTable[((key & 0x7fffffff) % myHashTableSize)];
    while (true) {
      final CacheEntry<T> cacheEntry = myCache[current];
      if (key == cacheEntry.key) {
        break;
      }
      current = cacheEntry.hash_next;
    }
    return current;
  }

  // start of Iterable implementation

  @Override
  public Iterator<T> iterator() {
    return new IntObjectCacheIterator(this);
  }

  protected class IntObjectCacheIterator implements Iterator<T> {
    private int myCurrentEntry;

    public IntObjectCacheIterator(IntObjectCache cache) {
      myCurrentEntry = 0;
      cache.myCache[0].next = cache.myTop;
    }

    @Override
    public boolean hasNext() {
      return (myCurrentEntry = myCache[myCurrentEntry].next) != 0;
    }

    @Override
    public T next() {
      return myCache[myCurrentEntry].value;
    }

    @Override
    public void remove() {
      removeEntry(myCache[myCurrentEntry].key);
    }
  }

  // end of Iterable implementation

  // start of listening features

  public interface DeletedPairsListener extends EventListener {
    void objectRemoved(int key, Object value);
  }

  public void addDeletedPairsListener(DeletedPairsListener listener) {
    if (myListeners == null) {
      myListeners = new DeletedPairsListener[1];
    }
    else {
      DeletedPairsListener[] newListeners = new DeletedPairsListener[myListeners.length + 1];
      System.arraycopy(myListeners, 0, newListeners, 0, myListeners.length);
      myListeners = newListeners;
    }
    myListeners[myListeners.length - 1] = listener;
  }

  public void removeDeletedPairsListener(DeletedPairsListener listener) {
    if (myListeners != null) {
      if (myListeners.length == 1) {
        myListeners = null;
      }
      else {
        DeletedPairsListener[] newListeners = new DeletedPairsListener[myListeners.length - 1];
        int i = 0;
        for (DeletedPairsListener myListener : myListeners) {
          if (myListener != listener) {
            newListeners[i++] = myListener;
          }
        }
        myListeners = newListeners;
      }
    }
  }

  private void fireListenersAboutDeletion(final int key, final Object value) {
    if (myListeners != null) {
      for (DeletedPairsListener myListener : myListeners) {
        myListener.objectRemoved(key, value);
      }
    }
  }

  // end of listening features
}
