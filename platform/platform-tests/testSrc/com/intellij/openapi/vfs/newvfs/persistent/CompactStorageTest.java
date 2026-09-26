// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.storage.AbstractRecordsTable;
import com.intellij.util.io.storage.RecordIdIterator;
import com.intellij.util.io.storage.Storage;
import com.intellij.util.io.storage.StorageTestBase;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class CompactStorageTest extends StorageTestBase {

  private static final int CREATED_RECORDS_COUNT = 2000;

  @Override
  protected @NotNull Storage createStorage(@NotNull Path fileName) throws IOException {
    return new CompactStorage(fileName);
  }

  @Test
  public void recordsInserted_couldBeReadBackViaIterator() throws IOException {
    for (int i = 0; i < CREATED_RECORDS_COUNT; ++i) {
      createTestRecord(myStorage);
    }
    assertThat(countLiveLogicalRecords()).isEqualTo(CREATED_RECORDS_COUNT);

    RecordIdIterator recordIdIterator = myStorage.createRecordIdIterator();
    while (recordIdIterator.hasNextId()) {
      boolean validId = recordIdIterator.validId();
      int nextId = recordIdIterator.nextId();
      if (!validId) continue;
      checkTestRecord(nextId);
    }
  }

  @Test
  public void recordsInserted_couldBeReadBackViaIterator_afterReopen() throws IOException {
    for (int i = 0; i < CREATED_RECORDS_COUNT; ++i) {
      createTestRecord(myStorage);
    }
    assertThat(countLiveLogicalRecords()).isEqualTo(CREATED_RECORDS_COUNT);

    //reopen storage:
    Disposer.dispose(myStorage);
    setUpStorage();
    assertThat(countLiveLogicalRecords()).isEqualTo(CREATED_RECORDS_COUNT);

    RecordIdIterator recordIdIterator = myStorage.createRecordIdIterator();
    while (recordIdIterator.hasNextId()) {
      boolean validId = recordIdIterator.validId();
      int nextId = recordIdIterator.nextId();
      if (!validId) continue;
      checkTestRecord(nextId);
    }
  }

  @Test
  public void deletedRecords_areSkippedDuringIteration() throws IOException {
    IntList recordsList = new IntArrayList();
    for (int i = 0; i < CREATED_RECORDS_COUNT; i++) {
      recordsList.add(createTestRecord(myStorage));
    }
    for (int i = 0; i < CREATED_RECORDS_COUNT / 2; i++) {
      myStorage.deleteRecord(recordsList.getInt(i));
    }
    int logicalRecordCount = countLiveLogicalRecords();
    assertThat(logicalRecordCount).isEqualTo(CREATED_RECORDS_COUNT / 2);

    int removedRecordId = recordsList.getInt(0);
    assertThat(myStorage.readStream(removedRecordId).available())
      .describedAs("No content for reading removed record")
      .isEqualTo(0);

    RecordIdIterator recordIdIterator = myStorage.createRecordIdIterator();
    while (recordIdIterator.hasNextId()) {
      boolean validId = recordIdIterator.validId();
      int nextId = recordIdIterator.nextId();
      if (!validId) continue;
      checkTestRecord(nextId);
    }

    assertThat(countLiveLogicalRecords()).isEqualTo(CREATED_RECORDS_COUNT / 2);
  }

  @Test
  public void testCompactAndIterators() throws IOException {
    IntList recordsList = new IntArrayList();
    // 1000 records after deletion greater than 3M limit for init time compaction
    for (int i = 0; i < CREATED_RECORDS_COUNT; i++) {
      recordsList.add(createTestRecord(myStorage));
    }
    int physicalRecordCount = myStorage.getLiveRecordsCount();
    for (int i = 0; i < CREATED_RECORDS_COUNT / 2; i++) {
      myStorage.deleteRecord(recordsList.getInt(i));
    }
    int logicalRecordCount = countLiveLogicalRecords();
    assertThat(logicalRecordCount).isEqualTo(CREATED_RECORDS_COUNT / 2);

    int removedRecordId = recordsList.getInt(0);
    assertThat(myStorage.readStream(removedRecordId).available()).describedAs("No content for reading removed record").isEqualTo(0);

    // compact is triggered
    assertThat(myStorage.getLiveRecordsCount()).isEqualTo(physicalRecordCount / 2);
    Disposer.dispose(myStorage);
    setUpStorage();
    assertThat(myStorage.getLiveRecordsCount()).isEqualTo(physicalRecordCount / 2);


    RecordIdIterator recordIdIterator = myStorage.createRecordIdIterator();
    while (recordIdIterator.hasNextId()) {
      boolean validId = recordIdIterator.validId();
      int nextId = recordIdIterator.nextId();
      if (!validId) continue;
      checkTestRecord(nextId);
    }

    assertThat(countLiveLogicalRecords()).isEqualTo(CREATED_RECORDS_COUNT / 2);
  }

  protected int countLiveLogicalRecords() throws IOException {
    RecordIdIterator recordIdIterator = myStorage.createRecordIdIterator();
    int logicalRecordCount = 0;
    while (recordIdIterator.hasNextId()) {
      boolean validId = recordIdIterator.validId();
      recordIdIterator.nextId();
      if (!validId) continue;
      logicalRecordCount++;
    }
    return logicalRecordCount;
  }

  private static final int INTS_PER_RECORD = 10000;

  /** Creates a new record in the storage and fills it with TIMES_LIMIT ints generated by `Random(recordId).nextInt()` */
  static int createTestRecord(@NotNull Storage storage) throws IOException {
    int recordId = storage.createNewRecord();

    try (DataOutputStream out = new DataOutputStream(storage.appendStream(recordId))) {
      Random random = new Random(recordId);
      for (int i = 0; i < INTS_PER_RECORD; i++) {
        out.writeInt(random.nextInt());
      }
    }

    return recordId;
  }

  private void checkTestRecord(int id) throws IOException {
    try (DataInputStream stream = myStorage.readStream(id)) {
      Random random = new Random(id);
      for (int i = 0; i < INTS_PER_RECORD; i++) {
        int expected = random.nextInt();
        int actual = stream.readInt();
        assertEquals(
          "[id: " + id + "][offset: " + i + "]",
          expected,
          actual
        );
      }
    }
  }

  static final class CompactStorage extends Storage {
    CompactStorage(@NotNull Path fileName) throws IOException {
      super(fileName);
    }

    @Override
    protected AbstractRecordsTable createRecordsTable(@NotNull StorageLockContext pool, @NotNull Path recordsFile) throws IOException {
      return new CompactRecordsTable(recordsFile, pool, false);
    }
  }
}