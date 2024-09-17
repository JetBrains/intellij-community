// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.blobstorage;

import com.intellij.openapi.util.IntRef;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy.WriterDecidesStrategy;
import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.intellij.util.io.blobstorage.StreamlinedBlobStorage.NULL_ID;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.*;

@RunWith(Theories.class)
public abstract class StreamlinedBlobStorageTestBase<S extends StreamlinedBlobStorage> extends BlobStorageTestBase<S> {


  protected final int pageSize;
  protected final SpaceAllocationStrategy allocationStrategy;

  @DataPoints
  public static List<SpaceAllocationStrategy> allocationStrategiesToTry() {
    return Arrays.asList(
      new WriterDecidesStrategy(StreamlinedBlobStorageHelper.MAX_CAPACITY, 1024),
      new WriterDecidesStrategy(StreamlinedBlobStorageHelper.MAX_CAPACITY, 256),
      new DataLengthPlusFixedPercentStrategy(256, 1024, StreamlinedBlobStorageHelper.MAX_CAPACITY, 30),
      new DataLengthPlusFixedPercentStrategy(64, 256, StreamlinedBlobStorageHelper.MAX_CAPACITY, 30),

      //put stress on allocation/reallocation code paths
      new DataLengthPlusFixedPercentStrategy(64, 128, StreamlinedBlobStorageHelper.MAX_CAPACITY, 0),
      new DataLengthPlusFixedPercentStrategy(2, 2, StreamlinedBlobStorageHelper.MAX_CAPACITY, 0)
    );
  }

  @DataPoints
  public static Integer[] pageSizesToTry() {
    return new Integer[]{
      //Try quite small pages, so issues on the page borders have a chance to manifest themselves
      1 << 14,
      1 << 18,
      1 << 22
    };
  }


  protected StreamlinedBlobStorageTestBase(final @NotNull Integer pageSize,
                                           final @NotNull SpaceAllocationStrategy strategy) {
    this.pageSize = pageSize;
    this.allocationStrategy = strategy;
  }

  protected StorageRecord[] randomRecordsToStartWith;

  @Before
  public void generateRandomRecords() throws Exception {
    randomRecordsToStartWith = generateRecords(ENOUGH_RECORDS, storage.maxPayloadSupported());
  }

  @Test
  public void emptyStorageHasNoRecords() throws Exception {
    final IntArrayList nonExistentIds = new IntArrayList();
    nonExistentIds.add(NULL_ID);
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
  public void openFileWithIncorrectMagicWordFails() throws Exception {
    storage.close();
    Files.write(storagePath, new byte[]{1, 2, 3, 4}, StandardOpenOption.WRITE);

    try {
      openStorage(storagePath);
    }
    catch (IOException e) {
      assertTrue(
        "Expect error message about 'magicWord mismatch'",
        e.getMessage().contains("magicWord")
      );
    }
  }


  @Test
  public void dataFormatVersion_CouldBeWritten_AndReadBackAsIs_AfterStorageReopened() throws Exception {
    final int dataFormatVersion = 42;

    storage.setDataFormatVersion(dataFormatVersion);
    assertEquals(
      "Data format version must be same as was just written",
      dataFormatVersion,
      storage.getDataFormatVersion()
    );

    closeStorage(storage);
    storage = openStorage(storagePath);

    assertEquals(
      "Data format version must be same as was written before close/reopen",
      dataFormatVersion,
      storage.getDataFormatVersion()
    );
  }

  @Test
  public void wasClosedProperly_isTrueForNewStorage() throws IOException {
    assertTrue(
      "New empty storage is always 'closed properly'",
      storage.wasClosedProperly()
    );
  }

  @Test
  public void wasClosedProperly_isTrueForClosedAndReopenedStorage() throws IOException {
    storage.close();
    storage = openStorage(storagePath);
    assertTrue(
      "Storage is 'closed properly' if it was closed and opened again",
      storage.wasClosedProperly()
    );
  }

  //RC: there is no easy way to check .wasClosedProperly() is false after not-closing
  //    because not-closing is hard to do without JVM kill -- most our storages prevent
  //    reopen already opened file without closing it first.

  @Test
  public void manyRecordsWritten_CouldAllBeReadBackUnchanged_ById() throws Exception {
    final StorageRecord[] recordsToWrite = randomRecordsToStartWith.clone();
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(this, storage);
    }

    for (int i = 0; i < recordsToWrite.length; i++) {
      final StorageRecord recordWritten = recordsToWrite[i];
      final StorageRecord recordReadBack = StorageRecord.readFromStorage(this, storage, recordWritten.recordId);
      assertEquals(
        "Record[" + i + "][#" + recordWritten.recordId + "] must be read back as-is: " +
        "written [" + recordWritten.payload + "], read back[" + recordReadBack.payload + "]",
        recordWritten.payload,
        recordReadBack.payload
      );
    }
  }

