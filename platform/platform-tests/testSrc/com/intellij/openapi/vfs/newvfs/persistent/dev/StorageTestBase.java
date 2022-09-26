// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.StorageLockContext;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static com.intellij.openapi.vfs.newvfs.persistent.AbstractAttributesStorage.NON_EXISTENT_ATTR_RECORD_ID;
import static com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedBlobStorage.NULL_ID;
import static org.junit.Assert.*;

/**
 *
 */
public abstract class StorageTestBase<S> {
  static {
    IndexDebugProperties.DEBUG = true;
  }

  protected static final StorageLockContext LOCK_CONTEXT = new StorageLockContext(true, true);
  protected static final int ENOUGH_RECORDS = 1_000_000;


  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  protected Path storagePath;
  protected S storage;

  // =============================================================================
  // RC: storages share no common interface, so the test itself used as an adapter,
  //    adopting them to the common interface:
  protected abstract S openStorage(final Path pathToStorage) throws Exception;

  protected abstract void closeStorage(final S storage) throws Exception;

  protected abstract boolean hasRecord(final S storage,
                                       final int recordId) throws Exception;


  protected abstract StorageRecord readRecord(final S storage,
                                              final int recordId) throws Exception;

  protected abstract StorageRecord writeRecord(final StorageRecord record,
                                               final S storage) throws Exception;

  protected abstract void deleteRecord(final int recordId,
                                       final S storage) throws Exception;

  //======== end of adapters =====================================================


  @Before
  public void setUp() throws Exception {
    storagePath = temporaryFolder.newFile().toPath();
    storage = openStorage(storagePath);
  }

  @After
  public void tearDown() throws Exception {
    if (storage != null) {
      closeStorage(storage);
    }
    if (Files.exists(storagePath)) {
      System.out.printf("[%s]: %d\n", storagePath, Files.size(storagePath));
    }
  }

  @Test
  public void singleWrittenRecord_CouldBeReadBackUnchanged() throws Exception {
    final StorageRecord recordToWrite = new StorageRecord("ABC")
      .writeIntoStorage(this, storage);
    assertNotEquals(
      NULL_ID,
      recordToWrite.recordId
    );

    final StorageRecord recordRead = StorageRecord.readFromStorage(this, storage, recordToWrite.recordId);
    assertEquals(
      recordToWrite.payload,
      recordRead.payload
    );
  }

  @Test
  public void singleWrittenRecord_ReWrittenWithSmallerSize_CouldBeReadBackUnchanged() throws Exception {
    final StorageRecord recordWritten = new StorageRecord("1234567890")
      .writeIntoStorage(this, storage);

    final StorageRecord recordOverWritten = recordWritten
      .withPayload("09876")
      .writeIntoStorage(this, storage);

    final StorageRecord recordRead = StorageRecord.readFromStorage(this, storage, recordWritten.recordId);
    assertEquals(
      recordOverWritten.payload,
      recordRead.payload
    );
  }


  @Test
  public void manyRecordsWritten_CouldAllBeReadBackUnchanged() throws Exception {
    final StorageRecord[] recordsToWrite = StorageTestBase.generateRecords(ENOUGH_RECORDS);
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(this, storage);
    }

    for (int i = 0; i < recordsToWrite.length; i++) {
      final StorageRecord recordWritten = recordsToWrite[i];
      final StorageRecord recordReadBack = StorageRecord.readFromStorage(this, storage, recordWritten.recordId);
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
    final StorageRecord[] recordsToWrite = StorageTestBase.generateRecords(ENOUGH_RECORDS);
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(this, storage);
    }

    closeStorage(storage);
    storage = openStorage(storagePath);

