// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.storage.AbstractRecordsTable;
import com.intellij.util.io.storage.RecordIdIterator;
import com.intellij.util.io.storage.Storage;
import com.intellij.util.io.storage.StorageTestBase;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class CompactStorageTest extends StorageTestBase {
  @NotNull
  @Override
  protected Storage createStorage(String fileName) throws IOException {
    return new CompactStorage(fileName);
  }

  public void testCompactAndIterators() throws IOException {
    TIntArrayList recordsList = new TIntArrayList();
    // 1000 records after deletion greater than 3M limit for init time compaction
    final int recordCount = 2000;
    for(int i = 0; i < recordCount; ++i) recordsList.add(createTestRecord());
    final int physicalRecordCount = myStorage.getLiveRecordsCount();
    for (int i = 0; i < recordCount / 2; ++i) myStorage.deleteRecord(recordsList.getQuick(i));
    int logicalRecordCount = countLiveLogicalRecords();
    assertEquals(recordCount / 2, logicalRecordCount);

    int removedRecordId = recordsList.getQuick(0);
    assertEquals("No content for reading removed record",0, myStorage.readStream(removedRecordId).available());

    Disposer.dispose(myStorage);  // compact is triggered
    myStorage = createStorage(getFileName());
    assertEquals(myStorage.getLiveRecordsCount(), physicalRecordCount / 2);

    logicalRecordCount = 0;

    RecordIdIterator recordIdIterator = myStorage.createRecordIdIterator();
    while(recordIdIterator.hasNextId()) {
      boolean validId = recordIdIterator.validId();
      int nextId = recordIdIterator.nextId();
      if (!validId) continue;
      ++logicalRecordCount;
      checkTestRecord(nextId);
    }

    assertEquals(recordCount / 2, logicalRecordCount);
  }

  protected int countLiveLogicalRecords() throws IOException {
    RecordIdIterator recordIdIterator = myStorage.createRecordIdIterator();
    int logicalRecordCount = 0;

    while(recordIdIterator.hasNextId()) {
      boolean validId = recordIdIterator.validId();
      recordIdIterator.nextId();
      if (!validId) continue;
      ++logicalRecordCount;
    }
    return logicalRecordCount;
  }

  private static final int TIMES_LIMIT = 10000;

  private int createTestRecord() throws IOException {
    return createTestRecord(myStorage);
  }

  static  int createTestRecord(Storage storage) throws IOException {
    final int r = storage.createNewRecord();

    try (DataOutputStream out = new DataOutputStream(storage.appendStream(r))) {
      Random random = new Random(r);
      for (int i = 0; i < TIMES_LIMIT; i++) {
        out.writeInt(random.nextInt());
      }
    }

    return r;
  }

  void checkTestRecord(int id) throws IOException {
    try (DataInputStream stream = myStorage.readStream(id)) {
      Random random = new Random(id);
      for (int i = 0; i < TIMES_LIMIT; i++) {
        assertEquals(random.nextInt(), stream.readInt());
      }
    }
  }

  static class CompactStorage extends Storage {
    CompactStorage(String fileName) throws IOException {
      super(fileName);
    }

    @Override
    protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
      return new CompactRecordsTable(recordsFile, pool, false);
    }
  }
}