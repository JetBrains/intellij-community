// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntPredicate;

import static com.intellij.util.SystemProperties.getBooleanProperty;

/**
 * {@link InvertedNameIndex} implementation: keeps [nameId->fileId] mapping in memory, split into 2 different maps: for unique
 * and non-unique [nameId->fileId] mappings.
 * Data layout is either single entry or multiple entries, not both:
 * <ul>
 *   <li>single {@code nameId->fileId} entry in {@link #singularMapping}</li>
 *   <li>multiple entries in {@link #multiMapping} in 2 possible formats:
 *     <ul>
 *       <li>{@code nameId->(fileId1, fileId2)}</li>
 *       <li>{@code nameId->(N, fileId1, ..., fileIdN, 0, ... 0)}</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @see #checkConsistency()
 */
@ApiStatus.Internal
public final class DefaultInMemoryInvertedNameIndex implements InvertedNameIndex {

  private final boolean CHECK_CONSISTENCY = getBooleanProperty("idea.vfs.name.index.check.consistency", false);

  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  /** [nameId -> fileId] mapping then fileId is unique -- i.e. only 1 file has such a name */
  private final Int2IntMap singularMapping = new Int2IntOpenHashMap();
  /**
   * [nameId -> (array of fileId)] mapping then fileId is NOT unique -- i.e. >1 files have such a name
   * (The exact array format is complicated, see class javadoc)
   */
  private final Int2ObjectMap<int[]> multiMapping = new Int2ObjectOpenHashMap<>();


  /**
   * Iterates through all the fileIds associated with nameId from nameIds collection, and passes each fileId to fileIdProcessor.
   *
   * @return if fileIdProcessor returns false -> stop eagerly, and return false, otherwise return true (even if there were no fileId
   * to process!)
   */
  @Override
  @VisibleForTesting
  public boolean forEachFileIds(@NotNull IntCollection nameIds,
                                @NotNull IntPredicate fileIdProcessor) {
    rwLock.readLock().lock();
    try {
      return processData(nameIds, fileIdProcessor);
    }
    finally {
      rwLock.readLock().unlock();
    }
  }

  private boolean processData(@NotNull IntCollection nameIds,
                              @NotNull IntPredicate processor) {
    for (IntIterator it = nameIds.iterator(); it.hasNext(); ) {
      int nameId = it.nextInt();
      int single = singularMapping.get(nameId);
      int[] multi;
      if (single != NULL_NAME_ID) {
        if (!processor.test(single)) return false;
      }
      else if ((multi = multiMapping.get(nameId)) != null) {
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

  @Override
  public void updateFileName(int fileId, int oldNameId, int newNameId) {
    if (oldNameId == newNameId) {
      return;
    }
    Lock writeLock = rwLock.writeLock();
    writeLock.lock();
    try {
      if (oldNameId != NULL_NAME_ID) {
        deleteDataInner(fileId, oldNameId);
      }
      if (newNameId != NULL_NAME_ID) {
        updateDataInner(fileId, newNameId);
      }
    }
    finally {
      writeLock.unlock();
    }
  }

  /** Adds fileId to the list of fileIds associated with nameId */
  //@GuardedBy(rwLock.writeLock)
  private void updateDataInner(int fileId, int nameId) {
    if (nameId == NULL_NAME_ID) {
      throw new IllegalArgumentException("nameId can't be NULL_ID(0)");
    }
    int single = singularMapping.get(nameId);
    int[] multi = multiMapping.get(nameId);
    if (single == NULL_NAME_ID && multi == null) {
      singularMapping.put(nameId, fileId);
    }
    else if (multi == null) {
      multiMapping.put(nameId, new int[]{single, fileId});
      singularMapping.remove(nameId);
    }
    else if (multi.length == 2) {
      multiMapping.put(nameId, new int[]{3, multi[0], multi[1], fileId, NULL_NAME_ID});
    }
    else if (multi[multi.length - 1] == 0) {
      multi[0]++;
      multi[multi[0]] = fileId;
    }
    else {
      int[] next = Arrays.copyOf(multi, multi.length * 2 + 1);
      next[0]++;
      next[next[0]] = fileId;
      multiMapping.put(nameId, next);
    }
    if (CHECK_CONSISTENCY) {
      checkConsistency(nameId);
    }
  }

  /* Removes fileId to the list of fileIds associated with nameId */
  //@GuardedBy(rwLock.writeLock)
  private void deleteDataInner(int fileId, int nameId) {
    int single = singularMapping.get(nameId);
    int[] multi = multiMapping.get(nameId);
    if (single == fileId) {
      singularMapping.remove(nameId);
    }
    else if (multi != null) {
      if (multi.length == 2) {
        if (multi[0] == fileId) {
          multiMapping.remove(nameId);
          singularMapping.put(nameId, multi[1]);
        }
        else if (multi[1] == fileId) {
          multiMapping.remove(nameId);
          singularMapping.put(nameId, multi[0]);
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
            multiMapping.remove(nameId);
          }
          else if (len == 1) {
            multiMapping.remove(nameId);
            singularMapping.put(nameId, multi[1]);
          }
          else if (len == 2) {
            multiMapping.put(nameId, new int[]{multi[1], multi[2]});
          }
          else {
            multi[len + 1] = NULL_NAME_ID;
          }
        }
      }
    }
    if (CHECK_CONSISTENCY) {
      checkConsistency(nameId);
    }
  }

  @Override
  @VisibleForTesting
  public void clear() {
    rwLock.writeLock().lock();
    try {
      singularMapping.clear();
      multiMapping.clear();
    }
    finally {
      rwLock.writeLock().unlock();
    }
  }

  @Override
  @VisibleForTesting
  public void checkConsistency() {
    rwLock.readLock().lock();
    try {
      IntIterator keyIt1 = singularMapping.keySet().intIterator();
      while (keyIt1.hasNext()) {
        checkConsistency(keyIt1.nextInt());
      }
      IntIterator keyIt2 = multiMapping.keySet().intIterator();
      while (keyIt2.hasNext()) {
        checkConsistency(keyIt2.nextInt());
      }
    }
    finally {
      rwLock.readLock().unlock();
    }
  }

  private void checkConsistency(int nameId) {
    int single = singularMapping.get(nameId);
    int[] multi = multiMapping.get(nameId);
    if (single != NULL_NAME_ID && multi != null) {
      throw new AssertionError("both single- and multi- entries present");
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