  @Test
  public void manyRecordsWritten_CouldAllBeReadBackUnchanged_ViaForEach() throws Exception {
    final StorageRecord[] recordsToWrite = randomRecordsToStartWith.clone();
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(this, storage);
    }

    final Int2ObjectMap<StorageRecord> recordsById = new Int2ObjectOpenHashMap<>();
    storage.forEach((recordId, recordCapacity, recordLength, payload) -> {
      if (storage.isRecordActual(recordLength)) {
        final StorageRecord record = new StorageRecord(recordId, StreamlinedBlobStorageTestBase.stringFromBuffer(payload));
        recordsById.put(recordId, record);
      }
      return true;
    });

    for (int i = 0; i < recordsToWrite.length; i++) {
      final StorageRecord recordWritten = recordsToWrite[i];
      final StorageRecord recordReadBack = recordsById.get(recordWritten.recordId);
      assertNotNull(
        "Record[" + i + "][#" + recordWritten.recordId + "]: must be read back",
        recordReadBack
      );
      assertEquals(
        "Record[" + i + "][#" + recordWritten.recordId + "]: " +
        "written [" + recordWritten.payload + "], read back[" + recordReadBack.payload + "]",
        recordWritten.payload,
        recordReadBack.payload
      );
    }
  }


  @Test
  public void singleRecordWithMaxSupportedPayload_CouldBeWritten_AndReadBackById() throws Exception {
    //Specifically, check payloads = maxPayloadSupported

    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    StorageRecord maxSupportedRecord = new StorageRecord(StorageTestingUtils.randomString(rnd, storage.maxPayloadSupported()));
    StorageRecord writtenRecord = maxSupportedRecord.writeIntoStorage(this, storage);

    StorageRecord recordReadBack = StorageRecord.readFromStorage(this, storage, writtenRecord.recordId);
    assertEquals(
      "Huge record[#" + writtenRecord.recordId + "] must be read back as-is: " +
      "written [" + writtenRecord.payload + "], read back[" + recordReadBack.payload + "]",
      writtenRecord.payload,
      recordReadBack.payload
    );
  }

  @Test
  public void manyRecordsWritten_WithBigPayload_CouldAllBeReadBackUnchanged_ById() throws Exception {
    //Specifically check payloads close to maxPayloadSize (-some margin for record header)

    int enoughRecordsButNotTooManyToNotTriggerOoM = 100;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final StorageRecord[] recordsToWrite = generateRecords(enoughRecordsButNotTooManyToNotTriggerOoM, () -> {
      return rnd.nextInt(storage.maxPayloadSupported() / 2, storage.maxPayloadSupported());
    });
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(this, storage);
    }

    for (int i = 0; i < recordsToWrite.length; i++) {
      StorageRecord recordWritten = recordsToWrite[i];
      StorageRecord recordReadBack = StorageRecord.readFromStorage(this, storage, recordWritten.recordId);
      assertEquals(
        "Record[" + i + "][#" + recordWritten.recordId + "] must be read back as-is: " +
        "written [" + recordWritten.payload + "], read back[" + recordReadBack.payload + "]",
        recordWritten.payload,
        recordReadBack.payload
      );
    }
  }

  @Test
  public void recordsLargerThanMaxPayloadSize_AreRejected_ButDoNotBreakStorage() throws Exception {
    //maxPayloadSupported is generally about records being on a single page.
    // But don't want to expose recordSize/payloadSize internal relationship, neither recordHeader size, hence
    // I just vary payload size _around_ storage.maxPayloadSupported() to check that records are either stored
    // OK, or throw IAE without breaking the storage
    final int margin = 16;
    final int maxPayloadSupported = storage.maxPayloadSupported();
    final List<StorageRecord> recordsActuallyWritten = new ArrayList<>();
    try {
      for (int payloadSize = maxPayloadSupported - margin; payloadSize < maxPayloadSupported + margin; payloadSize++) {
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
    catch (IllegalArgumentException | IllegalStateException e) {
      //this is expectable
    }

    //now check storage wasn't broken: add one record on the top of it
    final StorageRecord recordWrittenOnTop = StorageRecord.recordWithRandomPayload(maxPayloadSupported / 2)
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
  public void reallocatedRecord_CouldStillBeRead_ByOriginalId() throws Exception {
    final StorageRecord[] recordsToWrite = randomRecordsToStartWith.clone();
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
      fail("Read of deleted record should throw IOException, but: " + recordRead);
    }
    catch (IOException e) {
      //ok
    }
  }

  @Test
  public void closeCouldBeCalledTwice() throws IOException {
    storage.close();
    storage.close();
  }

  @Test
  public void afterClose_storageAccessorsMustThrowException() throws IOException {
    storage.close();
    assertThrows("Storage must throw exception after close()",
                 ClosedStorageException.class,
                 () -> storage.sizeInBytes()
    );
    assertThrows("Storage must throw exception after close()",
                 ClosedStorageException.class,
                 () -> storage.getStorageVersion()
    );
    assertThrows("Storage must throw exception after close()",
                 ClosedStorageException.class,
                 () -> storage.getDataFormatVersion()
    );
    assertThrows("Storage must throw exception after close()",
                 ClosedStorageException.class,
                 () -> storage.liveRecordsCount()
    );
  }

  @Test
  public void afterClose_toStringIsStillSafeToCall() throws IOException {
    storage.close();
    assertNotNull("Ensure .toString() could be called after .close()",
                  storage.toString());
  }

  @Test
  public void afterCloseAndClean_noFilesRemain() throws IOException {
    storage.closeAndClean();
    assertFalse(
      "No [" + storagePath + "] must remain after .closeAndClean()",
      Files.exists(storagePath)
    );
  }

  //TODO test write/read records of size=0

  //TODO test delete records


  /* ========================= adapter implementation: ========================================= */


  @Override
  protected abstract S openStorage(final Path pathToStorage) throws IOException;

  @Override
  protected void closeStorage(final S storage) throws Exception {
    storage.close();
  }

  @Override
  protected boolean hasRecord(final S storage,
                              final int recordId) throws Exception {
    return storage.hasRecord(recordId);
  }

  @Override
  protected StorageRecord readRecord(final S storage,
                                     final int recordId) throws Exception {
    final IntRef redirectedIdRef = new IntRef();
    final String newPayload = storage.readRecord(
      recordId,
      buffer -> {
        return StreamlinedBlobStorageTestBase.stringFromBuffer(buffer);
      },
      redirectedIdRef
    );
    final int newRecordId = redirectedIdRef.get();
    return new StorageRecord(newRecordId, newPayload);
  }

  @Override
  protected StorageRecord writeRecord(final StorageRecord record,
                                      final S storage) throws Exception {
    final byte[] payloadBytes = record.payload.getBytes(US_ASCII);
    final int recordId = record.recordId;
    final int newRecordId = storage.writeToRecord(
      recordId,
      buffer -> {
        final ByteBuffer buff = (buffer.capacity() >= payloadBytes.length /*&& recordId != NULL_ID*/) ?
                                buffer : ByteBuffer.allocate(payloadBytes.length);
        return buff
          .clear()
          .put(payloadBytes);
      },
      payloadBytes.length / 2,
      true
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
                              final S storage) throws Exception {
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
