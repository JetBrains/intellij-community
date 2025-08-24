// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.vfs.newvfs.persistent.InvertedNameIndex;
import com.intellij.platform.util.io.storages.intmultimaps.DurableIntToMultiIntMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntPredicate;

/**
 * {@link InvertedNameIndex} implementation over int-to-int multimap, {@link DurableIntToMultiIntMap}.
 *
 * @implNote In theory, I expected this implementation to be faster and more compact than {@link com.intellij.openapi.vfs.newvfs.persistent.DefaultInMemoryInvertedNameIndex},
 * but in practice it happens to be the opposite: open-addressing multimap tends to grow very large to accomodate clustering
 * caused by many-values-per-key, and as a result it is much larger and significantly slower than default impl.
 */
@ApiStatus.Internal
public class InvertedNameIndexOverIntToIntMultimap implements InvertedNameIndex {

  private final DurableIntToMultiIntMap nameIdToFileId;

  /** Some {@link DurableIntToMultiIntMap} implementations have their own locking, but we can't rely on that */
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  public InvertedNameIndexOverIntToIntMultimap(@NotNull DurableIntToMultiIntMap map) { nameIdToFileId = map; }

  @Override
  public boolean forEachFileIds(@NotNull IntCollection nameIds,
                                @NotNull IntPredicate fileIdProcessor) {
    Lock readLock = rwLock.readLock();
    try {
      for (IntIterator it = nameIds.intIterator(); it.hasNext(); ) {
        int nameId = it.nextInt();
        readLock.lock();
        try {
          //fileIdProcessor returning false means 'stop here' in forEachFileIds(),
          // but ValueAcceptor returning false means 'continue looking up':
          int acceptedFileId = nameIdToFileId.lookup(nameId, fileId -> !fileIdProcessor.test(fileId));
          if (acceptedFileId != DurableIntToMultiIntMap.NO_VALUE) {
            return false;
          }
        }
        finally {
          readLock.unlock();
        }
      }
      return true;
    }
    catch (IOException e) {
      throw new UncheckedIOException("nameIds: " + nameIds, e);
    }
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
        nameIdToFileId.remove(oldNameId, fileId);
      }
      if (newNameId != NULL_NAME_ID) {
        nameIdToFileId.put(newNameId, fileId);
      }
    }
    catch (IOException e) {
      throw new UncheckedIOException(".updateFileName(" + fileId + ", " + newNameId + "," + oldNameId + ")", e);
    }
    finally {
      writeLock.unlock();
    }
  }

  @Override
  public void clear() {
    Lock writeLock = rwLock.writeLock();
    writeLock.lock();
    try {
      nameIdToFileId.clear();
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    finally {
      writeLock.unlock();
    }
  }

  @Override
  public void checkConsistency() {
    //nothing?
  }
}
