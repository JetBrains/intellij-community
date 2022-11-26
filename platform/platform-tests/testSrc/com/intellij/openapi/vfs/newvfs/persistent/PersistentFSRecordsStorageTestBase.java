// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.*;

/**
 *
 */
public abstract class PersistentFSRecordsStorageTestBase<T extends PersistentFSRecordsStorage> {

  protected final int maxRecordsToInsert;

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  protected Path storagePath;
  protected T storage;

  protected PersistentFSRecordsStorageTestBase(final int maxRecordsToInsert) { this.maxRecordsToInsert = maxRecordsToInsert; }

  @Before
  public void setUp() throws Exception {
    storagePath = temporaryFolder.newFile().toPath();

    storage = openStorage(storagePath);
    //System.out.println("File: " + storagePath);
  }

  @NotNull
  protected abstract T openStorage(final Path storageFile) throws IOException;

  @Test
  public void singleWrittenRecord_CouldBeReadBackUnchanged() throws Exception {
    final int recordId = storage.allocateRecord();
    final FSRecord recordOriginal = generateRecordFields(recordId);

    recordOriginal.updateInStorage(storage);
    final FSRecord recordReadBack = FSRecord.readFromStorage(storage, recordOriginal.id);

    assertEqualExceptModCount("Record updated and record read back by same ID should be equal", recordOriginal, recordReadBack);
  }

  @Test
  public void manyRecordsWritten_CouldBeReadBackUnchanged() throws Exception {
    final FSRecord[] records = new FSRecord[maxRecordsToInsert];

    for (int i = 0; i < records.length; i++) {
      final int recordId = storage.allocateRecord();
      records[i] = generateRecordFields(recordId);
      records[i].updateInStorage(storage);
    }

    final Map<FSRecord, FSRecord> incorrectlyReadBackRecords = new HashMap<>();
    for (int i = 0; i < records.length; i++) {
      final FSRecord recordOriginal = records[i];
      final FSRecord recordReadBack = FSRecord.readFromStorage(storage, recordOriginal.id);
      if (!recordOriginal.equalsExceptModCount(recordReadBack)) {
        incorrectlyReadBackRecords.put(recordOriginal, recordReadBack);
      }
    }
    if (!incorrectlyReadBackRecords.isEmpty()) {
      fail("Records read back should be all equal to their originals, but " + incorrectlyReadBackRecords.size() +
           " different: \n" +
           incorrectlyReadBackRecords.entrySet().stream()
             .sorted(comparing(e -> e.getKey().id))
             .map(e -> e.getKey() + "\n" + e.getValue())
             .collect(joining("\n"))
      );
    }
  }

