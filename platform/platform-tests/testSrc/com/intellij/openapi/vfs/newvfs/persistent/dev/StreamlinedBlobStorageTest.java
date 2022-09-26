// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedBlobStorage.SpaceAllocationStrategy;
import com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedBlobStorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedBlobStorage.SpaceAllocationStrategy.WriterDecidesStrategy;
import com.intellij.util.io.PagedFileStorage;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.intellij.openapi.vfs.newvfs.persistent.AbstractAttributesStorage.NON_EXISTENT_ATTR_RECORD_ID;
import static com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedBlobStorage.NULL_ID;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 *
 */
@RunWith(Theories.class)
public class StreamlinedBlobStorageTest extends StorageTestBase<StreamlinedBlobStorage> {

  @DataPoints
  public static List<SpaceAllocationStrategy> allocationStrategiesToTry() {
    return Arrays.asList(
      new WriterDecidesStrategy(1024),
      new WriterDecidesStrategy(512),
      new WriterDecidesStrategy(256),
      new DataLengthPlusFixedPercentStrategy(1024, 256, 30),
      new DataLengthPlusFixedPercentStrategy(512, 128, 30),
      new DataLengthPlusFixedPercentStrategy(256, 64, 30),

      //put stress on allocation/reallocation code paths
      new DataLengthPlusFixedPercentStrategy(128, 64, 0),
      new DataLengthPlusFixedPercentStrategy(2, 2, 0)
    );
  }

  @DataPoints
  public static Integer[] pageSizesToTry() {
    return new Integer[]{
      //Try quite small pages, so issues on the page borders have chance to manifest themselves
      1 << 14,
      1 << 18,
      1 << 22
    };
  }


  private final int pageSize;
  private final SpaceAllocationStrategy allocationStrategy;

  public StreamlinedBlobStorageTest(final @NotNull Integer pageSize,
                                    final @NotNull SpaceAllocationStrategy strategy) {
    this.pageSize = pageSize;
    this.allocationStrategy = strategy;
  }

  /* ======================== TESTS (specific for this impl) ===================================== */

  @Test
  public void emptyStorageHasNoRecords() throws Exception {
    final IntArrayList nonExistentIds = new IntArrayList();
    nonExistentIds.add(NON_EXISTENT_ATTR_RECORD_ID);
    nonExistentIds.add(Integer.MAX_VALUE);
    ThreadLocalRandom.current().ints()
      .filter(i -> i > 0)
      .limit(1 << 14)
      .forEach(nonExistentIds::add);

    for (int i = 0; i < nonExistentIds.size(); i++) {
      final int nonExistentId = nonExistentIds.getInt(i);
      assertFalse(
        "Not inserted record is not exists",
        hasRecord(storage, nonExistentId)
      );
    }
  }

  @Test
  public void newStorageHasVersionOfCurrentStorageFormat() throws Exception {
    assertEquals(
      "New storage version == STORAGE_VERSION_CURRENT",
      storage.getStorageVersion(),
      StreamlinedBlobStorage.STORAGE_VERSION_CURRENT
    );
  }

  @Test
  public void dataFormatVersionCouldBeWrittenAndReadBackAsIs_AfterStorageReopened() throws Exception {
    final int dataFormatVersion = 42;

    storage.setDataFormatVersion(dataFormatVersion);
    assertEquals(
      "Data format version must be same as was just written",
      storage.getDataFormatVersion(),
      dataFormatVersion
    );

    closeStorage(storage);
    storage = openStorage(storagePath);

    assertEquals(
      "Data format version must be same as was written before close/reopen",
      storage.getDataFormatVersion(),
      dataFormatVersion
    );
  }

  @Test
  public void manyRecordsWritten_CouldAllBeReadBackUnchanged_ViaForEach() throws Exception {
    final StorageRecord[] recordsToWrite = StorageTestBase.generateRecords(ENOUGH_RECORDS);
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(this, storage);
    }

    final Int2ObjectMap<StorageRecord> recordsById = new Int2ObjectOpenHashMap<>();
    storage.forEach((recordId, recordCapacity, recordLength, payload) -> {
      if (storage.isRecordActual(recordLength)) {
        final StorageRecord record = new StorageRecord(recordId, stringFromBuffer(payload));
        recordsById.put(recordId, record);
      }
      return true;
    });

