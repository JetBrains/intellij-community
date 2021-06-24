// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.BitUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsStorage.RECORD_SIZE;

final class PersistentFSRecordAccessor {
  private static final Logger LOG = Logger.getInstance(PersistentFSRecordAccessor.class);
  static final int FREE_RECORD_FLAG = 0x400;
  static {
    assert (PersistentFS.Flags.ALL_VALID_FLAGS & FREE_RECORD_FLAG) == 0 : PersistentFS.Flags.ALL_VALID_FLAGS;
  }
  private static final int ALL_VALID_FLAGS = PersistentFS.Flags.ALL_VALID_FLAGS | FREE_RECORD_FLAG;

  @NotNull
  private final PersistentFSContentAccessor myPersistentFSContentAccessor;
  @NotNull
  private final PersistentFSAttributeAccessor myPersistentFSAttributeAccessor;
  private final PersistentFSConnection myFSConnection;
  @NotNull
  private final IntList myNewFreeRecords = new IntArrayList();

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
  }

  // todo: Address  / capacity store in records table, size store with payload
  int createRecord() throws IOException {
    PersistentFSConnection connection = myFSConnection;
    connection.markDirty();

    final int free = connection.getFreeRecord();
    if (free == 0) {
      final int fileLength = length();
      LOG.assertTrue(fileLength % RECORD_SIZE == 0, "record file length = " + fileLength + ", record size = " + RECORD_SIZE);
      int newRecord = fileLength / RECORD_SIZE;
      connection.getRecords().cleanRecord(newRecord);
      assert fileLength + RECORD_SIZE == length();
      return newRecord;
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
    assert fileLength % RECORD_SIZE == 0;
    int recordCount = fileLength / RECORD_SIZE;

    IntList usedAttributeRecordIds = new IntArrayList();
    IntList validAttributeIds = new IntArrayList();
    for (int id = 2; id < recordCount; id++) {
      int flags = connection.getRecords().doGetFlags(id);
      LOG.assertTrue((flags & ~ALL_VALID_FLAGS) == 0, "Invalid flags: 0x" + Integer.toHexString(flags) + ", id: " + id);
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

  private void checkRecordSanity(int id,
                                 int recordCount,
                                 @NotNull IntList usedAttributeRecordIds,
                                 @NotNull IntList validAttributeIds) throws IOException {
    PersistentFSConnection connection = myFSConnection;
    int parentId = connection.getRecords().getParent(id);
    assert parentId >= 0 && parentId < recordCount;
    if (parentId > 0 && connection.getRecords().getParent(parentId) > 0) {
      int parentFlags = connection.getRecords().doGetFlags(parentId);
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
    return myNewFreeRecords;
  }

  @Nullable
  private String getName(int fileId) throws IOException {
    return myFSConnection.getNames().valueOf(myFSConnection.getRecords().getNameId(fileId));
  }

  private int length() {
    return (int)myFSConnection.getRecords().length();
  }

  private void deleteContentAndAttributes(int id) throws IOException {
    myPersistentFSContentAccessor.deleteContent(id);
    myPersistentFSAttributeAccessor.deleteAttributes(id);
  }
}
