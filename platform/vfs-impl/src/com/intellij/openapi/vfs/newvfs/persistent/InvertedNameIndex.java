// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.EDT;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

final class InvertedNameIndex {

  private static final Int2IntMap ourSingleData = new Int2IntOpenHashMap();
  private static final Int2ObjectMap<int[]> ourMultiData = new Int2ObjectOpenHashMap<>();
  private static long ourModCount = -1;
  private static final AtomicLong ourNamesModCount = new AtomicLong();

  private static final ReentrantReadWriteLock ourLock = new ReentrantReadWriteLock();

  synchronized static boolean processFilesWithNames(@NotNull Set<String> names,
                                                    @NotNull IntPredicate processor,
                                                    @NotNull Supplier<PersistentFSRecordsStorage> supplier) {
    ourLock.readLock().lock();
    try {
      if (ourModCount != getModCount()) {
        ourLock.readLock().unlock();
        ourLock.writeLock().lock();
        try {
          if (ourModCount != getModCount()) {
            rebuildData(supplier);
            ourModCount = getModCount();
          }
        }
        finally {
          ourLock.readLock().lock();
          ourLock.writeLock().unlock();
        }
      }
      return processData(names, processor);
    }
    finally {
      ourLock.readLock().unlock();
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
          for (int i = 0, len = multi[multi.length - 1]; i < len; i++) {
            if (!processor.test(multi[i])) return false;
          }
        }
      }
    }
    return true;
  }

  private static void rebuildData(@NotNull Supplier<PersistentFSRecordsStorage> supplier) {
    long start = System.nanoTime();
    int defSize = Math.max(1000, ourSingleData.size() + ourMultiData.size());
    BitSet processedNames = new BitSet(defSize);
    ourSingleData.clear();
    ourMultiData.clear();
    FSRecords.readAndHandleErrors(() -> {
      supplier.get().processAllNames((nameId, fileId) -> {
        boolean processed = processedNames.get(nameId);
        processedNames.set(nameId);
        int[] multi;
        if (!processed) {
          ourSingleData.put(nameId, fileId);
        }
        else if ((multi = ourMultiData.get(nameId)) == null) {
          int prev = ourSingleData.remove(nameId);
          ourMultiData.put(nameId, new int[]{prev, fileId});
        }
        else if (multi.length == 2) {
          ourMultiData.put(nameId, new int[]{multi[0], multi[1], fileId, 0, 3});
        }
        else if (multi[multi.length - 2] == 0) {
          multi[multi[multi.length - 1]] = fileId;
          multi[multi.length - 1]++;
        }
        else {
          int[] next = Arrays.copyOf(multi, multi.length * 2 + 1);
          next[multi.length - 1] = fileId;
          next[next.length - 1] = multi.length;
          ourMultiData.put(nameId, next);
        }
        return 0;
      });
      return null;
    });
    if (FSRecords.LOG.isDebugEnabled()) {
      FSRecords.LOG.debug(InvertedNameIndex.class.getName()+ " rebuilt in " + TimeoutUtil.getDurationMillis(start) + " ms",
                          EDT.isCurrentThreadEdt() ? new Throwable("### EDT ###") : null);
    }
  }

  static void incModCount() {
    ourNamesModCount.incrementAndGet();
  }

  private static long getModCount() {
    return ourNamesModCount.get();
  }
}
