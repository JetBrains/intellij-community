// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.util.io.PagedFileStorage;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.junit.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedStorage.NULL_ID;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.*;

/**
 *
 */
public class StreamlinedStorageTest extends StorageTestBase<StreamlinedStorage> {

  /**
   * Use quite small pages, so issues on the page borders have chance to manifest themselves
   */
  protected static final int PAGE_SIZE = 1 << 13;


  @Override
  protected StreamlinedStorage openStorage(final Path pathToStorage) throws IOException {
    final PagedFileStorage pagedStorage = new PagedFileStorage(
      pathToStorage,
      LOCK_CONTEXT,
      PAGE_SIZE,
      true,
      true
    );
    return new StreamlinedStorage(pagedStorage);
  }

  @Override
  protected void closeStorage(final StreamlinedStorage storage) throws Exception {
    storage.close();
  }

  @Override
  protected StorageRecord readRecord(final StreamlinedStorage storage,
                                     final int recordId) throws Exception {
    final int[] redirectedIdRef = new int[1];
    final String newPayload = storage.readRecord(
      recordId,
      buffer -> {
        return stringFromBuffer(buffer);
      },
      redirectedIdRef
    );
    final int newRecordId = redirectedIdRef[0];
    return new StorageRecord(newRecordId, newPayload);
  }

  @Override
  protected StorageRecord writeRecord(final StorageRecord record,
                                      final StreamlinedStorage storage) throws Exception {
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
                              final StreamlinedStorage storage) throws Exception {
    storage.deleteRecord(recordId);
  }


  @Test
  public void newStorageHasVersionOfCurrentPersistentFormat() throws Exception {
    assertEquals(
      storage.getVersion(),
      StreamlinedStorage.VERSION_CURRENT
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
    final List<StorageRecord> recordsActuallyWritten = new ArrayList<>();
    try {
      for (int payloadSize = PAGE_SIZE - margin; payloadSize < PAGE_SIZE + margin; payloadSize++) {
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
    final StorageRecord recordWrittenOnTop = StorageRecord.recordWithRandomPayload(PAGE_SIZE / 2)
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
    final int maxAttempts = 5;
    final IntSet subsequentIds = new IntArraySet();
    subsequentIds.add(recordToGoNasty.recordId);
    for (int attemptNo = 0; subsequentIds.size() < 2 && attemptNo < maxAttempts; attemptNo++) {
      //assume 2^3 is enough to beat capacity reserve:
      final int newPayloadSize = recordToGoNasty.payload.length() * 4;
      recordToGoNasty = recordToGoNasty
        .withRandomPayloadOfSize(newPayloadSize)
        .writeIntoStorage(this, storage);

      subsequentIds.add(recordToGoNasty.recordId);
    }
    assertTrue(
      "Record with 4^5=1024x payload increase _must_ be re-allocated at least once, i.e. it's id must change -> something wrong is happening, if it is not",
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
  public void singleWrittenRecord_AndDeleted_ThrowExceptionIfQueried() throws Exception {
    final StorageRecord recordToWrite = new StorageRecord("ABC")
      .writeIntoStorage(this, storage);
    assertNotEquals(
      NULL_ID,
      recordToWrite.recordId
    );

    deleteRecord(recordToWrite.recordId, storage);

    try {
      final StorageRecord recordRead = StorageRecord.readFromStorage(this, storage, recordToWrite.recordId);
      fail("Read of deleted record should throw IOException");
    }
    catch (IOException e) {
      //ok
    }
  }


  //TODO write/read records of size=0
  //TODO delete records

  @NotNull
  public static String stringFromBuffer(final ByteBuffer buffer) {
    final int length = buffer.remaining();
    final byte[] bytes = new byte[length];
    buffer.get(bytes);
    return new String(bytes, US_ASCII);
  }
}