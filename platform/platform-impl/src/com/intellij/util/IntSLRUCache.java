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

import gnu.trove.TIntObjectHashMap;
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
  private final TIntObjectHashMap<Entry> myEntryMap = new TIntObjectHashMap<Entry>();
  private final IntSLRUQueue myProtectedQueue;
  private final IntSLRUQueue myProbationalQueue;
  private int probationalHits = 0;
  private int protectedHits = 0;
  private int misses = 0;

  public IntSLRUCache(int protectedQueueSize, int probationalQueueSize) {
    myProtectedQueue = new IntSLRUQueue(protectedQueueSize);
    myProbationalQueue = new IntSLRUQueue(probationalQueueSize);
  }

  public Entry cacheEntry(int id, final Entry entry) {
    Entry cached = myEntryMap.get(id);
    if (cached != null) {
      return cached;
    }

    entry.isProtected = false;
    myEntryMap.put(id, entry);
    int toDrop = myProbationalQueue.queueFirst(id, entry);
    if (toDrop >= 0) {
      myEntryMap.remove(toDrop);
    }
    return entry;
  }

  @Nullable
  public Entry getCachedEntry(int id) {
    Entry entry = myEntryMap.get(id);
    if (entry != null) {
      if (entry.isProtected) {
        protectedHits++;
        return entry;
      }

      probationalHits++;
      printStatistics(probationalHits);


      myProbationalQueue.removeEntry(entry);
      entry.isProtected = true;
      int demoted = myProtectedQueue.queueFirst(id, entry);
      if (demoted >= 0) {
        Entry demotedEntry = myEntryMap.get(demoted);
        demotedEntry.isProtected = false;
        int toDrop = myProbationalQueue.queueFirst(demoted, demotedEntry);
        if (toDrop >= 0) {
          myEntryMap.remove(toDrop);
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

  public static class CacheEntry<T> {
    int prevId;
    int nextId;
    protected final T userObject;
    boolean isProtected;

    public CacheEntry(T userObject) {
      this.userObject = userObject;
    }
  }

  /**
   * Keeps queue start & end id and size. The queue entries themselves are stored in {@link IntSLRUCache#myEntryMap}
   */
  private class IntSLRUQueue {
    private final int queueLimit;

    private int firstId = -1;
    private int lastId = -1;
    private int queueSize = 0;

    private IntSLRUQueue(int limit) {
      queueLimit = limit;
    }

    void removeEntry(Entry entry) {
      if (entry.prevId >= 0) {
        myEntryMap.get(entry.prevId).nextId = entry.nextId;
      } else {
        firstId = entry.nextId;
      }
      if (entry.nextId >= 0) {
        myEntryMap.get(entry.nextId).prevId = entry.prevId;
      } else {
        lastId = entry.prevId;
      }
      queueSize--;
    }

    int queueFirst(int addedId, Entry entry) {
      int oldFirst = firstId;
      entry.nextId = oldFirst;
      entry.prevId = -1;

      firstId = addedId;
      if (oldFirst >= 0) {
        myEntryMap.get(oldFirst).prevId = addedId;
      } else {
        lastId = addedId;
      }
      queueSize++;

      if (queueSize <= queueLimit) {
        return -1;
      }

      int eldest = lastId;
      removeEntry(myEntryMap.get(eldest));
      return eldest;
    }

  }

}
