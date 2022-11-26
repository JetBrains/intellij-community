// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.SystemProperties;
import com.intellij.util.io.DataEnumeratorEx;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Index for lookup fileName -> fileId(s). Supposed to be replacement of {@link com.intellij.psi.search.FilenameIndex}?
 *
 * Data layout is either single entry or multiple entries, not both:
 * <ul>
 *   <li>single {@code nameId->fileId} entry in {@link InvertedNameIndex#ourSingleData}</li>
 *   <li>multiple entries in {@link InvertedNameIndex#ourMultiData} in 2 possible formats:
 *     <ul>
 *       <li>{@code nameId->(fileId1, fileId2)}</li>
 *       <li>{@code nameId->(N, fileId1, ..., fileIdN, 0, ... 0)}</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @see InvertedNameIndex#checkConsistency
 */
final class InvertedNameIndex {
  /**
   * id=0 used as NULL (i.e. absent) value
   */
  public static final int NULL_NAME_ID = DataEnumeratorEx.NULL_ID;

  private static final ReadWriteLock myRwLock = new ReentrantReadWriteLock();

  private static final Int2IntMap ourSingleData = new Int2IntOpenHashMap();
  private static final Int2ObjectMap<int[]> ourMultiData = new Int2ObjectOpenHashMap<>();
  private static final boolean ourCheckConsistency = SystemProperties.getBooleanProperty("idea.vfs.name.index.check.consistency", false);


  static boolean processFilesWithNames(@NotNull Set<String> names, @NotNull IntPredicate processor) {
    myRwLock.readLock().lock();
    try {
      return processData(names, processor);
    }
    finally {
      myRwLock.readLock().unlock();
    }
  }

  static void updateFileName(int fileId, int newNameId, int oldNameId) {
    myRwLock.writeLock().lock();
    try {
      if (oldNameId != NULL_NAME_ID) {
        deleteDataInner(fileId, oldNameId);
      }
      if (newNameId != NULL_NAME_ID) {
        updateDataInner(fileId, newNameId);
      }
    }
    finally {
      myRwLock.writeLock().unlock();
    }
  }

  static void clear() {
    myRwLock.writeLock().lock();
    try {
      ourSingleData.clear();
      ourMultiData.clear();
    }
    finally {
      myRwLock.writeLock().unlock();
    }
  }

  /**
   * Add fileId to the list of fileIds associated with nameId
   */
  static void updateDataInner(int fileId, int nameId) {
    myRwLock.writeLock().lock();
    try {
      int single = ourSingleData.get(nameId);
      int[] multi = ourMultiData.get(nameId);
      if (single == NULL_NAME_ID && multi == null) {
        ourSingleData.put(nameId, fileId);
      }
      else if (multi == null) {
        ourMultiData.put(nameId, new int[]{single, fileId});
        ourSingleData.remove(nameId);
      }
      else if (multi.length == 2) {
        ourMultiData.put(nameId, new int[]{3, multi[0], multi[1], fileId, NULL_NAME_ID});
      }
      else if (multi[multi.length - 1] == 0) {
        multi[0]++;
        multi[multi[0]] = fileId;
      }
      else {
        int[] next = Arrays.copyOf(multi, multi.length * 2 + 1);
        next[0]++;
        next[next[0]] = fileId;
        ourMultiData.put(nameId, next);
      }
      if (ourCheckConsistency) {
        checkConsistency(nameId);
      }
    }
    finally {
      myRwLock.writeLock().unlock();
    }
  }

  @VisibleForTesting
  static boolean forEachFileIds(final @NotNull IntSet nameIds,
                                final @NotNull IntPredicate processor) {
    final IntIterator it = nameIds.iterator();
    while (it.hasNext()) {
      final int nameId = it.nextInt();
      final int single = ourSingleData.get(nameId);
      final int[] multi;
      if (single != NULL_NAME_ID) {
        if (!processor.test(single)) return false;
      }
      else if ((multi = ourMultiData.get(nameId)) != null) {
        if (multi.length == 2) {
          if (!processor.test(multi[0])) return false;
          if (!processor.test(multi[1])) return false;
        }
        else {
          for (int i = 1, len = multi[0]; i <= len; i++) {
            if (!processor.test(multi[i])) return false;
          }
        }
      }
    }

    return true;
  }

  private static boolean processData(@NotNull Set<String> names, @NotNull IntPredicate processor) {
    for (String name : names) {
      //TODO RC: this will not work for non-strict names enumerator!
      int nameId = FSRecords.getNameId(name);
      int single = ourSingleData.get(nameId);
      int[] multi;
      if (single != NULL_NAME_ID) {
        if (!processor.test(single)) return false;
      }
      else if ((multi = ourMultiData.get(nameId)) != null) {
        if (multi.length == 2) {
          if (!processor.test(multi[0])) return false;
          if (!processor.test(multi[1])) return false;
        }
        else {
          for (int i = 1, len = multi[0]; i <= len; i++) {
            if (!processor.test(multi[i])) return false;
          }
        }
      }
    }
    return true;
  }

  private static void deleteDataInner(int fileId, int nameId) {
    int single = ourSingleData.get(nameId);
    int[] multi = ourMultiData.get(nameId);
    if (single == fileId) {
      ourSingleData.remove(nameId);
    }
    else if (multi != null) {
      if (multi.length == 2) {
        if (multi[0] == fileId) {
          ourMultiData.remove(nameId);
          ourSingleData.put(nameId, multi[1]);
        }
        else if (multi[1] == fileId) {
          ourMultiData.remove(nameId);
          ourSingleData.put(nameId, multi[0]);
        }
      }
      else {
        boolean found = false;
        for (int i = 1, len = multi[0]; i <= len; i++) {
          if (found) {
            multi[i - 1] = multi[i];
          }
          else if (multi[i] == fileId) {
            found = true;
            multi[0]--;
          }
        }
        if (found) {
          int len = multi[0];
          if (len == 0) {
            ourMultiData.remove(nameId);
          }
          else if (len == 1) {
            ourMultiData.remove(nameId);
            ourSingleData.put(nameId, multi[1]);
          }
          else if (len == 2) {
            ourMultiData.put(nameId, new int[]{multi[1], multi[2]});
          }
          else {
            multi[len + 1] = NULL_NAME_ID;
          }
        }
      }
    }
    if (ourCheckConsistency) {
      checkConsistency(nameId);
    }
  }

  static void checkConsistency() {
    myRwLock.readLock().lock();
    try {
      IntIterator keyIt1 = ourSingleData.keySet().intIterator();
      while (keyIt1.hasNext()) {
        checkConsistency(keyIt1.nextInt());
      }
      IntIterator keyIt2 = ourMultiData.keySet().intIterator();
      while (keyIt2.hasNext()) {
        checkConsistency(keyIt2.nextInt());
      }
    }
    finally {
      myRwLock.readLock().unlock();
    }
  }

  private static void checkConsistency(int nameId) {
    int single = ourSingleData.get(nameId);
    int[] multi = ourMultiData.get(nameId);
    if (single != NULL_NAME_ID && multi != null) {
      throw new AssertionError("both single and multi entries present");
    }
    else if (multi == null) {
      // nothing
    }
    else if (multi.length == 2) {
      if (multi[0] == 0 || multi[1] == 0) {
        throw new AssertionError("zero non-free entry");
      }
      if (multi[0] == multi[1]) {
        throw new AssertionError("duplicate multi entries");
      }
    }
    else if (multi.length == 0 || multi[0] <= 0 || multi[0] + 1 > multi.length) {
      throw new AssertionError("incorrect multi entries number");
    }
    else {
      IntArraySet set = new IntArraySet();
      for (int i = 1, len = multi[0]; i < multi.length; i++) {
        if (i <= len) {
          if (multi[i] == 0) {
            throw new AssertionError("zero non-free entry");
          }
          if (!set.add(multi[i])) {
            throw new AssertionError("duplicate entries");
          }
        }
        else if (multi[i] != 0) {
          throw new AssertionError("non-zero free entry");
        }
      }
    }
  }
}
