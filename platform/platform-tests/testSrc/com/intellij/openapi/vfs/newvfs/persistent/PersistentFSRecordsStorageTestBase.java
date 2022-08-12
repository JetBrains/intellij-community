// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public abstract class PersistentFSRecordsStorageTestBase<T extends PersistentFSRecordsStorage> {

  protected final int maxRecordsToInsert;

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  protected File storageFile;
  protected T storage;

  protected PersistentFSRecordsStorageTestBase(final int maxRecordsToInsert) { this.maxRecordsToInsert = maxRecordsToInsert; }

  @Before
  public void setUp() throws Exception {
    storageFile = temporaryFolder.newFile();

    storage = openStorage(storageFile, maxRecordsToInsert);
  }

  @NotNull
  protected abstract T openStorage(final File storageFile,
                                   final int maxRecordsToInsert) throws IOException;

  @Test
  public void singleWrittenRecordFieldCouldBeReadBackUnchanged() throws Exception {
    final int recordId = storage.allocateRecord();
    final FSRecord recordOriginal = PersistentFSRecordsStorageTestBase.generateRecordFields(recordId);

    recordOriginal.updateInStorage(storage);
    final FSRecord recordReadBack = FSRecord.readFromStorage(storage, recordOriginal.id);

    assertEquals("Fields inserted and fields read back by same ID should be equal",
                 recordOriginal,
                 recordReadBack);
  }

  @Test
  public void manyRecordsWrittenCouldBeReadBackUnchanged() throws Exception {
    final FSRecord[] records = new FSRecord[maxRecordsToInsert];

    for (int i = 0; i < records.length; i++) {
      final int recordId = storage.allocateRecord();
      records[i] = PersistentFSRecordsStorageTestBase.generateRecordFields(recordId);
      records[i].updateInStorage(storage);
    }

    for (int i = 0; i < records.length; i++) {
      final FSRecord recordOriginal = records[i];
      final FSRecord recordReadBack = FSRecord.readFromStorage(storage, recordOriginal.id);
      assertEquals("[" + i + "]: fields inserted and fields read back by same ID should be equal",
                   recordOriginal,
                   recordReadBack);
    }
  }

  @Test
  public void manyRecordsWritten_MultiThreadedWithoutContention_CouldBeReadBackUnchanged() throws Exception {
    final FSRecord[] records = new FSRecord[maxRecordsToInsert];

    for (int i = 0; i < records.length; i++) {
      final int recordId = storage.allocateRecord();
      records[i] = PersistentFSRecordsStorageTestBase.generateRecordFields(recordId);
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
      assertEquals("[" + i + "]: fields inserted and fields read back by same ID should be equal",
                   recordOriginal,
                   recordReadBack);
    }
  }

  @Test
  public void manyRecordsWritten_MultiThreadedWithHighContention_CouldBeReadBackUnchanged() throws Exception {
    final FSRecord[] records = new FSRecord[maxRecordsToInsert];

    for (int i = 0; i < records.length; i++) {
      final int recordId = storage.allocateRecord();
      records[i] = PersistentFSRecordsStorageTestBase.generateRecordFields(recordId);
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
      assertEquals("[" + i + "]: fields inserted and fields read back by same ID should be equal",
                   recordOriginal,
                   recordReadBack);
    }
  }

  @Test
  public void writtenRecordCouldBeReadBackUnchangedAfterStorageCloseAndReopen() throws Exception {
    final Path file = storageFile.toPath();

    final PersistentFSRecordsStorage storage = PersistentFSRecordsStorage.createStorage(file);
    storage.allocateRecord();
    final int recordId = storage.allocateRecord();
    storage.setParent(recordId, 10);
    storage.setNameId(recordId, 110);
    final int globalModCount = storage.incGlobalModCount();
    storage.close();

    final PersistentFSRecordsStorage storageReopened = PersistentFSRecordsStorage.createStorage(file);
    assertEquals(globalModCount, storageReopened.getGlobalModCount());
    assertEquals(110, storageReopened.getNameId(recordId));
    assertEquals(10, storageReopened.getParent(recordId));
    //assertEquals(1, storageReopened.length());
  }

  @After
  public void tearDown() throws Exception {
    storage.close();
    //storageFile.delete();
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
      storage.setModCount(id, this.modCount);
      storage.putLength(id, this.length);
    }

    public FSRecord insertInStorage(final PersistentFSRecordsStorage storage) throws IOException {
      final int id = storage.allocateRecord();
      storage.setParent(id, this.parentRef);
      storage.setNameId(id, this.nameRef);
      storage.setFlags(id, this.flags);
      storage.setAttributeRecordId(id, this.attributeRef);
      storage.setContentRecordId(id, this.contentRef);
      storage.putTimestamp(id, this.timestamp);
      storage.setModCount(id, this.modCount);
      storage.putLength(id, this.length);
      return assignId(id);
    }

    public FSRecord assignId(int id) {
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
             ", modCount=" + modCount +
             ", length=" + length +
             '}';
    }
  }

  @NotNull
  private static FSRecord generateRecordFields(final int recordId) {
    return new FSRecord(
      recordId,
      ThreadLocalRandom.current().nextInt(),
      ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE),//nameId should be >0
      ThreadLocalRandom.current().nextInt(),
      ThreadLocalRandom.current().nextInt(),
      ThreadLocalRandom.current().nextInt(),
      System.currentTimeMillis(),
      ThreadLocalRandom.current().nextInt(),
      Long.MAX_VALUE //check extreme long values
    );
  }
}
