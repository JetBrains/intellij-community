// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;


import com.intellij.util.io.PageCacheUtils;
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
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
  private PagedFileStorageWithRWLockedPageContent pagedStorage;



  public PersistentFSRecordsStorageOverLockFreePagedStorageTest(final UpdateAPIMethod updateMethodToTest) { super(MAX_RECORDS_TO_INSERT, updateMethodToTest); }

  private StorageLockContext storageContext;

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
    final int pageSize;
    final boolean nativeBytesOrder;
    try (var file = PersistentFSRecordsStorageFactory.openRMappedFile(storagePath, RECORD_SIZE_IN_BYTES)) {
      storageContext = file.getStorageLockContext();
      pageSize = file.getPagedFileStorage().getPageSize();
      nativeBytesOrder = file.isNativeBytesOrder();
    }
    pagedStorage = new PagedFileStorageWithRWLockedPageContent(
      storagePath,
      storageContext,
      pageSize,
      nativeBytesOrder,
      PageContentLockingStrategy.LOCK_PER_PAGE
    );
    return new PersistentFSRecordsOverLockFreePagedStorage(pagedStorage);
  }

  @Test
  public void recordAreAlwaysAlignedFullyOnSinglePage() {
    final int pageSize = pagedStorage.getPageSize();
    final int enoughRecords = (pageSize / RECORD_SIZE_IN_BYTES) * 16;

    for (int recordId = 0; recordId < enoughRecords; recordId++) {
      final long recordOffsetInFile = storage.recordOffsetInFileUnchecked(recordId);
      final long recordEndOffsetInFile = recordOffsetInFile + RECORD_SIZE_IN_BYTES - 1;
      assertEquals(
        "Record(#" + recordId + ", offset: " + recordOffsetInFile + ") must start and end on a same page",
        recordOffsetInFile / pageSize,
        recordEndOffsetInFile / pageSize
      );
    }
  }

  @Test
  public void recordOffsetCalculatedByStorageIsConsistentWithPlainCalculation() {
    final int pageSize = pagedStorage.getPageSize();
    final int enoughRecords = (pageSize / RECORD_SIZE_IN_BYTES) * 16;

    long expectedRecordOffsetInFile = PersistentFSRecordsOverLockFreePagedStorage.HEADER_SIZE;
    for (int recordId = NULL_ID + 1; recordId < enoughRecords; recordId++) {

      final long recordOffsetInFile = storage.recordOffsetInFileUnchecked(recordId);
      assertEquals("recordOffset(recordId:" + recordId + ") must be " + expectedRecordOffsetInFile,
                   expectedRecordOffsetInFile,
                   recordOffsetInFile
      );

      expectedRecordOffsetInFile += RECORD_SIZE_IN_BYTES;
      if (pageSize - (expectedRecordOffsetInFile % pageSize) < RECORD_SIZE_IN_BYTES) {
        expectedRecordOffsetInFile = (expectedRecordOffsetInFile / pageSize + 1) * pageSize;
      }
    }
  }

  @Test
  public void loadRecordsCount_IsConsistentWith_recordOffsetInFile() throws IOException {
    final int pageSize = pagedStorage.getPageSize();
    final int enoughRecords = (pageSize / RECORD_SIZE_IN_BYTES) * 16;

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
    final int pageSize = pagedStorage.getPageSize();
    final int enoughRecords = (pageSize / RECORD_SIZE_IN_BYTES) * 16;

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