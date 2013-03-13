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
package com.intellij.util;

import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class IntSLRUCache<Entry extends IntSLRUCache.CacheEntry> {
  private static final boolean ourPrintDebugStatistics = false;
  /**
   * A map from int id to entries.
   * Entries keep user objects as well as some queue maintenance data:
   *   next, previous element ids in the queues,
   *   and a flag which queue this entry belongs to: protected or probational.
   */
  private final IntObjectChainedMap<Entry> myEntryMap;
  private final IntSLRUQueue myProtectedQueue;
  private final IntSLRUQueue myProbationalQueue;
  private int probationalHits = 0;
  private int protectedHits = 0;
  private int misses = 0;

  public IntSLRUCache(int protectedQueueSize, int probationalQueueSize) {
    myProtectedQueue = new IntSLRUQueue(protectedQueueSize);
    myProbationalQueue = new IntSLRUQueue(probationalQueueSize);
    myEntryMap = new IntObjectChainedMap<Entry>(protectedQueueSize + probationalQueueSize);
  }

  public Entry cacheEntry(final Entry entry) {
    Entry cached = myEntryMap.getEntry(entry.key);
    if (cached != null) {
      return cached;
    }

    entry.isProtected = false;
    myEntryMap.putEntry(entry);
    Entry toDrop = myProbationalQueue.queueFirst(entry);
    if (toDrop != null) {
      myEntryMap.removeEntry(toDrop);
    }
    return entry;
  }

  @Nullable
  public Entry getCachedEntry(int id) {
    Entry entry = myEntryMap.getEntry(id);
    if (entry != null) {
      if (entry.isProtected) {
        protectedHits++;
        return entry;
      }

      probationalHits++;
      printStatistics(probationalHits);


      myProbationalQueue.removeEntry(entry);
      entry.isProtected = true;
      Entry demoted = myProtectedQueue.queueFirst(entry);
      if (demoted != null) {
        demoted.isProtected = false;
        Entry toDrop = myProbationalQueue.queueFirst(demoted);
        if (toDrop != null) {
          myEntryMap.removeEntry(toDrop);
        }
      }
      return entry;
    }

    misses++;
    //noinspection ConstantConditions
    printStatistics(misses);

    return null;
  }

  private void printStatistics(int hits) {
    //noinspection ConstantConditions
    if (ourPrintDebugStatistics && hits % 1000 == 0) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("IntSLRUCache.getCachedEntry time " + System.currentTimeMillis() +
                         ", prot=" + protectedHits + ", prob=" + probationalHits + ", misses=" + misses);
    }
  }

  public static class CacheEntry<T> extends IntObjectChainedMap.MapEntry<T> {
    private CacheEntry<T> prevId;
    private CacheEntry<T> nextId;
    private boolean isProtected;

    public CacheEntry(int id, T userObject) {
      super(id, userObject);
    }
  }

  /**
   * Keeps queue start & end id and size. The queue entries themselves are stored in {@link IntSLRUCache#myEntryMap}
   */
  private class IntSLRUQueue {
    private final int queueLimit;

    private Entry firstId = null;
    private Entry lastId = null;
    private int queueSize = 0;

    private IntSLRUQueue(int limit) {
      queueLimit = limit;
    }

    void removeEntry(Entry entry) {
      if (entry.prevId != null) {
        entry.prevId.nextId = entry.nextId;
      } else {
        //noinspection unchecked
        firstId = (Entry)entry.nextId;
      }
      if (entry.nextId != null) {
        entry.nextId.prevId = entry.prevId;
      } else {
        //noinspection unchecked
        lastId = (Entry)entry.prevId;
      }
      queueSize--;
    }

    Entry queueFirst(Entry entry) {
      CacheEntry oldFirst = firstId;
      entry.nextId = oldFirst;
      entry.prevId = null;

      firstId = entry;
      if (oldFirst != null) {
        oldFirst.prevId = entry;
      } else {
        lastId = entry;
      }
      queueSize++;

      if (queueSize <= queueLimit) {
        return null;
      }

      Entry eldest = lastId;
      removeEntry(eldest);
      return eldest;
    }

  }

}

class IntObjectChainedMap<Entry extends IntObjectChainedMap.MapEntry> {
  private final MapEntry[] myArray;

  IntObjectChainedMap(int capacity) {
    myArray = new MapEntry[capacity * 8 / 5];
  }

  @Nullable
  Entry getEntry(int key) {
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

  void removeEntry(Entry entry) {
    int key = entry.key;
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
        candidate.next = null;
        return;
      }
      prev = candidate;
      candidate = candidate.next;
    }
  }

  void putEntry(Entry entry) {
    removeEntry(entry);
    int index = getArrayIndex(entry.key);
    entry.next = myArray[index];
    myArray[index] = entry;
  }

  static class MapEntry<T> {
    public final int key;
    public final T value;
    private MapEntry next;

    MapEntry(int key, T value) {
      this.key = key;
      this.value = value;
    }
  }


}
