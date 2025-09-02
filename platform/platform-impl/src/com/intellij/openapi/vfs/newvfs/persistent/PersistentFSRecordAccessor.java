// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import static com.intellij.openapi.vfs.newvfs.persistent.FSRecords.NULL_FILE_ID;
import static com.intellij.openapi.vfs.newvfs.persistent.InvertedNameIndex.NULL_NAME_ID;
import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags.FREE_RECORD_FLAG;
import static com.intellij.util.io.DataEnumerator.NULL_ID;

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
    connection.records().setFlags(id, FREE_RECORD_FLAG);
  }

  public int createRecord(Iterable<FileIdIndexedStorage> fileIdIndexedStorages) throws IOException {
    PersistentFSRecordsStorage records = connection.records();
    if (!FSRecordsImpl.REUSE_DELETED_FILE_IDS) {
      int newRecordId = records.allocateRecord();

      checkNewRecordIsZero(records, newRecordId);

      return newRecordId;
    }

    int reusedRecordId = connection.reserveFreeRecord();
    if (reusedRecordId < 0) {
      int newRecordId = records.allocateRecord();

      checkNewRecordIsZero(records, newRecordId);

      return newRecordId;
    }
    else {//there is a record for re-use, but let's clean it up first:
      //TODO RC: Actually, it could significantly slow down new record allocation, so it is better to clear all
      //         that async, during free records collection.
      //         We can do that for attributes & content, but we can't do it for fileIdIndexedStorages, since we
      //         don't know the list of FileIdIndexedStorage storages early on -- fileIdIndexedStorages are collected
      //         incrementally, as they are registered.
      //         Actually, by the same reason even current approach is not bulletproof: .createRecord() could be called
      //         _before_ all FileIdIndexedStorage are registered => reusedRecordId in not-yet-registered FileIdIndexedStorage
      //         won't be cleaned.
      //         Alternative solutions could be:
      //         a) Maintain (i.e. persist) list of 'associated storages' inside VFS, and read all them on startup.
      //            Cons: need additional fields in storage header (bytesPerRow) to read the storage content.
      //         b) Clean data associated with removed fileIds on shutdown (when all storages are already registered),
      //            not on startup. Cons: delays shutdown, and increase chance of VFS corruption if app forcibly terminated
      //            due to long shutdown.
      //         c) Don't reuse fileId at all -- just keep allocating new fileIds, and re-build VFS as int32 exhausted
      //            The approach under investigation now (see REUSE_DELETED_FILE_IDS=false feature-flag) probably to be default
      //            in the next releases. Cons: need to monitor fileId exhaustion and schedule VFS rebuild in advance
      deleteContentAndAttributes(reusedRecordId);
      for (FileIdIndexedStorage storage : fileIdIndexedStorages) {
        storage.clear(reusedRecordId);
      }
      records.cleanRecord(reusedRecordId);
      return reusedRecordId;
    }
  }

  private void checkNewRecordIsZero(PersistentFSRecordsStorage records,
                                    int newRecordId) throws IOException {
    int parentId = records.getParent(newRecordId);
    int nameId = records.getNameId(newRecordId);
    int contentId = records.getContentRecordId(newRecordId);
    int attributeRecordId = records.getAttributeRecordId(newRecordId);
    int flags = records.getFlags(newRecordId);
    int modCount = records.getModCount(newRecordId);
    long length = records.getLength(newRecordId);
    long timestamp = records.getTimestamp(newRecordId);

    if (parentId != NULL_FILE_ID || nameId != NULL_NAME_ID || contentId != NULL_ID || attributeRecordId != NULL_ID ||
        flags != 0 || length != 0 || timestamp != 0) {// modCount _should_ be !=0: it is set in .allocateRecord()

      IOException exception = new IOException(
        "new record (id: " + newRecordId + ") has non-empty fields: " +
        "parentId=" + parentId + ", flags=" + flags + ", nameId= " + nameId + ", " +
        "attributeId=" + attributeRecordId + ", contentId=" + contentId + ", " +
        "length=" + length + ", timestamp=" + timestamp + ", " +
        "modCount=" + modCount + " (globalModCount: " + records.getGlobalModCount() + "), " +
        "wasClosedProperly=" + records.wasClosedProperly()
      );

      //IJPL-1016: statistical analysis shows that it is quite likely an OS crash is responsible for most (if not all) of
      //           those 'non-zero records in un-allocated area' errors. So our current approach to that issue:
      //           1) We don't bother reporting these errors in EA anymore, if !wasClosedProperly: consider it
      //              'known issue, won't fix'.
      //           2) We still _do_ report them if there are no signs of a crash, though: maybe there are other causes?
      //           3) We try to fix the problem here -- clean the record -- but we can't be sure that record id wasn't
      //              already used somewhere (i.e. in a children list), so we mark VFS as corrupted anyway, because
      //              these cases are definitely a failure of VFS recovery procedure.

      if (records.wasClosedProperly()) {
        FSRecords.LOG.error(exception);
      }
      else {
        FSRecords.LOG.warn(exception);
      }
      connection.markAsCorruptedAndScheduleRebuild(exception);

      records.cleanRecord(newRecordId);

    }
  }

  public boolean isDeleted(int id) throws IOException {
    //TODO RC: why first condition is not enough? How could recordId be in freeRecords, if it doesn't have FREE_RECORD flag on it?
    int flags = connection.records().getFlags(id);
    return hasDeletedFlag(flags) || newFreeRecords.contains(id);
  }

  public static boolean hasDeletedFlag(int flags) {
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
