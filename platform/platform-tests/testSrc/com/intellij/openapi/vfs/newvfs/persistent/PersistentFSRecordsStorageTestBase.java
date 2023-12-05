// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.ExceptionUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSHeaders.CONNECTED_MAGIC;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.*;

/**
 *
 */
public abstract class PersistentFSRecordsStorageTestBase<T extends PersistentFSRecordsStorage> {

  protected final int maxRecordsToInsert;
  /** Which method to use for updating records in storage (different APIs available) */
  protected final @NotNull UpdateAPIMethod recordsUpdateMethod;

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  protected Path storagePath;
  protected T storage;

  protected PersistentFSRecordsStorageTestBase(int maxRecordsToInsert,
                                               @NotNull UpdateAPIMethod method) {
    this.maxRecordsToInsert = maxRecordsToInsert;
    recordsUpdateMethod = method;
  }

  protected PersistentFSRecordsStorageTestBase(int maxRecordsToInsert) {
    this(maxRecordsToInsert, DEFAULT_API_UPDATE_METHOD);
  }

  @Before
  public void setUp() throws Exception {
    storagePath = temporaryFolder.newFile().toPath();

    storage = openStorage(storagePath);
    //System.out.println("File: " + storagePath);
  }

  @NotNull
  protected abstract T openStorage(final Path storageFile) throws IOException;


  @Test
  public void recordsCountIsZeroForEmptyStorage() {
    assertEquals(
      "Should be 0 records in the empty storage",
      0,
      storage.recordsCount()
    );
  }

  @Test
  public void firstRecordInserted_MustGetValidId() throws Exception {
    final int recordId = storage.allocateRecord();

    assertTrue("First inserted record should get id (=" + recordId + ") > FSRecords.RESERVED_FILE_ID",
               recordId > FSRecords.NULL_FILE_ID //TODO replace with universal NULL_ID
    );

    assertEquals("Should be 1 (just inserted) record in the storage",
                 1,
                 storage.recordsCount()
    );
  }

  @Test
  public void maxAllocatedId_IsStored_AndRestoredAfterStorageReopened() throws Exception {
    final int allocatedRecordId = storage.allocateRecord();

    assertTrue("First inserted record should get id (=" + allocatedRecordId + ") > FSRecords.NULL_FILE_ID",
               allocatedRecordId > FSRecords.NULL_FILE_ID //TODO replace with universal NULL_ID
    );

    assertEquals("Should be 1 (just inserted) record in the storage",
                 1,
                 storage.recordsCount()
    );

    storage.close();
    final T storageReopened = openStorage(storagePath);
    storage = storageReopened;//for tearDown to successfully close it

    assertEquals(
      "Max allocated id must be kept after reopening",
      storageReopened.maxAllocatedID(),
      allocatedRecordId
    );
  }

  @Test
  public void cleanRecord_throwsException_IfRecordIdIsOutsideOfAllocatedRange() throws IOException {
    final int cleanedRecordId = 10;
    try {
      storage.cleanRecord(cleanedRecordId);
      fail(".cleanRecord(" +
           cleanedRecordId +
           ") must throw IndexOutOfBoundsException since there record " +
           cleanedRecordId +
           " is not allocated");
    }
    catch (IndexOutOfBoundsException e) {
      //OK
    }
  }

  @Test
  public void singleWrittenRecord_CouldBeReadBackUnchanged() throws Exception {
    final int recordId = storage.allocateRecord();
    final FSRecord recordOriginal = generateRecordFields(recordId);

    recordsUpdateMethod.updateInStorage(recordOriginal, storage);

    assertEquals("Should be 1 (just inserted) record in the storage",
                 1,
                 storage.recordsCount()
    );

    final FSRecord recordReadBack = FSRecord.readFromStorage(storage, recordOriginal.id);

    assertEqualExceptModCount("Record updated and record read back by same ID should be equal", recordOriginal, recordReadBack);
  }