    for (int i = 0; i < recordsToWrite.length; i++) {
      final StorageRecord recordWritten = recordsToWrite[i];
      final StorageRecord recordReadBack = recordsById.get(recordWritten.recordId);
      assertEquals(
        "Record[" + i + "][#" + recordWritten.recordId + "]: " +
        "written [" + recordWritten.payload + "], read back[" + recordReadBack.payload + "]",
        recordWritten.payload,
        recordReadBack.payload
      );
    }
  }

  @Test
  public void recordsLargerThanPageSizeRejectedByStorage_ButDoNotBreakStorage() throws Exception {
    //records must be on a single page, hence total size(record) <= pageSize
    // I don't want to expose recordSize/payloadSize internal relationship, hence
    // I just vary payload size _around_ PAGE_SIZE to check that they are either stored OK,
    // or throw IAE without breaking the storage
    final int margin = 16;
    assumeTrue(
      "capacity/length are 2 bytes, hence this test is not applicable for PAGE_SIZE > " + storage.maxPayloadSupported(),
      pageSize + margin + 1 < storage.maxPayloadSupported()
    );
    final List<StorageRecord> recordsActuallyWritten = new ArrayList<>();
    try {
      for (int payloadSize = pageSize - margin; payloadSize < pageSize + margin; payloadSize++) {
        final StorageRecord recordWritten = StorageRecord.recordWithRandomPayload(payloadSize)
          .writeIntoStorage(this, storage);
        recordsActuallyWritten.add(recordWritten);

        final StorageRecord recordReadBack = StorageRecord.readFromStorage(this, storage, recordWritten.recordId);
        assertEquals(
          "If record was written, it should be possible to read it back unchanged",
          recordWritten.payload,
          recordReadBack.payload
        );
      }
      fail("_Some_ payload size around PAGE_SIZE must be rejected by storage");
    }
    catch (IllegalArgumentException e) {
      //this is expectable
    }

    //now check storage wasn't broken: add one record on the top of it
    final StorageRecord recordWrittenOnTop = StorageRecord.recordWithRandomPayload(pageSize / 2)
      .writeIntoStorage(this, storage);
    recordsActuallyWritten.add(recordWrittenOnTop);

    //and check everything that _was_ written -- could be read back:
    final List<StorageRecord> recordsReadBack = new ArrayList<>();
    storage.forEach((recordId, recordCapacity, recordLength, payload) -> {
      if (storage.isRecordActual(recordLength)) {
        final StorageRecord recordRead = new StorageRecord(recordId, stringFromBuffer(payload));
        recordsReadBack.add(recordRead);
      }
      return true;
    });

    assertEquals(
      "Every record that was written successfully -- must be read back",
      recordsActuallyWritten,
      recordsReadBack
    );
  }

  @Test
  public void reallocatedRecordCouldStillBeReadByOriginalId() throws Exception {
    final StorageRecord[] recordsToWrite = StorageTestBase.generateRecords(ENOUGH_RECORDS);
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(this, storage);
    }

    //choose record in the middle, so it will re-allocated quite far away from its origin:
    StorageRecord recordToGoNasty = recordsToWrite[recordsToWrite.length / 2];
    final int maxAttempts = 6;
    final IntSet subsequentIds = new IntArraySet();
    subsequentIds.add(recordToGoNasty.recordId);
    for (int attemptNo = 0; subsequentIds.size() < 2 && attemptNo < maxAttempts; attemptNo++) {
      //assume 4^6 is enough to beat capacity reserve:
      final int newPayloadSize = (recordToGoNasty.payload.length() + 1) * 4;
      recordToGoNasty = recordToGoNasty
        .withRandomPayloadOfSize(newPayloadSize)
        .writeIntoStorage(this, storage);

      subsequentIds.add(recordToGoNasty.recordId);
    }
    assertTrue(
      "Record with 4^6=2048x payload increase _must_ be re-allocated at least once, i.e. it's id must change -> something wrong is happening, if it is not",
      subsequentIds.size() > 1
    );

    //Check: all the ids are lead to the same record (to the final version of recordToGoNasty):
    for (int recordId : subsequentIds) {
      final StorageRecord recordFetchedById = StorageRecord.readFromStorage(this, storage, recordId);
      assertEquals(
        "recordId[" + recordId + "] should be just an alias for " + recordToGoNasty.recordId,
        recordToGoNasty.payload,
        recordFetchedById.payload
      );
    }
  }

  @Test
  public void singleWrittenRecord_AndDeleted_NotExistsInStorage_AndThrowExceptionIfQueried() throws Exception {
    final StorageRecord recordWritten = new StorageRecord("ABC")
      .writeIntoStorage(this, storage);

    assertNotEquals(
      "Inserted record must be assigned valid ID",
      NULL_ID,
      recordWritten.recordId
    );

    deleteRecord(recordWritten.recordId, storage);

    assertFalse(
      "Deleted record must not exist anymore",
      hasRecord(storage, recordWritten.recordId)
    );

    try {
      final StorageRecord recordRead = StorageRecord.readFromStorage(this, storage, recordWritten.recordId);
      fail("Read of deleted record should throw IOException");
    }
    catch (IOException e) {
      //ok
    }
  }


  //TODO write/read records of size=0
  //TODO delete records

  /* ========================= adapter implementation ========================================= */
  @Override
  protected StreamlinedBlobStorage openStorage(final Path pathToStorage) throws IOException {
    final PagedFileStorage pagedStorage = new PagedFileStorage(
      pathToStorage,
      LOCK_CONTEXT,
      pageSize,
      true,
      true
    );
    return new StreamlinedBlobStorage(
      pagedStorage,
      allocationStrategy
    );
  }

  @Override
  protected void closeStorage(final StreamlinedBlobStorage storage) throws Exception {
    storage.close();
  }

  @Override
  public void tearDown() throws Exception {
    if (storage != null) {
      System.out.printf("Storage after test: %d records allocated, %d deleted, %d relocated, live records %.1f%% of total \n",
                        storage.recordsAllocated(),
                        storage.recordsDeleted(),
                        storage.recordsRelocated(),
                        storage.liveRecordsCount() * 100.0 / storage.recordsAllocated()
      );
      System.out.printf("                    %d bytes live payload, %d live capacity, live payload %.1f%% of total storage size \n",
                        storage.totalLiveRecordsPayloadBytes(),
                        storage.totalLiveRecordsCapacityBytes(),
                        storage.totalLiveRecordsPayloadBytes() * 100.0 / storage.sizeInBytes() //including _storage_ header
      );
    }
    super.tearDown();
  }

  @Override
  protected boolean hasRecord(final StreamlinedBlobStorage storage,
                              final int recordId) throws Exception {
    return storage.hasRecord(recordId);
  }

  @Override
  protected StorageRecord readRecord(final StreamlinedBlobStorage storage,
                                     final int recordId) throws Exception {
    final IntRef redirectedIdRef = new IntRef();
    final String newPayload = storage.readRecord(
      recordId,
      buffer -> {
        return stringFromBuffer(buffer);
      },
      redirectedIdRef
    );
    final int newRecordId = redirectedIdRef.get();
    return new StorageRecord(newRecordId, newPayload);
  }

  @Override
  protected StorageRecord writeRecord(final StorageRecord record,
                                      final StreamlinedBlobStorage storage) throws Exception {
    final byte[] payloadBytes = record.payload.getBytes(US_ASCII);
    final int recordId = record.recordId;
    final int newRecordId = storage.writeToRecord(
      recordId,
      buffer -> {
        final ByteBuffer buff = (buffer.capacity() >= payloadBytes.length && recordId != NULL_ID) ?
                                buffer : ByteBuffer.allocate(payloadBytes.length);
        return buff
          .clear()
          .put(payloadBytes);
      }
    );
    if (recordId == newRecordId) {
      return record;
    }
    else {
      return new StorageRecord(newRecordId, record.payload);
    }
  }

  @Override
  protected void deleteRecord(final int recordId,
                              final StreamlinedBlobStorage storage) throws Exception {
    storage.deleteRecord(recordId);
  }

  @NotNull
  public static String stringFromBuffer(final ByteBuffer buffer) {
    final int length = buffer.remaining();
    final byte[] bytes = new byte[length];
    buffer.get(bytes);
    return new String(bytes, US_ASCII);
  }
}