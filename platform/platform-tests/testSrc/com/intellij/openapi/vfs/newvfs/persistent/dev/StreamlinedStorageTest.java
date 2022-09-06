// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.StorageLockContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedStorage.NULL_ID;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.*;

/**
 *
 */
public class StreamlinedStorageTest {

  private final static StorageLockContext LOCK_CONTEXT = new StorageLockContext(true);

  private static final int ENOUGH_RECORDS = 7_000_000;
  /**
   * Use quite small pages, so issues on the page borders have chance to manifest themself
   */
  private static final int PAGE_SIZE = 1 << 14;

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  protected Path storagePath;

  protected StreamlinedStorage storage;


  @Before
  public void setUp() throws IOException {
    storagePath = temporaryFolder.newFile().toPath();
    storage = openStorage(storagePath);
  }

  private StreamlinedStorage openStorage(final Path pathToStorage) throws IOException {
    final PagedFileStorage pagedStorage = new PagedFileStorage(
      pathToStorage,
      LOCK_CONTEXT,
      PAGE_SIZE,
      true,
      true
    );
    return new StreamlinedStorage(pagedStorage);
  }

  @After
  public void tearDown() throws Exception {
    if (storage != null) {
      storage.close();
    }
  }

  @Test
  public void newStorageHasVersionOfCurrentPersistentFormat() throws IOException {
    assertEquals(
      storage.getVersion(),
      StreamlinedStorage.VERSION_CURRENT
    );
  }

  @Test
  public void singleWrittenRecord_CouldBeReadBackUnchanged() throws IOException {
    final Record recordToWrite = new Record("ABC")
      .writeIntoStorage(storage);
    assertNotEquals(
      NULL_ID,
      recordToWrite.recordId
    );

    final Record recordRead = Record.readFromStorage(storage, recordToWrite.recordId);
    assertEquals(
      recordToWrite.payload,
      recordRead.payload
    );
  }

  @Test
  public void singleWrittenRecord_ReWrittenWithSmallerSize_CouldBeReadBackUnchanged() throws IOException {
    final Record recordWritten = new Record("1234567890")
      .writeIntoStorage(storage);

    final Record recordOverWritten = recordWritten
      .withPayload("09876")
      .writeIntoStorage(storage);

    final Record recordRead = Record.readFromStorage(storage, recordWritten.recordId);
    assertEquals(
      recordOverWritten.payload,
      recordRead.payload
    );
  }

  @Test
  public void singleWrittenRecord_AndDeleted_ThrowExceptionIfQueried() throws IOException {
    final Record recordToWrite = new Record("ABC")
      .writeIntoStorage(storage);
    assertNotEquals(
      NULL_ID,
      recordToWrite.recordId
    );

    storage.deleteRecord(recordToWrite.recordId);

    try {
      final Record recordRead = Record.readFromStorage(storage, recordToWrite.recordId);
      fail("Read of deleted record should throw IOException");
    }
    catch (IOException e) {
      //ok
    }
  }


  @Test
  public void manyRecordsWritten_CouldAllBeReadBackUnchanged() throws IOException {
    final Record[] recordsToWrite = generateRecords(ENOUGH_RECORDS);
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(storage);
    }

