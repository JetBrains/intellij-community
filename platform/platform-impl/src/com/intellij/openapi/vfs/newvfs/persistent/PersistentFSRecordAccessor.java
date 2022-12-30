// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.BitUtil;
import com.intellij.util.TimeoutUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
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
final class PersistentFSRecordAccessor {
  private static final Logger LOG = Logger.getInstance(PersistentFSRecordAccessor.class);

  //static {
  //  assert (PersistentFS.Flags.MASK & FREE_RECORD_FLAG) == 0 : PersistentFS.Flags.MASK;
  //}

  private static final int ALL_VALID_FLAGS = PersistentFS.Flags.MASK;

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

  //RC: method name is a bit misleading, since really (in production) it doesn't add record to free-list
  //    -- it does that only in unit-tests.
  //    AFM: name like deleteRecord(id) would suit better
  public void addToFreeRecordsList(int id) throws IOException {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myNewFreeRecords.add(id);
    }
    // DbConnection.addFreeRecord(id); // important! Do not add fileId to free list until restart
    myFSConnection.getRecords().setFlags(id, FREE_RECORD_FLAG);
    myFSConnection.markDirty();
  }

  // todo: Address  / capacity store in records table, size store with payload
  public int createRecord() throws IOException {
    PersistentFSConnection connection = myFSConnection;
    connection.markDirty();

    final int reusedRecordId = connection.reserveFreeRecord();
    if (reusedRecordId < 0) {
      return myFSConnection.getRecords().allocateRecord();
    }
    else {//there is a record for re-use, but let's clean it up first:
      deleteContentAndAttributes(reusedRecordId);
      connection.getRecords().cleanRecord(reusedRecordId);
      return reusedRecordId;
    }
  }

  public void checkSanity() throws IOException {
    PersistentFSConnection connection = myFSConnection;
    final long startedAtNs = System.nanoTime();

    final int fileLength = recordsFileLength();
    assert fileLength % PersistentFSRecordsStorage.recordsLength() == 0;
    final int recordCount = fileLength / PersistentFSRecordsStorage.recordsLength();

    final IntList usedAttributeRecordIds = new IntArrayList();
    final IntList validAttributeIds = new IntArrayList();
    final IntList freeRecords = connection.getFreeRecords();
    //FIXME RC: here we start from record #2 because 0-th record is used for header, and 1st record is used for root, which is also
    //          somehow special, hence we skip its validation here. I think, this is kind of sacred knowledge that should be either
    //          made explicit, or encapsulated
    for (int id = 2; id < recordCount; id++) {
      final int flags = connection.getRecords().getFlags(id);
      LOG.assertTrue((flags & ~ALL_VALID_FLAGS) == 0, "Invalid flags: 0x" + Integer.toHexString(flags) + ", id: " + id);

      final boolean recordInFreeList = freeRecords.contains(id);
      final boolean recordMarkedAsFree = hasDeletedFlag(flags);
      if (recordMarkedAsFree) {
        LOG.assertTrue(recordInFreeList, "Record, marked free, not in free list: " + id);
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
    int parentId = connection.getRecords().getParent(id);
    assert parentId >= 0 && parentId < totalRecordCount;
    if (parentId > 0 && connection.getRecords().getParent(parentId) > 0) {
      int parentFlags = connection.getRecords().getFlags(parentId);
      assert !hasDeletedFlag(parentFlags) : parentId + ": " + Integer.toHexString(parentFlags);
      assert BitUtil.isSet(parentFlags, PersistentFS.Flags.IS_DIRECTORY) : parentId + ": " + Integer.toHexString(parentFlags);
    }

    CharSequence name = getName(id);
    LOG.assertTrue(parentId == 0 || name.length() != 0, "File with empty name found under " + getName(parentId) + ", id=" + id);

    myPersistentFSContentAccessor.checkContentsStorageSanity(id);
    myPersistentFSAttributeAccessor.checkAttributesStorageSanity(id, usedAttributeRecordIds, validAttributeIds);

    long length = connection.getRecords().getLength(id);
    assert length >= -1 : "Invalid file length found for " + name + ": " + length;
  }

  @Nullable
  private CharSequence getName(int fileId) throws IOException {
    return myFSConnection.getNames().valueOf(myFSConnection.getRecords().getNameId(fileId));
  }

  private int recordsFileLength() {
    return (int)myFSConnection.getRecords().length();
  }

  private void deleteContentAndAttributes(int id) throws IOException {
    myPersistentFSContentAccessor.deleteContent(id);
    myPersistentFSAttributeAccessor.deleteAttributes(id);
  }
}
