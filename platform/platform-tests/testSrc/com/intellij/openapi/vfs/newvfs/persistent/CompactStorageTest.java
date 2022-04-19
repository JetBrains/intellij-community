// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.PagePool;
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

public class CompactStorageTest extends StorageTestBase {
  @NotNull
  @Override
  protected Storage createStorage(@NotNull Path fileName) throws IOException {
    return new CompactStorage(fileName);
  }

  @Test
  public void testCompactAndIterators() throws IOException {
    IntList recordsList = new IntArrayList();
    // 1000 records after deletion greater than 3M limit for init time compaction
    final int recordCount = 2000;
    for (int i = 0; i < recordCount; ++i) {
      recordsList.add(createTestRecord(myStorage));
    }
    final int physicalRecordCount = myStorage.getLiveRecordsCount();
    for (int i = 0; i < recordCount / 2; ++i) {
      myStorage.deleteRecord(recordsList.getInt(i));
    }
    int logicalRecordCount = countLiveLogicalRecords();
    assertThat(logicalRecordCount).isEqualTo(recordCount / 2);

    int removedRecordId = recordsList.getInt(0);
    assertThat(myStorage.readStream(removedRecordId).available()).describedAs("No content for reading removed record").isEqualTo(0);

    // compact is triggered
    Disposer.dispose(myStorage);
    setUpStorage();
    assertThat(myStorage.getLiveRecordsCount()).isEqualTo(physicalRecordCount / 2);

    logicalRecordCount = 0;

    RecordIdIterator recordIdIterator = myStorage.createRecordIdIterator();
    while (recordIdIterator.hasNextId()) {
      boolean validId = recordIdIterator.validId();
      int nextId = recordIdIterator.nextId();
      if (!validId) continue;
      ++logicalRecordCount;
      checkTestRecord(nextId);
    }

    assertThat(logicalRecordCount).isEqualTo(recordCount / 2);
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

  private void checkTestRecord(int id) throws IOException {
    try (DataInputStream stream = myStorage.readStream(id)) {
      Random random = new Random(id);
      for (int i = 0; i < TIMES_LIMIT; i++) {
        assertThat(stream.readInt()).isEqualTo(random.nextInt());
      }
    }
  }

  static final class CompactStorage extends Storage {
    CompactStorage(@NotNull Path fileName) throws IOException {
      super(fileName);
    }

    @Override
    protected AbstractRecordsTable createRecordsTable(@NotNull PagePool pool, @NotNull Path recordsFile) throws IOException {
      return new CompactRecordsTable(recordsFile, pool, false);
    }
  }
}