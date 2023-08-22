// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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

  public int createRecord() throws IOException {
    connection.markDirty();

    final int reusedRecordId = connection.reserveFreeRecord();
    if (reusedRecordId < 0) {
      return connection.getRecords().allocateRecord();
    }
    else {//there is a record for re-use, but let's clean it up first:
      deleteContentAndAttributes(reusedRecordId);
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
