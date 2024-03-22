// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;


import com.intellij.util.io.PageCacheUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsOverLockFreePagedStorage.NULL_ID;
import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsOverLockFreePagedStorage.RECORD_SIZE_IN_BYTES;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class PersistentFSRecordsStorageOverLockFreePagedStorageTest
  extends PersistentFSRecordsStorageTestBase<PersistentFSRecordsOverLockFreePagedStorage> {

  @Parameterized.Parameters(name = "{index}: {0}")
  public static UpdateAPIMethod[] METHODS_TO_TEST() {
    return new UpdateAPIMethod[]{
      DEFAULT_API_UPDATE_METHOD,
      MODERN_API_UPDATE_METHOD
    };
  }

  public static final int MAX_RECORDS_TO_INSERT = 1 << 22;

  private static final int PAGE_SIZE = PageCacheUtils.DEFAULT_PAGE_SIZE;


  public PersistentFSRecordsStorageOverLockFreePagedStorageTest(final UpdateAPIMethod updateMethodToTest) { super(MAX_RECORDS_TO_INSERT, updateMethodToTest); }

  @BeforeClass
  public static void beforeClass() throws Exception {
    assumeTrue(
      "LockFree FilePageCache must be enabled: see PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED",
      PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED
    );
  }

  @NotNull
  @Override
  protected PersistentFSRecordsOverLockFreePagedStorage openStorage(final Path storagePath) throws IOException {
    return (PersistentFSRecordsOverLockFreePagedStorage)PersistentFSRecordsStorageKind.OVER_LOCK_FREE_FILE_CACHE.open(storagePath);
  }

  @Test
  public void recordAreAlwaysAlignedFullyOnSinglePage() {
    final int enoughRecords = (PAGE_SIZE / RECORD_SIZE_IN_BYTES) * 16;

    for (int recordId = 0; recordId < enoughRecords; recordId++) {
      final long recordOffsetInFile = storage.recordOffsetInFileUnchecked(recordId);
      final long recordEndOffsetInFile = recordOffsetInFile + RECORD_SIZE_IN_BYTES - 1;
      assertEquals(
        "Record(#" + recordId + ", offset: " + recordOffsetInFile + ") must start and end on a same page",
        recordOffsetInFile / PAGE_SIZE,
        recordEndOffsetInFile / PAGE_SIZE
      );
    }
  }

  @Test
  public void recordOffsetCalculatedByStorageIsConsistentWithPlainCalculation() {
    final int enoughRecords = (PAGE_SIZE / RECORD_SIZE_IN_BYTES) * 16;

    long expectedRecordOffsetInFile = PersistentFSRecordsOverLockFreePagedStorage.HEADER_SIZE;
    for (int recordId = NULL_ID + 1; recordId < enoughRecords; recordId++) {

      final long recordOffsetInFile = storage.recordOffsetInFileUnchecked(recordId);
      assertEquals("recordOffset(recordId:" + recordId + ") must be " + expectedRecordOffsetInFile,
                   expectedRecordOffsetInFile,
                   recordOffsetInFile
      );

      expectedRecordOffsetInFile += RECORD_SIZE_IN_BYTES;
      if (PAGE_SIZE - (expectedRecordOffsetInFile % PAGE_SIZE) < RECORD_SIZE_IN_BYTES) {
        expectedRecordOffsetInFile = (expectedRecordOffsetInFile / PAGE_SIZE + 1) * PAGE_SIZE;
      }
    }
  }

  @Test
  public void loadRecordsCount_IsConsistentWith_recordOffsetInFile() throws IOException {
    final int enoughRecords = (PAGE_SIZE / RECORD_SIZE_IN_BYTES) * 16;

    for (int recordId = NULL_ID + 1; recordId < enoughRecords; recordId++) {
      final long recordOffsetInFile = storage.recordOffsetInFileUnchecked(recordId);
      final int calculatedRecordNo = storage.loadRecordsCount(recordOffsetInFile);
      final int expectedRecordNo = recordId - 1;
      assertEquals("storage(" + recordOffsetInFile + "b) must contain " + expectedRecordNo + " records",
                   expectedRecordNo,
                   calculatedRecordNo
      );
    }
  }

  @Test
  public void loadRecordsCount_ThrowsIOException_IfUnEvenNumberOfRecordsFound() throws IOException {
    //RC: method is very slow, so use 1 random excess for each record, instead of iterating through
    // each excess: [1..RECORD_SIZE_IN_BYTES) on each record
    final int enoughRecords = (PAGE_SIZE / RECORD_SIZE_IN_BYTES) * 16;

    for (int recordId = NULL_ID + 1; recordId < enoughRecords; recordId++) {
      final long recordOffsetInFile = storage.recordOffsetInFileUnchecked(recordId);
      final int excessBytes = ThreadLocalRandom.current().nextInt(1, RECORD_SIZE_IN_BYTES);
      final long storageSizeWithExcess = recordOffsetInFile + excessBytes;
      try {
        storage.loadRecordsCount(storageSizeWithExcess);
        fail(recordOffsetInFile + "b + " + excessBytes + "b: excess contains non-integer records " +
             "-> must throw 'corrupted' exception");
      }
      catch (IOException e) {
        final String message = e.getMessage();
        assertTrue(
          "Exception [" + message + "] must contain 'is corrupted' message",
          message.contains("is corrupted")
        );
      }
    }
  }
}