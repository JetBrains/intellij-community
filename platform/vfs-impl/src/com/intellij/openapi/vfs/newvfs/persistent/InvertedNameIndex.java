// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.EDT;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
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

  private static final Int2IntMap ourSingleData = new Int2IntOpenHashMap();
  private static final Int2ObjectMap<int[]> ourMultiData = new Int2ObjectOpenHashMap<>();
  private static final boolean ourCheckConsistency = SystemProperties.getBooleanProperty("idea.vfs.name.index.check.consistency", false);

  static boolean processFilesWithNames(@NotNull Set<String> names,
                                       @NotNull IntPredicate processor,
                                       @NotNull Supplier<PersistentFSRecordsStorage> supplier) {
    FSRecords.lock.readLock().lock();
    try {
      if (needsLoading()) {
        loadData(supplier);
      }
      return processData(names, processor);
    }
    finally {
      FSRecords.lock.readLock().unlock();
    }
  }

  static void updateFileName(int fileId, int newNameId, int oldNameId) {
    FSRecords.LOG.assertTrue(FSRecords.lock.isWriteLocked(), "no write lock");
    if (needsLoading()) {
      return;
    }
    if (oldNameId != 0) {
      deleteDataInner(fileId, oldNameId);
    }
    if (newNameId != 0) {
      updateDataInner(fileId, newNameId);
    }
  }

  static void clear() {
    FSRecords.LOG.assertTrue(FSRecords.lock.isWriteLocked(), "no write lock");
    ourSingleData.clear();
    ourMultiData.clear();
  }

  private static boolean needsLoading() {
    return ourSingleData.isEmpty() && ourMultiData.isEmpty();
  }

  private static void loadData(@NotNull Supplier<PersistentFSRecordsStorage> supplier) {
    FSRecords.lock.readLock().unlock();
    FSRecords.lock.writeLock().lock();
    try {
      if (needsLoading()) {
        loadDataInner(supplier);
      }
    }
    catch (ProcessCanceledException ex) {
      clear();
      throw ex;
    }
    finally {
      FSRecords.lock.readLock().lock();
      FSRecords.lock.writeLock().unlock();
    }
  }

  private static boolean processData(@NotNull Set<String> names, @NotNull IntPredicate processor) {
    for (String name : names) {
      int nameId = FSRecords.getNameId(name);
      int single = ourSingleData.get(nameId);
      int[] multi;
      if (single != 0) {
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

  private static void loadDataInner(@NotNull Supplier<PersistentFSRecordsStorage> supplier) {
    long start = System.nanoTime();
    FSRecords.readAndHandleErrors(() -> {
      supplier.get().processAllNames((nameId, fileId) -> {
        updateDataInner(fileId, nameId);
        return 0;
      });
      return null;
    });
    FSRecords.LOG.info(InvertedNameIndex.class.getSimpleName() + " rebuilt in " + TimeoutUtil.getDurationMillis(start) + " ms",
                       EDT.isCurrentThreadEdt() ? new Throwable("### EDT ###") : null);
  }

  private static void updateDataInner(int fileId, int nameId) {
    int single = ourSingleData.get(nameId);
    int[] multi = ourMultiData.get(nameId);
    if (single == 0 && multi == null) {
      ourSingleData.put(nameId, fileId);
    }
    else if (multi == null) {
      ourMultiData.put(nameId, new int[]{single, fileId});
      ourSingleData.remove(nameId);
    }
    else if (multi.length == 2) {
      ourMultiData.put(nameId, new int[]{3, multi[0], multi[1], fileId, 0});
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
            ourMultiData.put(nameId, new int[] {multi[1], multi[2]});
          }
          else {
            multi[len + 1] = 0;
          }
        }
      }
    }
    if (ourCheckConsistency) {
      checkConsistency(nameId);
    }
  }

  private static void checkConsistency(int nameId) {
    int single = ourSingleData.get(nameId);
    int[] multi = ourMultiData.get(nameId);
    if (single != 0 && multi != null) {
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
