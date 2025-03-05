// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.OwnershipInfo;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class PersistentFSRecordsStorageLockFreeOverMMappedFileTest
  extends PersistentFSRecordsStorageTestBase<PersistentFSRecordsLockFreeOverMMappedFile> {

  private static final int MAX_RECORDS_TO_INSERT = 1 << 22;

  private static final int PAGE_SIZE = PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE;

  @Parameterized.Parameters(name = "{index}: {0}")
  public static UpdateAPIMethod[] METHODS_TO_TEST() {
    return new UpdateAPIMethod[]{
      DEFAULT_API_UPDATE_METHOD,
      MODERN_API_UPDATE_METHOD
    };
  }



  public PersistentFSRecordsStorageLockFreeOverMMappedFileTest(UpdateAPIMethod updateMethodToTest) {
    super(MAX_RECORDS_TO_INSERT, updateMethodToTest);
  }


  @NotNull
  @Override
  protected PersistentFSRecordsLockFreeOverMMappedFile openStorage(@NotNull Path storagePath) throws IOException {
    return MMappedFileStorageFactory.withDefaults()
      .pageSize(PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE)
      .wrapStorageSafely(storagePath, PersistentFSRecordsLockFreeOverMMappedFile::new);
  }

  @Test
  public void recordAreAlwaysAlignedFullyOnSinglePage() {
    int enoughRecords = (PAGE_SIZE / PersistentFSRecordsLockFreeOverMMappedFile.RecordLayout.RECORD_SIZE_IN_BYTES) * 16;

    for (int recordId = 0; recordId < enoughRecords; recordId++) {
      long recordOffsetInFile = storage.recordOffsetInFileUnchecked(recordId);
      long recordEndOffsetInFile = recordOffsetInFile + PersistentFSRecordsLockFreeOverMMappedFile.RecordLayout.RECORD_SIZE_IN_BYTES - 1;
      assertEquals(
        "Record(#" + recordId + ", offset: " + recordOffsetInFile + ") must start and end on a same page",
        recordOffsetInFile / PAGE_SIZE,
        recordEndOffsetInFile / PAGE_SIZE
      );
    }
  }

  @Test
  public void recordOffsetCalculatedByStorageIsConsistentWithPlainCalculation() {
    int enoughRecords = (PAGE_SIZE / PersistentFSRecordsLockFreeOverMMappedFile.RecordLayout.RECORD_SIZE_IN_BYTES) * 16;

    long expectedRecordOffsetInFile = PersistentFSRecordsLockFreeOverMMappedFile.FileHeader.HEADER_SIZE;
    for (int recordId = PersistentFSRecordsStorage.NULL_ID + 1; recordId < enoughRecords; recordId++) {

      long recordOffsetInFile = storage.recordOffsetInFileUnchecked(recordId);
      assertEquals("recordOffset(recordId:" + recordId + ") must be " + expectedRecordOffsetInFile,
                   expectedRecordOffsetInFile,
                   recordOffsetInFile
      );

      expectedRecordOffsetInFile += PersistentFSRecordsLockFreeOverMMappedFile.RecordLayout.RECORD_SIZE_IN_BYTES;
      if (PAGE_SIZE - (expectedRecordOffsetInFile % PAGE_SIZE) < PersistentFSRecordsLockFreeOverMMappedFile.RecordLayout.RECORD_SIZE_IN_BYTES) {
        expectedRecordOffsetInFile = (expectedRecordOffsetInFile / PAGE_SIZE + 1) * PAGE_SIZE;
      }
    }
  }

  @Test
  public void tryAcquireExclusiveAccess_alwaysSucceedWithoutConcurrentRequests() throws IOException {
    int currentPid = 123;
    long acquiringTimestamp = 42;
    OwnershipInfo owner = storage.tryAcquireExclusiveAccess(currentPid, acquiringTimestamp, /*force: */ false);
    assertEquals(
      "Acquire must be successful since there no other owners",
      currentPid,
      owner.ownerProcessPid
    );
    assertEquals(
      "Acquire must be successful since there no other owners",
      acquiringTimestamp,
      owner.ownershipAcquiredAtMs
    );
  }

  @Test
  public void ifStorageIsAcquired_acquireWithDifferentPid_MustFailToChangeOwner() throws IOException {
    int currentPid = 123;
    long acquiringTimestamp = 42;

    int competingPid = 124;
    long competingTimestamp = 42;

    OwnershipInfo owner1 = storage.tryAcquireExclusiveAccess(currentPid, acquiringTimestamp, /*force: */ false);
    OwnershipInfo owner2 = storage.tryAcquireExclusiveAccess(competingPid, competingTimestamp, /*force: */ false);
    assertEquals(
      "First acquire ownership must succeed since no previous owner",
      currentPid,
      owner1.ownerProcessPid
    );
    assertEquals(
      "If storage is already acquired, 2nd acquire must fail, and owner must not change",
      currentPid,
      owner2.ownerProcessPid
    );
  }


  @Test
  public void processAlreadyAcquiredStorage_alwaysSucceedInAcquiringAgain() throws IOException {
    int currentPid = 123;
    long acquiringTimestamp1 = 42;
    long acquiringTimestamp2 = 43;
    storage.tryAcquireExclusiveAccess(currentPid, acquiringTimestamp1, false);
    OwnershipInfo owner = storage.tryAcquireExclusiveAccess(currentPid, acquiringTimestamp2, false);
    assertEquals(
      "Same process could always acquire storage again (i.e. acquire is idempotent)",
      currentPid,
      owner.ownerProcessPid
    );
    assertEquals(
      "Ownership acquisition timestamp must NOT change (i.e. acquire is idempotent)",
      acquiringTimestamp1,
      owner.ownershipAcquiredAtMs
    );
  }

  @Test
  public void reopenedStorageHasNoOwner_henceCouldBeAcquired_ByAnyProcess() throws IOException {
    int firstOwnerPid = 123;
    storage.tryAcquireExclusiveAccess(firstOwnerPid, 42L, false);

    storage.close();
    storage = openStorage(storagePath);

    int secondOwnerPid = 125;
    OwnershipInfo owner = storage.tryAcquireExclusiveAccess(secondOwnerPid, 43L, false);
    assertEquals(
      ".close() clears the owner, so any process could acquire the reopened storage",
      secondOwnerPid,
      owner.ownerProcessPid
    );
  }

}