    for (int i = 0; i < recordsToWrite.length; i++) {
      final StorageRecord recordWritten = recordsToWrite[i];
      final StorageRecord recordReadBack = StorageRecord.readFromStorage(this, storage, recordWritten.recordId);
      assertEquals(
        "Record[" + i + "][#" + recordWritten.recordId + "]: " +
        "written [" + recordWritten.payload + "], read back[" + recordReadBack.payload + "]",
        recordWritten.payload,
        recordReadBack.payload
      );
    }
  }

  @Test
  public void manyRecordsWritten_ReWrittenWithSmallerSize_CouldAllBeReadBackUnchanged() throws Exception {
    final StorageRecord[] recordsToWrite = StorageTestBase.generateRecords(ENOUGH_RECORDS);

    //write initial records
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(this, storage);
    }

    //now re-write with smaller payloads:
    final StorageRecord[] recordsToReWrite = new StorageRecord[recordsToWrite.length];
    for (int i = 0; i < recordsToWrite.length; i++) {
      final StorageRecord recordWritten = recordsToWrite[i];
      recordsToReWrite[i] = recordWritten
        .withRandomPayloadOfSize(recordWritten.payload.length() / 2 + 1)
        .writeIntoStorage(this, storage);
    }

    for (int i = 0; i < recordsToReWrite.length; i++) {
      final StorageRecord recordWritten = recordsToWrite[i];
      final StorageRecord recordReWritten = recordsToReWrite[i];
      final StorageRecord recordReadBack = StorageRecord.readFromStorage(this, storage, recordReWritten.recordId);
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
  public void manyRecordsWritten_AndReWrittenWithLargerSize_AndCouldAllBeReadBackUnchanged() throws Exception {
    final StorageRecord[] recordsToWrite = StorageTestBase.generateRecords(ENOUGH_RECORDS);

    //write initial records
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(this, storage);
    }

    //now re-write with bigger payloads:
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i]
        .withRandomPayloadOfSize(recordsToWrite[i].payload.length() * 3)
        .writeIntoStorage(this, storage);
    }

    for (int i = 0; i < recordsToWrite.length; i++) {
      final StorageRecord recordWritten = recordsToWrite[i];
      final StorageRecord recordReadBack = StorageRecord.readFromStorage(this, storage, recordWritten.recordId);
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
    final StorageRecord[] recordsToWrite = StorageTestBase.generateRecords(ENOUGH_RECORDS);

    //write initial records
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i].writeIntoStorage(this, storage);
    }

    closeStorage(storage);
    storage = openStorage(storagePath);

    //now re-write with bigger payloads:
    for (int i = 0; i < recordsToWrite.length; i++) {
      recordsToWrite[i] = recordsToWrite[i]
        .withRandomPayloadOfSize(recordsToWrite[i].payload.length() * 3)
        .writeIntoStorage(this, storage);
    }

    closeStorage(storage);
    storage = openStorage(storagePath);

    for (int i = 0; i < recordsToWrite.length; i++) {
      final StorageRecord recordWritten = recordsToWrite[i];
      final StorageRecord recordReadBack = StorageRecord.readFromStorage(this, storage, recordWritten.recordId);
      assertEquals(
        "Record[" + i + "][#" + recordWritten.recordId + "]: " +
        "written [" + recordWritten.payload + "], read back[" + recordReadBack.payload + "]",
        recordWritten.payload,
        recordReadBack.payload
      );
    }
  }

  //TODO RC: test space reclamation (not implemented yet): add/delete records multiple time, check storage.size is not
  //         growing infinitely

  @NotNull
  protected static StorageRecord[] generateRecords(final int count) {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return Stream.generate(() -> {
        final int payloadSize = (int)Math.max(Math.min(rnd.nextExponential() * 30, 2000), 0);
        return StorageTestBase.randomString(rnd, payloadSize);
      })
      .limit(count)
      .map(StorageRecord::new)
      .toArray(StorageRecord[]::new);
  }

  @NotNull
  protected static String randomString(final ThreadLocalRandom rnd,
                                       final int size) {
    final char[] chars = new char[size];
    for (int i = 0; i < chars.length; i++) {
      chars[i] = Character.forDigit(rnd.nextInt(0, 36), 36);
    }
    return new String(chars);
  }

  //@Immutable
  protected static class StorageRecord {
    public final int recordId;
    @NotNull
    public final String payload;

    public StorageRecord(final int recordId,
                         final @NotNull String payload) {
      this.recordId = recordId;
      this.payload = payload;
    }

    public StorageRecord(final String payload) {
      this(NULL_ID, payload);
    }

    public StorageRecord withPayload(final @NotNull String newPayload) {
      return new StorageRecord(recordId, newPayload);
    }

    public StorageRecord withRandomPayloadOfSize(final int size) {
      return withPayload(StorageTestBase.randomString(ThreadLocalRandom.current(), size));
    }

    public <S> StorageRecord writeIntoStorage(final StorageTestBase<S> test,
                                              final S storage) throws Exception {
      return test.writeRecord(this, storage);
    }

    public <S> StorageRecord readFromStorage(final StorageTestBase<S> test,
                                             final S storage) throws Exception {
      return test.readRecord(storage, recordId);
    }

    @NotNull
    public static StorageRecord recordWithRandomPayload(final int payloadSize) {
      return new StorageRecord(randomString(ThreadLocalRandom.current(), payloadSize));
    }


    public static <S> StorageRecord readFromStorage(final StorageTestBase<S> test,
                                                    final S storage,
                                                    final int recordId) throws Exception {
      return new StorageRecord(recordId, "").readFromStorage(test, storage);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final StorageRecord record = (StorageRecord)o;

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
}