  @Test
  public void singleWrittenRecord_MakeStorageDirty_AndForceMakeItNonDirtyAgain() throws Exception {
    final int recordId = storage.allocateRecord();
    final FSRecord recordOriginal = generateRecordFields(recordId);

    recordsUpdateMethod.updateInStorage(recordOriginal, storage);
    assertTrue("Record is written -- storage must be dirty",
               storage.isDirty());
    storage.force();
    assertFalse(".force() is called -> storage must be !dirty",
                storage.isDirty());
  }

  @Test
  public void singleRecordWritten_AndCleaned_ReadsBackAsAllZeroes() throws Exception {
    final int recordId = storage.allocateRecord();
    final FSRecord recordOriginal = generateRecordFields(recordId);

    recordsUpdateMethod.updateInStorage(recordOriginal, storage);
    storage.cleanRecord(recordId);
    final FSRecord recordReadBack = FSRecord.readFromStorage(storage, recordOriginal.id);

    //all fields are 0:
    assertEquals("Cleaned record must have parent=0", recordReadBack.parentRef, 0);
    assertEquals("Cleaned record must have name=0", recordReadBack.nameRef, 0);
    assertEquals("Cleaned record must have flags=0", recordReadBack.flags, 0);
    assertEquals("Cleaned record must have content=0", recordReadBack.contentRef, 0);
    assertEquals("Cleaned record must have attribute=0", recordReadBack.attributeRef, 0);
    assertEquals("Cleaned record must have length=0", recordReadBack.length, 0);
    assertEquals("Cleaned record must have timestamp=0", recordReadBack.timestamp, 0);
    assertEquals("Cleaned record must have modCount=0", recordReadBack.modCount, 0);
  }

  @Test
  @Ignore("Not true now: storages ignores .close(), and also .close() is not idempotent (fails being called 2nd time)")
  public void closedStorageFailsOnMethodCalls() throws IOException {
    storage.close();
    try {
      storage.allocateRecord();
      fail(".allocateRecords() must fail on closed storage");
    }
    catch (Exception e) {
      //OK
    }
    try {
      storage.getTimestamp();
      fail(".getTimestamp() must fail on closed storage");
    }
    catch (Exception e) {
      //OK
    }

    try {
      storage.getVersion();
      fail(".getVersion() must fail on closed storage");
    }
    catch (Exception e) {
      //OK
    }
    try {
      storage.getGlobalModCount();
      fail(".getGlobalModCount() must fail on closed storage");
    }
    catch (Exception e) {
      //OK
    }
  }

  @Test
  public void closeAndRemoveAllFiles_cleansUpEverything_newStorageCreatedFromSameFilenameIsEmpty() throws Exception {
    final int enoughTry = 16;
    File recordsFile = File.createTempFile("records", "dat");
    Path recordsPath = recordsFile.toPath();

    for (int tryNo = 0; tryNo < enoughTry; tryNo++) {
      T storage = openStorage(recordsPath);
      try {
        int version = storage.getVersion();
        int recordsCount = storage.recordsCount();
        assertEquals(
          "Storage must be created anew each time => version must be 0",
          0,
          version
        );
        assertEquals(
          "Storage must be created anew each time => recordsCount must be 0",
          0,
          recordsCount
        );

        storage.setVersion(42);
      }
      finally {
        storage.closeAndClean();
      }
      assertFalse(
        recordsPath + " must be deleted",
        Files.exists(recordsPath)
      );
    }
  }


