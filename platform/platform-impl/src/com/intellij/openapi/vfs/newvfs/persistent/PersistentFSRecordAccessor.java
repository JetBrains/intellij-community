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
  @NotNull
  private final IntList myNewFreeRecords;

  PersistentFSRecordAccessor(@NotNull PersistentFSContentAccessor accessor,
                             @NotNull PersistentFSAttributeAccessor attributeAccessor) {
    myPersistentFSContentAccessor = accessor;
    myPersistentFSAttributeAccessor = attributeAccessor;
    myNewFreeRecords = new IntArrayList();
  }

  void addToFreeRecordsList(int id, @NotNull PersistentFSConnection connection) {
    connection.markDirty();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myNewFreeRecords.add(id);
    }
    // DbConnection.addFreeRecord(id); // important! Do not add fileId to free list until restart
    connection.getRecords().setFlags(id, FREE_RECORD_FLAG);
  }

  // todo: Address  / capacity store in records table, size store with payload
  int createRecord(@NotNull PersistentFSConnection connection) throws IOException {
    connection.markDirty();

    final int free = connection.getFreeRecord();
    if (free == 0) {
      final int fileLength = length(connection);
      LOG.assertTrue(fileLength % RECORD_SIZE == 0);
      int newRecord = fileLength / RECORD_SIZE;
      connection.getRecords().cleanRecord(newRecord);
      assert fileLength + RECORD_SIZE == length(connection);
      return newRecord;
    }
    else {
      deleteContentAndAttributes(free, connection);
      connection.getRecords().cleanRecord(free);
      return free;
    }
  }

  void checkSanity(@NotNull PersistentFSConnection connection) throws IOException {
    long t = System.currentTimeMillis();

    final int fileLength = length(connection);
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
        checkRecordSanity(id, recordCount, usedAttributeRecordIds, validAttributeIds, connection);
      }
    }

    t = System.currentTimeMillis() - t;
    LOG.info("Sanity check took " + t + " ms");
  }

  private void checkRecordSanity(int id,
                                 int recordCount,
                                 @NotNull IntList usedAttributeRecordIds,
                                 @NotNull IntList validAttributeIds,
                                 @NotNull PersistentFSConnection connection) throws IOException {
    int parentId = connection.getRecords().getParent(id);
    assert parentId >= 0 && parentId < recordCount;
    if (parentId > 0 && connection.getRecords().getParent(parentId) > 0) {
      int parentFlags = connection.getRecords().doGetFlags(parentId);
      assert !BitUtil.isSet(parentFlags, FREE_RECORD_FLAG) : parentId + ": " + Integer.toHexString(parentFlags);
      assert BitUtil.isSet(parentFlags, PersistentFS.Flags.IS_DIRECTORY) : parentId + ": " + Integer.toHexString(parentFlags);
    }

    CharSequence name = getName(id, connection);
    LOG.assertTrue(parentId == 0 || name.length() != 0, "File with empty name found under " + getName(parentId, connection) + ", id=" + id);

    myPersistentFSContentAccessor.checkContentsStorageSanity(id, connection);
    myPersistentFSAttributeAccessor.checkAttributesStorageSanity(id, usedAttributeRecordIds, validAttributeIds, connection);

    long length = connection.getRecords().getLength(id);
    assert length >= -1 : "Invalid file length found for " + name + ": " + length;
  }

  @NotNull IntList getNewFreeRecords() {
    return myNewFreeRecords;
  }

  @Nullable
  private static String getName(int fileId, @NotNull PersistentFSConnection connection) throws IOException {
    return connection.getNames().valueOf(connection.getRecords().getNameId(fileId));
  }

  private static int length(@NotNull PersistentFSConnection connection) {
    return (int)connection.getRecords().length();
  }

  private void deleteContentAndAttributes(int id,
                                          @NotNull PersistentFSConnection connection) throws IOException {
    myPersistentFSContentAccessor.deleteContent(id, connection);
    myPersistentFSAttributeAccessor.deleteAttributes(id, connection);
  }
}
