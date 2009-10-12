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

import java.util.ArrayList;
import java.util.EventListener;
import java.util.Iterator;

public class ObjectCache<K,V> extends ObjectCacheBase implements Iterable<V> {

  public static final int DEFAULT_SIZE = 8192;
  public static final int MIN_SIZE = 4;

  private int myTop;
  private int myBack;
  private CacheEntry<K, V>[] myCache;
  private int[] myHashTable;
  private int myHashTableSize;
  private int myCount;
  private int myFirstFree;
  private DeletedPairsListener[] myListeners;
  private int myAttempts;
  private int myHits;

  protected static class CacheEntry<K, V> {
    public K key;
    public V value;
    public int prev;
    public int next;
    public int hash_next;
  }

  public ObjectCache() {
    this(DEFAULT_SIZE);
  }

  public ObjectCache(int cacheSize) {
    if (cacheSize < MIN_SIZE) {
      cacheSize = MIN_SIZE;
    }
    myTop = myBack = 0;
    myCache = new CacheEntry[cacheSize + 1];
    for (int i = 0; i < myCache.length; ++i) {
      myCache[i] = new CacheEntry<K,V>();
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

  public boolean containsKey(K key) {
    return isCached(key);
  }

  public V get(K key) {
    return tryKey(key);
  }

  public V put(K key, V value) {
    V oldValue = tryKey(key);
    if (oldValue != null) {
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
      final V deletedVal = myCache[index].value;
      myCache[index].key = null;
      myCache[index].value = null;
      fireListenersAboutDeletion(key, deletedVal);
    }
  }

  public void removeAll() {
    final ArrayList<K> keys = new ArrayList<K>(count());
    int current = myTop;
    while (current > 0) {
      if (myCache[current].value != null) {
        keys.add(myCache[current].key);
      }
      current = myCache[current].next;
    }
    for (K key : keys) {
      remove(key);
    }
  }

  // Some AbstractMap functions finished

  final public void cacheObject(K key, V x) {
    K deletedKey = null;
    V deletedValue = null;

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

      final CacheEntry<K, V> cacheEntry = myCache[index];
      deletedKey = cacheEntry.key;
      deletedValue = cacheEntry.value;
      
      myCache[myBack = myCache[index].prev].next = 0;
    }
    myCache[index].key = key;
    myCache[index].value = x;
    addEntry2HashTable(index);
    add2Top(index);
    
    if (deletedKey != null) {
      fireListenersAboutDeletion(deletedKey, deletedValue);
    }
  }

  final public V tryKey(K key) {
    ++myAttempts;
    int index = searchForCacheEntry(key);
    if (index == 0) {
      return null;
    }
    ++myHits;
    final CacheEntry<K, V> cacheEntry = myCache[index];
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

  public Iterator<V> iterator() {
    return new ObjectCacheIterator<K, V>(this);
  }

  protected class ObjectCacheIterator<K,V> implements Iterator<V> {
    private final ObjectCache<K, V> myCache;
    private int myCurrentEntry;

    public ObjectCacheIterator(ObjectCache<K, V> cache) {
      myCache = cache;
      myCurrentEntry = 0;
      cache.myCache[0].next = cache.myTop;
    }

    public boolean hasNext() {
      return (myCurrentEntry = myCache.myCache[myCurrentEntry].next) != 0;
    }

    public V next() {
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

  private void fireListenersAboutDeletion(final K key, final V value) {
    if (myListeners != null) {
      for (DeletedPairsListener myListener : myListeners) {
        myListener.objectRemoved(key, value);
      }
    }
  }

  // end of listening features
}