  @Test
  public void manyRecordsWritten_MultiThreadedWithoutContention_CouldBeReadBackUnchanged() throws Exception {
    final FSRecord[] records = new FSRecord[maxRecordsToInsert];

    for (int i = 0; i < records.length; i++) {
      final int recordId = storage.allocateRecord();
      records[i] = generateRecordFields(recordId);
    }

    final int threadsCount = Runtime.getRuntime().availableProcessors() * 2;

    final Thread[] threads = new Thread[threadsCount];
    for (int i = 0; i < threads.length; i++) {
      final int threadNo = i;
      threads[threadNo] = new Thread(() -> {
        //each thread updates own subset of all records:
        for (int recordNo = threadNo; recordNo < records.length; recordNo += threadsCount) {
          try {
            final FSRecord record = records[recordNo];
            record.updateInStorage(storage);
          }
          catch (IOException e) {
            ExceptionUtil.rethrow(e);
          }
        }
      }, "updater-" + threadNo);
    }
    for (Thread thread : threads) {
      thread.start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    for (int i = 0; i < records.length; i++) {
      final FSRecord recordOriginal = records[i];
      final FSRecord recordReadBack = FSRecord.readFromStorage(storage, recordOriginal.id);
      assertEqualExceptModCount("[" + i + "]: fields inserted and fields read back by same ID should be equal",
                                recordOriginal,
                                recordReadBack
      );
    }
  }

  @Test
  public void manyRecordsWritten_MultiThreadedWithHighContention_CouldBeReadBackUnchanged() throws Exception {
    final FSRecord[] records = new FSRecord[maxRecordsToInsert];

    for (int i = 0; i < records.length; i++) {
      final int recordId = storage.allocateRecord();
      records[i] = generateRecordFields(recordId);
    }

    final int threadsCount = Runtime.getRuntime().availableProcessors() * 2;

    final Thread[] threads = new Thread[threadsCount];
    for (int threadNo = 0; threadNo < threads.length; threadNo++) {
      threads[threadNo] = new Thread(() -> {
        //each thread updates each record (so each record is updated threadCount times):
        for (FSRecord fsRecord : records) {
          try {
            final FSRecord record = fsRecord;
            record.updateInStorage(storage);
          }
          catch (IOException e) {
            ExceptionUtil.rethrow(e);
          }
        }
      }, "updater-" + threadNo);
    }

    for (Thread thread : threads) {
      thread.start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    for (int i = 0; i < records.length; i++) {
      final FSRecord recordOriginal = records[i];
      final FSRecord recordReadBack = FSRecord.readFromStorage(storage, recordOriginal.id);
      assertEqualExceptModCount("[" + i + "]: fields inserted and fields read back by same ID should be equal", recordOriginal,
                                recordReadBack
      );
    }
  }

  /* =================== PERSISTENCE: values are kept through close-and-reopen =============================== */

  @Test
  public void emptyStorageRemainsEmptyButHeaderFieldsStillRestored_AfterStorageClosedAndReopened() throws IOException {
    final int version = 10;
    final int connectionStatus = PersistentFSHeaders.CONNECTED_MAGIC;

    storage.setVersion(version);
    storage.setConnectionStatus(connectionStatus);
    final int globalModCount = storage.getGlobalModCount();
    assertTrue("Storage must be 'dirty' after few header fields were written",
               storage.isDirty());

    final long lengthBeforeClose = storage.length();
    assertEquals("No records were allocated => (storage size == HEADER_SIZE)",
                 PersistentFSHeaders.HEADER_SIZE,
                 lengthBeforeClose
    );

    //close storage, and reopen from same file again:
    storage.close();
    final T storageReopened = openStorage(storagePath);
    storage = storageReopened;//for tearDown to successfully close it

    //now re-read values from re-opened storage:
    assertFalse("Storage must be !dirty since no modifications since open", storageReopened.isDirty());
    assertEquals("globalModCount", globalModCount, storageReopened.getGlobalModCount());
    assertEquals("version", version, storageReopened.getVersion());
    assertEquals("connectionStatus", connectionStatus, storageReopened.getConnectionStatus());
    assertEquals("length", lengthBeforeClose, storageReopened.length());
  }

  @Test
  public void singleWrittenRecord_CouldBeReadBackUnchanged_AfterStorageClosedAndReopened() throws Exception {
    final int recordId = storage.allocateRecord();
    final FSRecord recordWritten = generateRecordFields(recordId);
    recordWritten.updateInStorage(storage);

    storage.close();
    final T storageReopened = openStorage(storagePath);
    storage = storageReopened;//for tearDown to successfully close it

    final FSRecord recordReadBack = FSRecord.readFromStorage(storage, recordId);
    assertEqualExceptModCount("Record written should be read back as-is", recordWritten, recordReadBack);
  }

  @After
  public void tearDown() throws Exception {
    storage.close();
  }


  /* ========================== INFRASTRUCTURE =============================================================== */

  /**
   * Plain data holder
   */
  public static final class FSRecord {
    public final int id;

    public int parentRef;
    public int nameRef;
    public int flags;
    public int attributeRef;
    public int contentRef;
    public long timestamp;
    public int modCount;
    public long length;

    public static FSRecord readFromStorage(final PersistentFSRecordsStorage storage,
                                           final int recordId) throws IOException {
      return new FSRecord(recordId,
                          storage.getParent(recordId),
                          storage.getNameId(recordId),
                          storage.getFlags(recordId),
                          storage.getAttributeRecordId(recordId),
                          storage.getContentRecordId(recordId),
                          storage.getTimestamp(recordId),
                          storage.getModCount(recordId),
                          storage.getLength(recordId)
      );
    }

    public FSRecord(final int id,
                    final int parentRef,
                    final int nameRef,
                    final int flags,
                    final int attributeRef,
                    final int contentRef,
                    final long timestamp,
                    final int modCount,
                    final long length) {
      this.id = id;
      this.parentRef = parentRef;
      this.nameRef = nameRef;
      this.flags = flags;
      this.attributeRef = attributeRef;
      this.contentRef = contentRef;
      this.timestamp = timestamp;
      this.modCount = modCount;
      this.length = length;
    }

    public void updateInStorage(final PersistentFSRecordsStorage storage) throws IOException {
      storage.setParent(id, this.parentRef);
      storage.setNameId(id, this.nameRef);
      storage.setFlags(id, this.flags);
      storage.setAttributeRecordId(id, this.attributeRef);
      storage.setContentRecordId(id, this.contentRef);
      storage.putTimestamp(id, this.timestamp);
      storage.putLength(id, this.length);
      //storage.overwriteModCount(id, this.modCount);
    }

    //public FSRecord insertInStorage(final PersistentFSRecordsStorage storage) throws IOException {
    //  final int id = storage.allocateRecord();
    //  final FSRecord recordWithId = assignId(id);
    //  recordWithId.updateInStorage(storage);
    //  return recordWithId;
    //}

    public FSRecord assignId(final int id) {
      return new FSRecord(id, parentRef, nameRef, flags, attributeRef, contentRef, timestamp, modCount, length);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FSRecord record = (FSRecord)o;

      if (id != record.id) return false;
      if (parentRef != record.parentRef) return false;
      if (nameRef != record.nameRef) return false;
      if (flags != record.flags) return false;
      if (attributeRef != record.attributeRef) return false;
      if (contentRef != record.contentRef) return false;
      if (timestamp != record.timestamp) return false;
      if (modCount != record.modCount) return false;
      if (length != record.length) return false;

      return true;
    }

    public boolean equalsExceptModCount(FSRecord other) {
      if (this == other) return true;
      if (other == null) return false;

      if (id != other.id) return false;
      if (parentRef != other.parentRef) return false;
      if (nameRef != other.nameRef) return false;
      if (flags != other.flags) return false;
      if (attributeRef != other.attributeRef) return false;
      if (contentRef != other.contentRef) return false;
      if (timestamp != other.timestamp) return false;
      if (length != other.length) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = id;
      result = 31 * result + parentRef;
      result = 31 * result + nameRef;
      result = 31 * result + flags;
      result = 31 * result + attributeRef;
      result = 31 * result + contentRef;
      result = 31 * result + (int)(timestamp ^ (timestamp >>> 32));
      result = 31 * result + modCount;
      result = 31 * result + (int)(length ^ (length >>> 32));
      return result;
    }

    @Override
    public String toString() {
      return "FSRecord{" +
             "id=" + id +
             ", parentRef=" + parentRef +
             ", nameRef=" + nameRef +
             ", flags=" + flags +
             ", attributeRef=" + attributeRef +
             ", contentRef=" + contentRef +
             ", timestamp=" + timestamp +
             ", length=" + length +
             ", modCount=" + modCount +
             '}';
    }
  }

  @NotNull
  private static FSRecord generateRecordFields(final int recordId) {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return new FSRecord(
      recordId,
      rnd.nextInt(),
      rnd.nextInt(1, Integer.MAX_VALUE),//nameId should be >0
      rnd.nextInt(),
      rnd.nextInt(),
      rnd.nextInt(),
      //rnd.nextBoolean() ? System.currentTimeMillis() : Long.MAX_VALUE,
      System.currentTimeMillis(),
      -1,
      Long.MAX_VALUE
      //rnd.nextBoolean() ? rnd.nextLong(0, Long.MAX_VALUE) : Long.MAX_VALUE //check extreme long values
    );
  }

  private static void assertEqualExceptModCount(final String message,
                                                final FSRecord recordOriginal,
                                                final FSRecord recordReadBack) {
    assertTrue(message + "\n" +
                 "\toriginal:  " + recordOriginal + "\n" +
                 "\tread back: " + recordReadBack + "\n",
                 recordOriginal.equalsExceptModCount(recordReadBack));
  }
}
