// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.BitUtil;
import com.intellij.util.TimeoutUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @NotNull
  private final PersistentFSContentAccessor myPersistentFSContentAccessor;
  @NotNull
  private final PersistentFSAttributeAccessor myPersistentFSAttributeAccessor;
  private final PersistentFSConnection myFSConnection;

  private final Object myNewFreeRecordsSync = new Object();
  @NotNull
  private final IntList myNewFreeRecords = IntLists.synchronize(new IntArrayList(), myNewFreeRecordsSync);


  PersistentFSRecordAccessor(@NotNull PersistentFSContentAccessor contentAccessor,
                             @NotNull PersistentFSAttributeAccessor attributeAccessor,
                             @NotNull PersistentFSConnection connection) {
    myPersistentFSContentAccessor = contentAccessor;
    myPersistentFSAttributeAccessor = attributeAccessor;
    myFSConnection = connection;
  }

  public void markRecordAsDeleted(int id) throws IOException {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myNewFreeRecords.add(id);
    }
    // DbConnection.addFreeRecord(id); // important! Do not add fileId to free list until restart
    myFSConnection.getRecords().setFlags(id, FREE_RECORD_FLAG);
    myFSConnection.markDirty();
  }

  public int createRecord() throws IOException {
    myFSConnection.markDirty();

    final int reusedRecordId = myFSConnection.reserveFreeRecord();
    if (reusedRecordId < 0) {
      return myFSConnection.getRecords().allocateRecord();
    }
    else {//there is a record for re-use, but let's clean it up first:
      deleteContentAndAttributes(reusedRecordId);
      myFSConnection.getRecords().cleanRecord(reusedRecordId);
      return reusedRecordId;
    }
  }

  public void checkSanity() throws IOException {
    final long startedAtNs = System.nanoTime();

    final int recordCount = myFSConnection.getRecords().recordsCount();

    final IntList usedAttributeRecordIds = new IntArrayList();
    final IntList validAttributeIds = new IntArrayList();
    final IntList freeRecords = myFSConnection.getFreeRecords();
    for (int id = FSRecords.MIN_REGULAR_FILE_ID; id < recordCount; id++) {
      final int flags = myFSConnection.getRecords().getFlags(id);
      LOG.assertTrue((flags & ~PersistentFS.Flags.getAllValidFlags()) == 0, "Invalid flags: 0x" + Integer.toHexString(flags) + ", id: " + id);

      final boolean recordInFreeList = freeRecords.contains(id);
      final boolean recordMarkedAsFree = hasDeletedFlag(flags);
      if (recordMarkedAsFree) {
        //Record is marked free, but not in free list: it is OK, we fill freeList on VFS load only,
        //  so records free-ed in current session are not in it
      }
      else {
        LOG.assertTrue(!recordInFreeList, "Record, not marked free, in free list: " + id);
        //if record is not free -> check its attributes sanity
        checkRecordSanity(id, recordCount, usedAttributeRecordIds, validAttributeIds);
      }
    }

    LOG.info("Sanity check took " + TimeoutUtil.getDurationMillis(startedAtNs) + " ms");
  }

  public boolean isDeleted(int id) throws IOException {
    //TODO RC: why first condition is not enough? How could recordId be in freeRecords, if it doesn't have FREE_RECORD flag on it?
    final int flags = myFSConnection.getRecords().getFlags(id);
    return hasDeletedFlag(flags) || myNewFreeRecords.contains(id);
  }

  public static boolean hasDeletedFlag(final int flags) {
    return BitUtil.isSet(flags, FREE_RECORD_FLAG);
  }

  public @NotNull IntList getNewFreeRecords() {
    synchronized (myNewFreeRecordsSync) {
      return new IntArrayList(myNewFreeRecords);
    }
  }

  //=== internals:

  private void checkRecordSanity(int id,
                                 int totalRecordCount,
                                 @NotNull IntList usedAttributeRecordIds,
                                 @NotNull IntList validAttributeIds) throws IOException {
    PersistentFSConnection connection = myFSConnection;
    PersistentFSRecordsStorage records = connection.getRecords();
    int parentId = records.getParent(id);
    assert parentId >= 0 && parentId < totalRecordCount;
    if (parentId > 0 && records.getParent(parentId) > 0) {
      int parentFlags = records.getFlags(parentId);
      assert !hasDeletedFlag(parentFlags) : parentId + ": " + Integer.toHexString(parentFlags);
      assert BitUtil.isSet(parentFlags, PersistentFS.Flags.IS_DIRECTORY) : parentId + ": " + Integer.toHexString(parentFlags);
    }

    CharSequence name = getName(id);
    if(parentId != 0 && name.isEmpty()) {
      LOG.error("File[" + id + "] with empty name found under parent[" + parentId + "][name:" + getName(parentId) + "]");
    }

    myPersistentFSContentAccessor.checkContentsStorageSanity(id);
    myPersistentFSAttributeAccessor.checkAttributesStorageSanity(id, usedAttributeRecordIds, validAttributeIds);

    long length = records.getLength(id);
    assert length >= -1 : "Invalid file length found for " + name + ": " + length;
  }

  @Nullable
  private CharSequence getName(int fileId) throws IOException {
    return myFSConnection.getNames().valueOf(myFSConnection.getRecords().getNameId(fileId));
  }

  private void deleteContentAndAttributes(int id) throws IOException {
    myPersistentFSContentAccessor.deleteContent(id);
    myPersistentFSAttributeAccessor.deleteAttributes(id);
  }
}
