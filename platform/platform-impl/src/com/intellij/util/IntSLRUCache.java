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

import com.intellij.util.containers.IntObjectLinkedMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class IntSLRUCache<T> {
  private static final boolean ourPrintDebugStatistics = false;
  private final IntObjectLinkedMap<T> myProtectedQueue;
  private final IntObjectLinkedMap<T> myProbationalQueue;
  private int probationalHits;
  private int protectedHits;
  private int misses;

  public IntSLRUCache(int protectedQueueSize, int probationalQueueSize) {
    myProtectedQueue = new IntObjectLinkedMap<>(protectedQueueSize);
    myProbationalQueue = new IntObjectLinkedMap<>(probationalQueueSize);
  }

  @NotNull
  public IntObjectLinkedMap.MapEntry<T> cacheEntry(int key, T value) {
    IntObjectLinkedMap.MapEntry<T> cached = myProtectedQueue.getEntry(key);
    if (cached == null) {
      cached = myProbationalQueue.getEntry(key);
    }
    if (cached != null) {
      return cached;
    }

    IntObjectLinkedMap.MapEntry<T> entry = new IntObjectLinkedMap.MapEntry<>(key, value);
    myProbationalQueue.putEntry(entry);
    return entry;
  }

  @Nullable
  public IntObjectLinkedMap.MapEntry<T> getCachedEntry(int id) {
    return getCachedEntry(id, true);
  }

  @Nullable
  public IntObjectLinkedMap.MapEntry<T> getCachedEntry(int id, boolean allowMutation) {
    IntObjectLinkedMap.MapEntry<T> entry = myProtectedQueue.getEntry(id);
    if (entry != null) {
      protectedHits++;
      return entry;
    }

    entry = myProbationalQueue.getEntry(id);
    if (entry != null) {
      printStatistics(++probationalHits);

      if (allowMutation) {
        myProbationalQueue.removeEntry(entry.key);
        IntObjectLinkedMap.MapEntry<T> demoted = myProtectedQueue.putEntry(entry);
        if (demoted != null) {
          myProbationalQueue.putEntry(demoted);
        }
      }
      return entry;
    }

    printStatistics(++misses);

    return null;
  }

  private void printStatistics(int hits) {
    if (ourPrintDebugStatistics && hits % 1000 == 0) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("IntSLRUCache.getCachedEntry time " + System.currentTimeMillis() +
                         ", prot=" + protectedHits + ", prob=" + probationalHits + ", misses=" + misses);
    }
  }

}