// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl.FileIdIndexedStorage;
import com.intellij.util.BitUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags.FREE_RECORD_FLAG;

/**
 * This class responsibility is record allocation/deletion/re-use.
 * <br>
 * In production env. it marks deleted records with {@linkplain PersistentFS.Flags#FREE_RECORD_FLAG}, which will be
 * used after restart by {@linkplain  PersistentFSConnection} to build free-list of record IDs to
 * reuse.
 * <br>
 * In unit-tests env. this class, in addition to marking deleted records, also adds them to its own
 * free-list. That free-list is not utilized for re-use, but for various sanity-checking activities
 * during the testing.
 */
@ApiStatus.Internal
public final class PersistentFSRecordAccessor {
  private static final Logger LOG = Logger.getInstance(PersistentFSRecordAccessor.class);


  private final @NotNull PersistentFSConnection connection;
  private final @NotNull PersistentFSContentAccessor contentAccessor;
  private final @NotNull PersistentFSAttributeAccessor attributeAccessor;

  private final Object newFreeRecordsSync = new Object();

  private final IntList newFreeRecords = IntLists.synchronize(new IntArrayList(), newFreeRecordsSync);


  PersistentFSRecordAccessor(@NotNull PersistentFSContentAccessor contentAccessor,
                             @NotNull PersistentFSAttributeAccessor attributeAccessor,
                             @NotNull PersistentFSConnection connection) {
    this.contentAccessor = contentAccessor;
    this.attributeAccessor = attributeAccessor;
    this.connection = connection;
  }

  public void markRecordAsDeleted(int id) throws IOException {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      newFreeRecords.add(id);
    }
    // important! Do not add fileId to free list until restart
    connection.getRecords().setFlags(id, FREE_RECORD_FLAG);
    connection.markDirty();
  }

  public int createRecord(Iterable<FileIdIndexedStorage> fileIdIndexedStorages) throws IOException {
    connection.markDirty();

    if (!FSRecordsImpl.REUSE_DELETED_FILE_IDS) {
      return connection.getRecords().allocateRecord();
    }

    final int reusedRecordId = connection.reserveFreeRecord();
    if (reusedRecordId < 0) {
      return connection.getRecords().allocateRecord();
    }
    else {//there is a record for re-use, but let's clean it up first:
      //TODO RC: Actually, it could significantly slow down new record allocation, so it is better to clear all
      //         that async, during free records collection.
      //         We can do that for attributes & content, but we can't do it for fileIdIndexedStorages, since we
      //         don't know the list of FileIdIndexedStorage storages early on -- fileIdIndexedStorages are collected
      //         incrementally, as they are registered.
      //         Actually, by the same reason even current approach is not bulletproof: since .createRecord() could
      //         be called _before_ all FileIdIndexedStorage are registered, hence reusedRecordId in not-yet-registered
      //         FileIdIndexedStorage won't be cleaned.
      //         Alternative solutions could be:
      //         a) Maintain (i.e. persist) list of 'associated storages' inside VFS, and read all them on startup.
      //            Need additional fields in storage header (bytesPerRow) to read the storage content.
      //         b) Clean data associated with removed fileIds on shutdown (when all storages are already registered),
      //            not on startup. Delays shutdown, and increase chance of VFS corruption if app forcibly terminated
      //            due to long shutdown.
      //         c) Don't reuse fileId at all -- just keep allocating new fileIds, and re-build VFS as int32 exhausted
      //            Actually, quite interesting approach, now under REUSE_DELETED_FILE_IDS feature-flag, but needs careful examination
      //            (for the next release?)
      deleteContentAndAttributes(reusedRecordId);
      for (FileIdIndexedStorage storage : fileIdIndexedStorages) {
        storage.clear(reusedRecordId);
      }
      connection.getRecords().cleanRecord(reusedRecordId);
      return reusedRecordId;
    }
  }

  public boolean isDeleted(int id) throws IOException {
    //TODO RC: why first condition is not enough? How could recordId be in freeRecords, if it doesn't have FREE_RECORD flag on it?
    final int flags = connection.getRecords().getFlags(id);
    return hasDeletedFlag(flags) || newFreeRecords.contains(id);
  }

  public static boolean hasDeletedFlag(final int flags) {
    return BitUtil.isSet(flags, FREE_RECORD_FLAG);
  }

  public @NotNull IntList getNewFreeRecords() {
    synchronized (newFreeRecordsSync) {
      return new IntArrayList(newFreeRecords);
    }
  }

  //=== internals:

  private void deleteContentAndAttributes(int id) throws IOException {
    contentAccessor.deleteContent(id);
    attributeAccessor.deleteAttributes(id);
  }
}
