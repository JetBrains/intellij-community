// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Arrays;
import java.util.EventListener;
import java.util.Iterator;

public final class IntObjectCache<T> implements Iterable<T> {
  private static final int DEFAULT_SIZE = 8192;
  public static final int MIN_SIZE = 4;
  private static final int[] HASHTABLE_SIZES = {
      5, 11, 23, 47, 97, 197, 397, 797, 1597, 3203, 6421, 12853, 25717, 51437, 102877, 205759,
      411527, 823117, 1646237, 3292489, 6584983, 13169977, 26339969, 52679969, 105359939,
      210719881, 421439783, 842879579, 1685759167,
      433, 877, 1759, 3527, 7057, 14143, 28289, 56591, 113189, 226379, 452759, 905551, 1811107,
      3622219, 7244441, 14488931, 28977863, 57955739, 115911563, 231823147, 463646329, 927292699,
      1854585413,
      953, 1907, 3821, 7643, 15287, 30577, 61169, 122347, 244703, 489407, 978821, 1957651, 3915341,
      7830701, 15661423, 31322867, 62645741, 125291483, 250582987, 501165979, 1002331963,
      2004663929,
      1039, 2081, 4177, 8363, 16729, 33461, 66923, 133853, 267713, 535481, 1070981, 2141977, 4283963,
      8567929, 17135863, 34271747, 68543509, 137087021, 274174111, 548348231, 1096696463,
      31, 67, 137, 277, 557, 1117, 2237, 4481, 8963, 17929, 35863, 71741, 143483, 286973, 573953,
      1147921, 2295859, 4591721, 9183457, 18366923, 36733847, 73467739, 146935499, 293871013,
      587742049, 1175484103,
      599, 1201, 2411, 4831, 9677, 19373, 38747, 77509, 155027, 310081, 620171, 1240361, 2480729,
      4961459, 9922933, 19845871, 39691759, 79383533, 158767069, 317534141, 635068283, 1270136683,
      311, 631, 1277, 2557, 5119, 10243, 20507, 41017, 82037, 164089, 328213, 656429, 1312867,
      2625761, 5251529, 10503061, 21006137, 42012281, 84024581, 168049163, 336098327, 672196673,
      1344393353,
      3, 7, 17, 37, 79, 163, 331, 673, 1361, 2729, 5471, 10949, 21911, 43853, 87719, 175447, 350899,
      701819, 1403641, 2807303, 5614657, 11229331, 22458671, 44917381, 89834777, 179669557,
      359339171, 718678369, 1437356741,
      43, 89, 179, 359, 719, 1439, 2879, 5779, 11579, 23159, 46327, 92657, 185323, 370661, 741337,
      1482707, 2965421, 5930887, 11861791, 23723597, 47447201, 94894427, 189788857, 379577741,
      759155483, 1518310967,
      379, 761, 1523, 3049, 6101, 12203, 24407, 48817, 97649, 195311, 390647, 781301, 1562611,
      3125257, 6250537, 12501169, 25002389, 50004791, 100009607, 200019221, 400038451, 800076929,
      1600153859
  };

  static {
    Arrays.sort(HASHTABLE_SIZES);
  }

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

  private static int getAdjustedTableSize(int candidate) {
    int index = Arrays.binarySearch(HASHTABLE_SIZES, candidate);
    if (index >= 0) return candidate;
    return HASHTABLE_SIZES[-index];
  }

  protected static final class CacheEntry<T> {
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
      myCache[i] = new CacheEntry<>();
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

      fireListenersAboutDeletion(deletedKey, (T)deletedValue);
    }
  }

  public void removeAll() {
    final IntList keys = new it.unimi.dsi.fastutil.ints.IntArrayList(count());
    int current = myTop;
    while (current > 0) {
      if (myCache[current].value != null) {
        keys.add(myCache[current].key);
      }
      current = myCache[current].next;
    }
    for (int i = 0; i < keys.size(); ++ i) {
      remove(keys.getInt(i));
    }
  }

  // Some AbstractMap functions finished

  public void cacheObject(int key, T x) {
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
      fireListenersAboutDeletion(deletedKey, (T)deletedValue);
    }
  }

  public T tryKey(int key) {
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

  public boolean isCached(int key) {
    return searchForCacheEntry(key) != 0;
  }

  public int count() {
    return myCount;
  }

  public int size() {
    return myCache.length - 1;
  }

  public void resize(int newSize) {
    final IntObjectCache<T> newCache = new IntObjectCache<>(newSize);
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

  protected final class IntObjectCacheIterator implements Iterator<T> {
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

  @FunctionalInterface
  public interface DeletedPairsListener<T> extends EventListener {
    void objectRemoved(int key, T value);
  }

  public void addDeletedPairsListener(DeletedPairsListener<T> listener) {
    if (myListeners == null) {
      myListeners = new DeletedPairsListener[1];
    }
    else {
      myListeners = Arrays.copyOf(myListeners, myListeners.length + 1);
    }
    myListeners[myListeners.length - 1] = listener;
  }

  public void removeDeletedPairsListener(DeletedPairsListener<T> listener) {
    if (myListeners != null) {
      if (myListeners.length == 1) {
        myListeners = null;
      }
      else {
        DeletedPairsListener<?>[] newListeners = new DeletedPairsListener[myListeners.length - 1];
        int i = 0;
        for (DeletedPairsListener<?> myListener : myListeners) {
          if (myListener != listener) {
            newListeners[i++] = myListener;
          }
        }
        myListeners = newListeners;
      }
    }
  }

  private void fireListenersAboutDeletion(int key, T value) {
    if (myListeners != null) {
      for (DeletedPairsListener<?> myListener : myListeners) {
        ((DeletedPairsListener<T>)myListener).objectRemoved(key, value);
      }
    }
  }

  // end of listening features
}