  @Test
  public void manyRecordsWritten_CouldBeReadBackUnchanged() throws Exception {
    final FSRecord[] records = new FSRecord[maxRecordsToInsert];

    for (int i = 0; i < records.length; i++) {
      final int recordId = storage.allocateRecord();
      records[i] = generateRecordFields(recordId);
      recordsUpdateMethod.updateInStorage(records[i], storage);
    }

    assertEquals(
      "storage.recordsCount() should be == number of inserted records",
      records.length,
      storage.recordsCount()
    );

    final Map<FSRecord, FSRecord> incorrectlyReadBackRecords = new HashMap<>();
    for (final FSRecord recordOriginal : records) {
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
  public void processRecords_reportsAllRecordsIdWrittenBefore() throws Exception {
    final FSRecord[] records = new FSRecord[maxRecordsToInsert];

    final IntSet recordIdsWritten = new IntOpenHashSet();
    for (int i = 0; i < records.length; i++) {
      final int recordId = storage.allocateRecord();
      records[i] = generateRecordFields(recordId);
      recordsUpdateMethod.updateInStorage(records[i], storage);

      recordIdsWritten.add(recordId);
    }

    assertEquals(
      "storage.recordsCount() should be == number of inserted records",
      records.length,
      storage.recordsCount()
    );

    final IntSet recordIdsReadBack = new IntOpenHashSet();
    storage.processAllRecords((fileId, nameId, flags, parentId, attributeRecordId, contentId, corrupted) -> {
      recordIdsReadBack.add(fileId);
    });

    if (!recordIdsReadBack.equals(recordIdsWritten)) {
      final int[] missedIds = recordIdsWritten.intStream()
        .filter(id -> !recordIdsReadBack.contains(id))
        .sorted().toArray();
      final int[] excessiveIds = recordIdsReadBack.intStream()
        .filter(id -> !recordIdsWritten.contains(id))
        .sorted().toArray();
      fail("fileIds written and read back should be the same, but: \n" +
           "\tmissed ids:    " + Arrays.toString(missedIds) + ", \n" +
           "\texcessive ids: " + Arrays.toString(excessiveIds));
    }

    assertEquals(
      recordIdsReadBack,
      recordIdsWritten
    );
  }

  @Test
  public void markModified_JustIncrementsRecordVersion_OtherRecordFieldsAreUnchanged() throws Exception {
    final FSRecord[] recordsToInsert = new FSRecord[maxRecordsToInsert];

    for (int i = 0; i < recordsToInsert.length; i++) {
      final int recordId = storage.allocateRecord();
      recordsToInsert[i] = generateRecordFields(recordId);
      recordsUpdateMethod.updateInStorage(recordsToInsert[i], storage);
    }

    final FSRecord[] recordsReadBack = new FSRecord[maxRecordsToInsert];
    for (int i = 0; i < recordsToInsert.length; i++) {
      recordsReadBack[i] = FSRecord.readFromStorage(storage, recordsToInsert[i].id);
    }

    for (FSRecord record : recordsReadBack) {
      storage.markRecordAsModified(record.id);
    }

    final Map<FSRecord, FSRecord> incorrectlyReadBackRecords = new HashMap<>();
    for (final FSRecord recordReadBack : recordsReadBack) {
      final FSRecord recordReadBackAgain = FSRecord.readFromStorage(storage, recordReadBack.id);
      assertTrue(
        "Record.modCount was increased by .markRecordAsModified",
        recordReadBackAgain.modCount > recordReadBack.modCount
      );
      //...but everything else in the record is unchanged:
      if (!recordReadBack.equalsExceptModCount(recordReadBackAgain)) {
        incorrectlyReadBackRecords.put(recordReadBack, recordReadBack);
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

    final int threadsCount = Runtime.getRuntime().availableProcessors();

    final Thread[] threads = new Thread[threadsCount];
    for (int i = 0; i < threads.length; i++) {
      final int threadNo = i;
      threads[threadNo] = new Thread(() -> {
        //each thread updates own subset of all records:
        for (int recordNo = threadNo; recordNo < records.length; recordNo += threadsCount) {
          try {
            final FSRecord record = records[recordNo];
            recordsUpdateMethod.updateInStorage(record, storage);
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

    final int threadsCount = Runtime.getRuntime().availableProcessors();

    final Thread[] threads = new Thread[threadsCount];
    for (int threadNo = 0; threadNo < threads.length; threadNo++) {
      threads[threadNo] = new Thread(() -> {
        //each thread updates each record (so each record is updated threadCount times):
        for (FSRecord record : records) {
          try {
            recordsUpdateMethod.updateInStorage(record, storage);
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

  @Test
  public void manyRecordsWritten_DoesntOverrideHeaderFields() throws Exception {
    //Assign some storage.header fields and check the assigned values are not changed
    // after many records are inserted into storage:
    final int version = 1;
    storage.setVersion(version);
    final long createdTimestamp = storage.getTimestamp();
    storage.setConnectionStatus(CONNECTED_MAGIC);

    final FSRecord[] records = new FSRecord[maxRecordsToInsert];
    for (int i = 0; i < records.length; i++) {
      final int recordId = storage.allocateRecord();
      records[i] = generateRecordFields(recordId);
      recordsUpdateMethod.updateInStorage(records[i], storage);
    }

    assertEquals(
      "storage.version must keep value assigned initially",
      version,
      storage.getVersion()
    );
    assertEquals(
      "storage.timestamp must not change since initially",
      createdTimestamp,
      storage.getTimestamp()
    );
    assertEquals(
      "storage.connectedStatus must keep value assigned initially",
      CONNECTED_MAGIC,
      storage.getConnectionStatus()
    );
  }


  /* =================== PERSISTENCE: values are kept through close-and-reopen =============================== */

  @Test
  public void emptyStorageRemains_EmptyButHeaderFieldsStillRestored_AfterStorageClosedAndReopened() throws IOException {
    final int version = 10;
    final int connectionStatus = CONNECTED_MAGIC;

    storage.setVersion(version);
    storage.setConnectionStatus(connectionStatus);
    final int globalModCount = storage.getGlobalModCount();
    assertTrue("Storage must be 'dirty' after few header fields were written",
               storage.isDirty());

    final long recordsCountBeforeClose = storage.recordsCount();
    assertEquals("No records were allocated yet",
                 0,
                 recordsCountBeforeClose
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
    assertEquals("recordsCountBeforeClose", recordsCountBeforeClose, storageReopened.recordsCount());
  }

  @Test
  public void singleWrittenRecord_CouldBeReadBackUnchanged_AfterStorageClosedAndReopened() throws Exception {
    final int recordId = storage.allocateRecord();
    final FSRecord recordWritten = generateRecordFields(recordId);
    recordsUpdateMethod.updateInStorage(recordWritten, storage);

    storage.close();
    final T storageReopened = openStorage(storagePath);
    storage = storageReopened;//for tearDown to successfully close it

    assertEquals("Records count should be still 1 after re-open",
                 1,
                 storage.recordsCount()
    );
    final FSRecord recordReadBack = FSRecord.readFromStorage(storage, recordId);
    assertEqualExceptModCount("Record written should be read back as-is", recordWritten, recordReadBack);
  }


  @Test
  public void globalStorageModCount_ShouldNotChange_OnForceAndClose() throws IOException {
    int modCountBefore = storage.getGlobalModCount();
    storage.force();
    assertEquals("globalModCount should not change with .force()",
                 modCountBefore,
                 storage.getGlobalModCount());
    storage.close();
    final T storageReopened = openStorage(storagePath);
    storage = storageReopened;//for tearDown to successfully close it
    assertEquals("globalModCount should not change with .close()",
                 modCountBefore,
                 storage.getGlobalModCount());
  }

  @Test
  public void errorsAccumulated_RestoredAfterReopen() throws IOException {
    int errorsWritten = 42;

    storage.setErrorsAccumulated(errorsWritten);
    storage.close();

    final T storageReopened = openStorage(storagePath);
    storage = storageReopened;//for tearDown to successfully close it
    assertEquals("errorsAccumulated be restored",
                 errorsWritten,
                 storage.getErrorsAccumulated());
  }


  @Test
  public void allocatedRecordId_CouldBeAlwaysWritten_EvenInMultiThreadedEnv() throws Exception {
    //RC: there are EA reports with 'fileId ... outside of allocated range ...' exception
    //    _just after recordId was allocated_. So the test checks there are no concurrency errors
    //    that could leads to that:
    int CPUs = Runtime.getRuntime().availableProcessors();
    int recordsPerThread = maxRecordsToInsert / CPUs;
    ExecutorService pool = Executors.newFixedThreadPool(CPUs);
    try {
      Callable<Object> insertingRecordsTask = () -> {
        for (int i = 0; i < recordsPerThread; i++) {
          int recordId = storage.allocateRecord();
          storage.setParent(recordId, 1);
          storage.updateNameId(recordId, 11);
          storage.setContentRecordId(recordId, 12);
          storage.setAttributeRecordId(recordId, 13);
          storage.setFlags(recordId, PersistentFS.Flags.MUST_RELOAD_LENGTH);
        }
        return null;
      };
      List<Future<Object>> futures = IntStream.range(0, CPUs)
        .mapToObj(i -> insertingRecordsTask)
        .map(pool::submit)
        .toList();
      for (Future<Object> future : futures) {
        future.get();//give a chance to deliver exception
      }
    }
    finally {
      pool.shutdown();
      pool.awaitTermination(15, SECONDS);
    }
  }


  @After
  public void tearDown() throws Exception {
    if (storage != null) {
      storage.close();
    }
  }


  // ========================== INFRASTRUCTURE ===============================================================

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
      rnd.nextInt(0, recordId),
      rnd.nextInt(1, Integer.MAX_VALUE),//nameId should be >0
      rnd.nextInt(),
      rnd.nextInt(0, Integer.MAX_VALUE),//attributeRecordId should be >=0
      rnd.nextInt(0, Integer.MAX_VALUE),
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

  /**
   * Newly implemented storages provide experimental APIs for 'per-record' updates, but default API
   * should also be tested -- hence specific API variant to test is abstracted out, and could be
   * plugged in by subclasses
   */
  public interface UpdateAPIMethod {
    void updateInStorage(FSRecord record,
                         PersistentFSRecordsStorage storage) throws IOException;
  }

  public static final UpdateAPIMethod DEFAULT_API_UPDATE_METHOD = new UpdateAPIMethod() {
    @Override
    public void updateInStorage(FSRecord record, PersistentFSRecordsStorage storage) throws IOException {
      storage.setParent(record.id, record.parentRef);
      storage.updateNameId(record.id, record.nameRef);
      storage.setFlags(record.id, record.flags);
      storage.setAttributeRecordId(record.id, record.attributeRef);
      storage.setContentRecordId(record.id, record.contentRef);
      storage.setTimestamp(record.id, record.timestamp);
      storage.setLength(record.id, record.length);
    }

    @Override
    public String toString() {
      return "DEFAULT_API_UPDATE_METHOD";
    }
  };

  public static final UpdateAPIMethod MODERN_API_UPDATE_METHOD = new UpdateAPIMethod() {
    @Override
    public void updateInStorage(FSRecord record, PersistentFSRecordsStorage storage) throws IOException {
      if (!(storage instanceof IPersistentFSRecordsStorage newStorage)) {
        throw new UnsupportedOperationException(
          "MODERN API update available only for IPersistentFSRecordsStorage, but " + storage + " doesn't implement that interface");
      }
      newStorage.updateRecord(record.id, updatableRecordView -> {
        updatableRecordView.setParent(record.parentRef);
        updatableRecordView.setNameId(record.nameRef);
        updatableRecordView.setFlags(record.flags);
        updatableRecordView.setAttributeRecordId(record.attributeRef);
        updatableRecordView.setContentRecordId(record.contentRef);
        updatableRecordView.setTimestamp(record.timestamp);
        updatableRecordView.setLength(record.length);
        return true;
      });
    }

    @Override
    public String toString() {
      return "MODERN_API_UPDATE_METHOD";
    }
  };
}
