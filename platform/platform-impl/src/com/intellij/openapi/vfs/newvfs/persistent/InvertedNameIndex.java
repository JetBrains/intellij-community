// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.io.DataEnumerator;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntPredicate;

import static com.intellij.util.SystemProperties.getBooleanProperty;

/**
 * Index for lookup fileName -> fileId(s). Supposed to be replacement of {@link com.intellij.psi.search.FilenameIndex}?
 * <p>
 * Data layout is either single entry or multiple entries, not both:
 * <ul>
 *   <li>single {@code nameId->fileId} entry in {@link InvertedNameIndex#singularMapping}</li>
 *   <li>multiple entries in {@link InvertedNameIndex#multiMapping} in 2 possible formats:
 *     <ul>
 *       <li>{@code nameId->(fileId1, fileId2)}</li>
 *       <li>{@code nameId->(N, fileId1, ..., fileIdN, 0, ... 0)}</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @see InvertedNameIndex#checkConsistency
 */
@ApiStatus.Internal
public final class InvertedNameIndex {
  /**
   * id=0 used as NULL (i.e. absent) value
   */
  public static final int NULL_NAME_ID = DataEnumerator.NULL_ID;

  private final boolean CHECK_CONSISTENCY = getBooleanProperty("idea.vfs.name.index.check.consistency", false);

  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  /** [nameId -> fileId] mapping then fileId is unique -- i.e. only 1 file has such a name */
  private final Int2IntMap singularMapping = new Int2IntOpenHashMap();
  /**
   * [nameId -> (array of fileId)] mapping then fileId is NOT unique -- i.e. >1 files have such a name
   * (The exact array format is complicated, see class javadoc)
   */
  private final Int2ObjectMap<int[]> multiMapping = new Int2ObjectOpenHashMap<>();


  boolean processFilesWithNames(@NotNull IntList namesIds, @NotNull IntPredicate processor) {
    rwLock.readLock().lock();
    try {
      return processData(namesIds, processor);
    }
    finally {
      rwLock.readLock().unlock();
    }
  }

  public void updateFileName(int fileId, int newNameId, int oldNameId) {
    rwLock.writeLock().lock();
    try {
      if (oldNameId != NULL_NAME_ID) {
        deleteDataInner(fileId, oldNameId);
      }
      if (newNameId != NULL_NAME_ID) {
        updateDataInner(fileId, newNameId);
      }
    }
    finally {
      rwLock.writeLock().unlock();
    }
  }

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

  /**
   * Add fileId to the list of fileIds associated with nameId
   */
  void updateDataInner(int fileId, int nameId) {
    rwLock.writeLock().lock();
    try {
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
    finally {
      rwLock.writeLock().unlock();
    }
  }

  @VisibleForTesting
  public boolean forEachFileIds(final @NotNull IntSet nameIds, final @NotNull IntPredicate processor) {
    final IntIterator it = nameIds.iterator();
    while (it.hasNext()) {
      final int nameId = it.nextInt();
      final int single = singularMapping.get(nameId);
      final int[] multi;
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

  private boolean processData(@NotNull IntList namesIds,
                              @NotNull IntPredicate processor) {
    for (IntIterator it = namesIds.iterator(); it.hasNext(); ) {
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