    for (int i = 0; i < recordsToWrite.length; i++) {
      final Record recordWritten = recordsToWrite[i];
      final Record recordReadBack = Record.readFromStorage(storage, recordWritten.recordId);
      assertEquals(
        "Record[" + i + "][#" + recordWritten.recordId + "]: " +
        "written [" + recordWritten.payload + "], read back[" + recordReadBack.payload + "]",
        recordWritten.payload,
        recordReadBack.payload
      );
    }
  }

  @Test
  public void manyRecordsWritten_CouldAllBeReadBackUnchanged_EvenAfterStorageReopened() throws Exception {
    final Record[] recordsToWrite = generateRecords(ENOUGH_RECORDS);
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(storage);
    }

    storage.close();
    storage = openStorage(storagePath);

    for (int i = 0; i < recordsToWrite.length; i++) {
      final Record recordWritten = recordsToWrite[i];
      final Record recordReadBack = Record.readFromStorage(storage, recordWritten.recordId);
      assertEquals(
        "Record[" + i + "][#" + recordWritten.recordId + "]: " +
        "written [" + recordWritten.payload + "], read back[" + recordReadBack.payload + "]",
        recordWritten.payload,
        recordReadBack.payload
      );
    }
  }


  @Test
  public void manyRecordsWritten_ReWrittenWithSmallerSize_CouldAllBeReadBackUnchanged() throws IOException {
    final Record[] recordsToWrite = generateRecords(ENOUGH_RECORDS);

    //write initial records
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(storage);
    }

    //now re-write with smaller payloads:
    final Record[] recordsToReWrite = new Record[recordsToWrite.length];
    for (int i = 0; i < recordsToWrite.length; i++) {
      final Record recordWritten = recordsToWrite[i];
      recordsToReWrite[i] = recordWritten
        .withRandomPayloadOfSize(recordWritten.payload.length() / 2 + 1)
        .writeIntoStorage(storage);
    }

    for (int i = 0; i < recordsToReWrite.length; i++) {
      final Record recordWritten = recordsToWrite[i];
      final Record recordReWritten = recordsToReWrite[i];
      final Record recordReadBack = Record.readFromStorage(storage, recordReWritten.recordId);
      assertEquals(
        "Record[" + i + "][#" + recordReWritten.recordId + "]: " +
        "written[" + recordWritten.payload + "], " +
        "re-written[" + recordReWritten.payload + "], " +
        "read back[" + recordReadBack.payload + "]",
        recordReWritten.payload,
        recordReadBack.payload
      );
    }
  }

  @Test
  public void manyRecordsWritten_AndReWrittenWithLargerSize_AndCouldAllBeReadBackUnchanged() throws IOException {
    final Record[] recordsToWrite = generateRecords(ENOUGH_RECORDS);

    //write initial records
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(storage);
    }

    //now re-write with bigger payloads:
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i]
        .withRandomPayloadOfSize(recordsToWrite[i].payload.length() * 3)
        .writeIntoStorage(storage);
    }

    for (int i = 0; i < recordsToWrite.length; i++) {
      final Record recordWritten = recordsToWrite[i];
      final Record recordReadBack = Record.readFromStorage(storage, recordWritten.recordId);
      assertEquals(
        "Record[" + i + "][#" + recordWritten.recordId + "]: " +
        "written [" + recordWritten.payload + "], read back[" + recordReadBack.payload + "]",
        recordWritten.payload,
        recordReadBack.payload
      );
    }
  }


  @Test
  public void manyRecordsWritten_AndReWrittenWithLargerSize_AndCouldAllBeReadBackUnchanged_EventAfterStorageReopened() throws Exception {
    final Record[] recordsToWrite = generateRecords(ENOUGH_RECORDS);

    //write initial records
    for (Record record : recordsToWrite) {
      record.writeIntoStorage(storage);
    }

    storage.close();
    storage = openStorage(storagePath);

    //now re-write with bigger payloads:
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i]
        .withRandomPayloadOfSize(recordsToWrite[i].payload.length() * 3)
        .writeIntoStorage(storage);
    }

    for (int i = 0; i < recordsToWrite.length; i++) {
      final Record recordWritten = recordsToWrite[i];
      final Record recordReadBack = Record.readFromStorage(storage, recordWritten.recordId);
      assertEquals(
        "Record[" + i + "][#" + recordWritten.recordId + "]: " +
        "written [" + recordWritten.payload + "], read back[" + recordReadBack.payload + "]",
        recordWritten.payload,
        recordReadBack.payload
      );
    }
  }

  @Test
  public void reallocatedRecordCouldStillBeReadByOriginalId() throws IOException {
    final Record[] recordsToWrite = generateRecords(ENOUGH_RECORDS);
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(storage);
    }

    //choose record in the middle, so it will re-allocated quite far away from its origin:
    Record recordToGoNasty = recordsToWrite[recordsToWrite.length / 2];
    final int maxAttempts = 5;
    final IntSet subsequentIds = new IntArraySet();
    subsequentIds.add(recordToGoNasty.recordId);
    for (int attemptNo = 0; subsequentIds.size() < 2 && attemptNo < maxAttempts; attemptNo++) {
      //assume 2^3 is enough to beat capacity reserve:
      final int newPayloadSize = recordToGoNasty.payload.length() * 4;
      recordToGoNasty = recordToGoNasty
        .withRandomPayloadOfSize(newPayloadSize)
        .writeIntoStorage(storage);

      subsequentIds.add(recordToGoNasty.recordId);
    }
    assertTrue(
      "Record with 4^5=1024x payload increase _must_ be re-allocated at least once, i.e. it's id must change -> something wrong is happening, if it is not",
      subsequentIds.size() > 1
    );

    //Check: all the ids are lead to the same record (to the final version of recordToGoNasty):
    for (int recordId : subsequentIds) {
      final Record recordFetchedById = Record.readFromStorage(storage, recordId);
      assertEquals(
        "recordId[" + recordId + "] should be just an alias for " + recordToGoNasty.recordId,
        recordToGoNasty.payload,
        recordFetchedById.payload
      );
    }
  }

  @Test
  public void manyRecordsWritten_CouldAllBeReadBackUnchanged_ViaForEach() throws IOException {
    final Record[] recordsToWrite = generateRecords(ENOUGH_RECORDS);
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(storage);
    }

    final Int2ObjectMap<Record> recordsById = new Int2ObjectOpenHashMap<>();
    storage.forEach((recordId, recordCapacity, recordLength, payload) -> {
      if (storage.isRecordActual(recordLength)) {
        final Record record = new Record(recordId, Record.stringFromBuffer(payload));
        recordsById.put(recordId, record);
      }
      return true;
    });

    for (int i = 0; i < recordsToWrite.length; i++) {
      final Record recordWritten = recordsToWrite[i];
      final Record recordReadBack = recordsById.get(recordWritten.recordId);
      assertEquals(
        "Record[" + i + "][#" + recordWritten.recordId + "]: " +
        "written [" + recordWritten.payload + "], read back[" + recordReadBack.payload + "]",
        recordWritten.payload,
        recordReadBack.payload
      );
    }
  }

  @Test
  public void recordsLargerThanPageSizeRejectedByStorage_ButDoNotBreakStorage() throws IOException {
    //records must be on a single page, hence total size(record) <= pageSize
    // I don't want to expose recordSize/payloadSize internal relationship, hence
    // I just vary payload size _around_ PAGE_SIZE to check that they are either stored OK,
    // or throw IAE without breaking the storage
    final int margin = 16;
    final List<Record> recordsActuallyWritten = new ArrayList<>();
    try {
      for (int payloadSize = PAGE_SIZE - margin; payloadSize < PAGE_SIZE + margin; payloadSize++) {
        final Record recordWritten = Record.recordWithRandomPayload(payloadSize)
          .writeIntoStorage(storage);
        recordsActuallyWritten.add(recordWritten);

        final Record recordReadBack = Record.readFromStorage(storage, recordWritten.recordId);
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
    final Record recordWrittenOnTop = Record.recordWithRandomPayload(PAGE_SIZE / 2)
      .writeIntoStorage(storage);
    recordsActuallyWritten.add(recordWrittenOnTop);

    //and check everything that _was_ written -- could be read back:
    final List<Record> recordsReadBack = new ArrayList<>();
    storage.forEach((recordId, recordCapacity, recordLength, payload) -> {
      if (storage.isRecordActual(recordLength)) {
        final Record recordRead = new Record(recordId, Record.stringFromBuffer(payload));
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

  //TODO write/read records of size=0
  //TODO delete records


  //@Immutable
  private static class Record {
    public final int recordId;
    @NotNull
    public final String payload;

    public Record(final int recordId,
                  final @NotNull String payload) {
      this.recordId = recordId;
      this.payload = payload;
    }

    public Record(final String payload) {
      this(NULL_ID, payload);
    }

    public Record withPayload(final @NotNull String newPayload) {
      return new Record(recordId, newPayload);
    }

    public Record withRandomPayloadOfSize(final int size) {
      return withPayload(randomString(ThreadLocalRandom.current(), size));
    }

    public Record writeIntoStorage(final StreamlinedStorage storage) throws IOException {
      final int newRecordId = storage.writeToRecord(
        recordId,
        buffer -> {
          final ByteBuffer buff = (buffer.capacity() >= payload.length()) ?
                                  buffer : ByteBuffer.allocate(payload.length());
          return buff
            .clear()
            .put(payload.getBytes(US_ASCII));
        }
      );
      if (recordId == newRecordId) {
        return this;
      }
      else {
        return new Record(newRecordId, payload);
      }
    }

    public Record readFromStorage(final StreamlinedStorage storage) throws IOException {
      final int[] redirectedIdRef = new int[1];
      final String newPayload = storage.readRecord(
        recordId,
        buffer -> {
          return stringFromBuffer(buffer);
        },
        redirectedIdRef
      );
      final int newRecordId = redirectedIdRef[0];
      return new Record(newRecordId, newPayload);
    }

    @NotNull
    public static Record recordWithRandomPayload(final int payloadSize) {
      return new Record(randomString(ThreadLocalRandom.current(), payloadSize));
    }

    @NotNull
    public static String stringFromBuffer(final ByteBuffer buffer) {
      final int length = buffer.remaining();
      final byte[] bytes = new byte[length];
      buffer.get(bytes);
      return new String(bytes, US_ASCII);
    }

    public static Record readFromStorage(final StreamlinedStorage storage,
                                         final int recordId) throws IOException {
      return new Record(recordId, "").readFromStorage(storage);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final Record record = (Record)o;

      if (recordId != record.recordId) return false;
      if (!Objects.equals(payload, record.payload)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = recordId;
      result = 31 * result + (payload != null ? payload.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return "Record[#" + recordId + "]{" + payload + '}';
    }
  }

  @NotNull
  private static Record[] generateRecords(final int count) {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return Stream.generate(() -> {
        final int payloadSize = (int)Math.max(Math.min(rnd.nextExponential() * 30, 2000), 0);
        return randomString(rnd, payloadSize);
      })
      .limit(count)
      .map(Record::new)
      .toArray(Record[]::new);
  }

  @NotNull
  private static String randomString(final ThreadLocalRandom rnd,
                                     final int size) {
    final char[] chars = new char[size];
    for (int i = 0; i < chars.length; i++) {
      chars[i] = Character.forDigit(rnd.nextInt(0, 36), 36);
    }
    return new String(chars);
  }
}