// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.BitUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags.MASK;
import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags.FREE_RECORD_FLAG;

final class PersistentFSRecordAccessor {
  private static final Logger LOG = Logger.getInstance(PersistentFSRecordAccessor.class);

  private final PersistentFSContentAccessor myPersistentFSContentAccessor;
  private final PersistentFSAttributeAccessor myPersistentFSAttributeAccessor;
  private final PersistentFSConnection myFSConnection;
  private final Object myNewFreeRecordsSync = new Object();
  private final IntList myNewFreeRecords = IntLists.synchronize(new IntArrayList(), myNewFreeRecordsSync);

  PersistentFSRecordAccessor(@NotNull PersistentFSContentAccessor contentAccessor,
                             @NotNull PersistentFSAttributeAccessor attributeAccessor,
                             @NotNull PersistentFSConnection connection) {
    myPersistentFSContentAccessor = contentAccessor;
    myPersistentFSAttributeAccessor = attributeAccessor;
    myFSConnection = connection;
  }

  void addToFreeRecordsList(int id) throws IOException {
    myFSConnection.markDirty();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myNewFreeRecords.add(id);
    }
    // DbConnection.addFreeRecord(id); // important! Do not add fileId to free list until restart
    myFSConnection.getRecords().setFlags(id, FREE_RECORD_FLAG);
    //TODO RC: should we call FSRecords.incModCount(id); here?
  }

  // todo: Address  / capacity store in records table, size store with payload
  int createRecord() throws IOException {
    PersistentFSConnection connection = myFSConnection;
    connection.markDirty();

    final int free = connection.reserveFreeRecord();
    if (free == 0) {
      return allocateRecord();
    }
    else {
      deleteContentAndAttributes(free);
      connection.getRecords().cleanRecord(free);
      return free;
    }
  }

  void checkSanity() throws IOException {
    PersistentFSConnection connection = myFSConnection;
    long t = System.currentTimeMillis();

    final int fileLength = length();
    assert fileLength % PersistentFSRecordsStorage.recordsLength() == 0;
    int recordCount = fileLength / PersistentFSRecordsStorage.recordsLength();

    IntList usedAttributeRecordIds = new IntArrayList();
    IntList validAttributeIds = new IntArrayList();
    for (int id = 2; id < recordCount; id++) {
      int flags = connection.getRecords().getFlags(id);
      LOG.assertTrue((flags & ~MASK) == 0, "Invalid flags: 0x" + Integer.toHexString(flags) + ", id: " + id);
      boolean isFreeRecord = connection.getFreeRecords().contains(id);
      if (BitUtil.isSet(flags, FREE_RECORD_FLAG)) {
        LOG.assertTrue(isFreeRecord, "Record, marked free, not in free list: " + id);
      }
      else {
        LOG.assertTrue(!isFreeRecord, "Record, not marked free, in free list: " + id);
        checkRecordSanity(id, recordCount, usedAttributeRecordIds, validAttributeIds);
      }
    }

    t = System.currentTimeMillis() - t;
    LOG.info("Sanity check took " + t + " ms");
  }

  boolean isDeleted(int id) throws IOException {
    return BitUtil.isSet(myFSConnection.getRecords().getFlags(id), FREE_RECORD_FLAG) || myNewFreeRecords.contains(id);
  }

  private void checkRecordSanity(int id,
                                 int recordCount,
                                 @NotNull IntList usedAttributeRecordIds,
                                 @NotNull IntList validAttributeIds) throws IOException {
    PersistentFSConnection connection = myFSConnection;
    int parentId = connection.getRecords().getParent(id);
    assert parentId >= 0 && parentId < recordCount;
    if (parentId > 0 && connection.getRecords().getParent(parentId) > 0) {
      int parentFlags = connection.getRecords().getFlags(parentId);
      assert !BitUtil.isSet(parentFlags, FREE_RECORD_FLAG) : parentId + ": " + Integer.toHexString(parentFlags);
      assert BitUtil.isSet(parentFlags, PersistentFS.Flags.IS_DIRECTORY) : parentId + ": " + Integer.toHexString(parentFlags);
    }

    CharSequence name = getName(id);
    LOG.assertTrue(parentId == 0 || name.length() != 0, "File with empty name found under " + getName(parentId) + ", id=" + id);

    myPersistentFSContentAccessor.checkContentsStorageSanity(id);
    myPersistentFSAttributeAccessor.checkAttributesStorageSanity(id, usedAttributeRecordIds, validAttributeIds);

    long length = connection.getRecords().getLength(id);
    assert length >= -1 : "Invalid file length found for " + name + ": " + length;
  }

  @NotNull IntList getNewFreeRecords() {
    synchronized (myNewFreeRecordsSync) {
      return new IntArrayList(myNewFreeRecords);
    }
  }

  @Nullable
  private CharSequence getName(int fileId) throws IOException {
    return myFSConnection.getNames().valueOf(myFSConnection.getRecords().getNameId(fileId));
  }

  private int length() {
    return (int)myFSConnection.getRecords().length();
  }

  private int allocateRecord() {
    return myFSConnection.getRecords().allocateRecord();
  }

  private void deleteContentAndAttributes(int id) throws IOException {
    myPersistentFSContentAccessor.deleteContent(id);
    myPersistentFSAttributeAccessor.deleteAttributes(id);
  }
}
