/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.persistent.CompactRecordsTable;
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

    DataOutputStream out = new DataOutputStream(storage.appendStream(r));
    try {
      Random random = new Random(r);
      for (int i = 0; i < TIMES_LIMIT; i++) {
        out.writeInt(random.nextInt());
      }
    }
    finally {
      out.close();
    }

    return r;
  }

  void checkTestRecord(int id) throws IOException {
    DataInputStream stream = myStorage.readStream(id);
    try {
      Random random = new Random(id);
      for (int i = 0; i < TIMES_LIMIT; i++) {
        assertEquals(random.nextInt(), stream.readInt());
      }
    } finally {
      stream.close();
    }
  }

  static class CompactStorage extends Storage {
    public CompactStorage(String fileName) throws IOException {
      super(fileName);
    }

    @Override
    protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
      return new CompactRecordsTable(recordsFile, pool, false);
    }
  }
}