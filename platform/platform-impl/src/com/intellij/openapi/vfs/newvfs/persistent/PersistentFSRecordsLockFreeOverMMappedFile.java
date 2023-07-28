// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage.Page;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.IOUtil;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSHeaders.*;
import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.VFS;
import static com.intellij.util.SystemProperties.getIntProperty;
import static java.lang.invoke.MethodHandles.byteBufferViewVarHandle;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.StandardOpenOption.*;

/**
 * Implementation uses memory-mapped file (real one, not our emulation of it via {@link com.intellij.util.io.FilePageCache}).
 */
@ApiStatus.Internal
public class PersistentFSRecordsLockFreeOverMMappedFile implements PersistentFSRecordsStorage, IPersistentFSRecordsStorage {

  /* ================ FILE HEADER FIELDS LAYOUT ======================================================= */
  /**
   * For mmapped implementation file size is page-aligned, we can't calculate records size from it. Instead
   * we store allocated records count in header, in a reserved field (HEADER_RESERVED_4BYTES_OFFSET)
   */
  private static final int HEADER_RECORDS_ALLOCATED = HEADER_RESERVED_OFFSET_1;

  private static final int HEADER_SIZE = PersistentFSHeaders.HEADER_SIZE;

  /* ================ RECORD FIELDS LAYOUT  =========================================================== */

  private static final int PARENT_REF_OFFSET = 0;
  private static final int PARENT_REF_SIZE = Integer.BYTES;
  private static final int NAME_REF_OFFSET = PARENT_REF_OFFSET + PARENT_REF_SIZE;
  private static final int NAME_REF_SIZE = Integer.BYTES;
  private static final int FLAGS_OFFSET = NAME_REF_OFFSET + NAME_REF_SIZE;
  private static final int FLAGS_SIZE = Integer.BYTES;
  private static final int ATTR_REF_OFFSET = FLAGS_OFFSET + FLAGS_SIZE;
  private static final int ATTR_REF_SIZE = Integer.BYTES;
  private static final int CONTENT_REF_OFFSET = ATTR_REF_OFFSET + ATTR_REF_SIZE;
  private static final int CONTENT_REF_SIZE = Integer.BYTES;
  private static final int MOD_COUNT_OFFSET = CONTENT_REF_OFFSET + CONTENT_REF_SIZE;
  private static final int MOD_COUNT_SIZE = Integer.BYTES;
  //RC: moved timestamp 1 field down so both LONG fields are 8-byte aligned (for atomic accesses alignment is important)
  private static final int TIMESTAMP_OFFSET = MOD_COUNT_OFFSET + MOD_COUNT_SIZE;
  private static final int TIMESTAMP_SIZE = Long.BYTES;
  private static final int LENGTH_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
  private static final int LENGTH_SIZE = Long.BYTES;

  public static final int RECORD_SIZE_IN_BYTES = LENGTH_OFFSET + LENGTH_SIZE;

  /* ================ RECORD FIELDS LAYOUT end             ======================================== */

  public static final int DEFAULT_MAPPED_CHUNK_SIZE = getIntProperty("vfs.records-storage.memory-mapped.mapped-chunk-size", 1 << 26);//64Mb

  private static final VarHandle INT_HANDLE = byteBufferViewVarHandle(int[].class, nativeOrder()).withInvokeExactBehavior();
  private static final VarHandle LONG_HANDLE = byteBufferViewVarHandle(long[].class, nativeOrder()).withInvokeExactBehavior();


  private final @NotNull MMappedFileStorage storage;

  /**
   * How many records were allocated already. Since id=0 is reserved (NULL_ID), we start assigning ids from 1,
   * and hence (last record id == allocatedRecordsCount)
   */
  private final AtomicInteger allocatedRecordsCount = new AtomicInteger(0);

  /**
   * Incremented on each update of anything in the storage -- header, record. Hence be seen as 'version'
   * of storage content -- not storage format version, but current storage content.
   * Stored in {@link PersistentFSHeaders#HEADER_GLOBAL_MOD_COUNT_OFFSET} header field.
   * If a record is updated -> current value of globalModCount is 'stamped' into a record MOD_COUNT field.
   */
  private final AtomicInteger globalModCount = new AtomicInteger(0);
  //MAYBE RC: if we increment .globalModCount on _each_ modification -- this rises interesting possibility to
  //          detect corruptions without corruption marker: currently stored globalModCount (HEADER_GLOBAL_MOD_COUNT_OFFSET)
  //          is a version of last record what is guaranteed to be correctly written. So we could scan all
  //          records on file open, and ensure no one of them has .modCount > .globalModCount read from header.
  //          If this is true -- most likely records were stored correctly, even if app was crushed. If not, if
  //          we find a record(s) with modCount>globalModCount => there were writes unfinished on app crush, and
  //          likely at least those records are corrupted.

  //MAYBE RC: instead of dirty flag -> just compare .globalModCount != getIntHeaderField(HEADER_GLOBAL_MOD_COUNT_OFFSET)
  private final AtomicBoolean dirty = new AtomicBoolean(false);

  //cached for faster access:
  private final transient int pageSize;
  private final transient int recordsPerPage;

  private final transient HeaderAccessor headerAccessor = new HeaderAccessor(this);

  public PersistentFSRecordsLockFreeOverMMappedFile(final @NotNull Path path,
                                                    final int mappedChunkSize) throws IOException {
    this.storage = new MMappedFileStorage(path, mappedChunkSize);

    this.pageSize = mappedChunkSize;
    recordsPerPage = mappedChunkSize / RECORD_SIZE_IN_BYTES;

    final int recordsCountInStorage = getIntHeaderField(HEADER_RECORDS_ALLOCATED);
    final int modCount = getIntHeaderField(HEADER_GLOBAL_MOD_COUNT_OFFSET);

    allocatedRecordsCount.set(recordsCountInStorage);
    globalModCount.set(modCount);
  }

  @Override
  public <R, E extends Throwable> R readRecord(final int recordId,
                                               final @NotNull RecordReader<R, E> reader) throws E, IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      final RecordAccessor recordAccessor = new RecordAccessor(recordId, recordOffsetOnPage, page, allocatedRecordsCount);
      return reader.readRecord(recordAccessor);
    }
  }

  @Override
  public <E extends Throwable> int updateRecord(final int recordId,
                                                final @NotNull RecordUpdater<E> updater) throws E, IOException {
    final int trueRecordId = (recordId <= NULL_ID) ?
                             allocateRecord() :
                             recordId;
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      //RC: hope EscapeAnalysis removes the allocation here:
      final RecordAccessor recordAccessor = new RecordAccessor(recordId, recordOffsetOnPage, page, allocatedRecordsCount);
      final boolean updated = updater.updateRecord(recordAccessor);
      if (updated) {
        incrementRecordVersion(recordAccessor.pageBuffer, recordOffsetOnPage);
      }
      return trueRecordId;
    }
  }

  @Override
  public <R, E extends Throwable> R readHeader(final @NotNull HeaderReader<R, E> reader) throws E, IOException {
    //'acquire' page in advance -- ensure page will not be evicted during .readHeader()
    try (final Page page = storage.pageByOffset(0)) {
      return reader.readHeader(headerAccessor);
    }
  }

  @Override
  public <E extends Throwable> void updateHeader(final @NotNull HeaderUpdater<E> updater) throws E, IOException {
    try (final Page page = storage.pageByOffset(0)) {
      if (updater.updateHeader(headerAccessor)) {
        globalModCount.incrementAndGet();
        dirty.compareAndSet(true, false);
      }
    }
  }


  private static class RecordAccessor implements RecordForUpdate {
    private final int recordId;
    private final int recordOffsetInPage;
    private final transient ByteBuffer pageBuffer;
    private final AtomicInteger allocatedRecordsCount;

    private RecordAccessor(final int recordId,
                           final int recordOffsetInPage,
                           final Page recordPage,
                           final AtomicInteger allocatedRecordsCount) {
      this.recordId = recordId;
      this.recordOffsetInPage = recordOffsetInPage;
      pageBuffer = recordPage.rawPageBuffer();
      this.allocatedRecordsCount = allocatedRecordsCount;
    }

    @Override
    public int recordId() {
      return recordId;
    }

    @Override
    public int getAttributeRecordId() {
      return getIntField(ATTR_REF_OFFSET);
    }

    @Override
    public int getParent() {
      return getIntField(PARENT_REF_OFFSET);
    }

    @Override
    public int getNameId() {
      return getIntField(NAME_REF_OFFSET);
    }

    @Override
    public long getLength() {
      return getLongField(LENGTH_OFFSET);
    }

    @Override
    public long getTimestamp() {
      return getLongField(TIMESTAMP_OFFSET);
    }

    @Override
    public int getModCount() {
      return getIntField(MOD_COUNT_OFFSET);
    }

    @Override
    public int getContentRecordId() {
      return getIntField(CONTENT_REF_OFFSET);
    }

    @Override
    public @PersistentFS.Attributes int getFlags() {
      return getIntField(FLAGS_OFFSET);
    }

    @Override
    public void setAttributeRecordId(final int attributeRecordId) {
      checkValidIdField(recordId, attributeRecordId, "attributeRecordId");
      setIntField(ATTR_REF_OFFSET, attributeRecordId);
    }

    @Override
    public void setParent(final int parentId) {
      checkParentIdIsValid(parentId);
      setIntField(PARENT_REF_OFFSET, parentId);
    }

    @Override
    public void setNameId(final int nameId) {
      checkValidIdField(recordId, nameId, "nameId");
      setIntField(NAME_REF_OFFSET, nameId);
    }

    @Override
    public boolean setFlags(final @PersistentFS.Attributes int flags) {
      return setIntFieldIfChanged(FLAGS_OFFSET, flags);
    }

    @Override
    public boolean setLength(final long length) {
      return setLongFieldIfChanged(LENGTH_OFFSET, length);
    }

    @Override
    public boolean setTimestamp(final long timestamp) {
      return setLongFieldIfChanged(TIMESTAMP_OFFSET, timestamp);
    }

    @Override
    public boolean setContentRecordId(final int contentRecordId) {
      checkValidIdField(recordId, contentRecordId, "contentRecordId");
      return setIntFieldIfChanged(CONTENT_REF_OFFSET, contentRecordId);
    }


    private long getLongField(final int fieldRelativeOffset) {
      return (long)LONG_HANDLE.getVolatile(pageBuffer, recordOffsetInPage + fieldRelativeOffset);
    }

    private boolean setLongFieldIfChanged(final int fieldRelativeOffset,
                                          final long newValue) {
      final int fieldOffsetInPage = recordOffsetInPage + fieldRelativeOffset;
      final long oldValue = (long)LONG_HANDLE.getVolatile(pageBuffer, fieldOffsetInPage);
      if (oldValue != newValue) {
        setLongVolatile(pageBuffer, fieldOffsetInPage, newValue);
        return true;
      }
      return false;
    }

    private int getIntField(final int fieldRelativeOffset) {
      return (int)INT_HANDLE.getVolatile(pageBuffer, recordOffsetInPage + fieldRelativeOffset);
    }

    private void setIntField(final int fieldRelativeOffset,
                             final int newValue) {
      setIntVolatile(pageBuffer, recordOffsetInPage + fieldRelativeOffset, newValue);
    }

    private boolean setIntFieldIfChanged(final int fieldRelativeOffset,
                                         final int newValue) {
      final int fieldOffsetInPage = recordOffsetInPage + fieldRelativeOffset;
      final int oldValue = (int)INT_HANDLE.getVolatile(pageBuffer, fieldOffsetInPage);
      if (oldValue != newValue) {
        setIntVolatile(pageBuffer, fieldOffsetInPage, newValue);
        return true;
      }
      return false;
    }

    private void checkParentIdIsValid(final int parentId) throws IndexOutOfBoundsException {
      final int recordsAllocatedSoFar = allocatedRecordsCount.get();
      if (!(NULL_ID <= parentId && parentId <= recordsAllocatedSoFar)) {
        throw new IndexOutOfBoundsException(
          "parentId(=" + parentId + ") is outside of allocated IDs range [0, " + recordsAllocatedSoFar + "]");
      }
    }
  }

  private static class HeaderAccessor implements HeaderForUpdate {
    private final @NotNull PersistentFSRecordsLockFreeOverMMappedFile records;

    private HeaderAccessor(final @NotNull PersistentFSRecordsLockFreeOverMMappedFile records) { this.records = records; }

    @Override
    public long getTimestamp() throws IOException {
      return records.getTimestamp();
    }

    @Override
    public int getConnectionStatus() throws IOException {
      return records.getConnectionStatus();
    }

    @Override
    public int getVersion() throws IOException {
      return records.getVersion();
    }

    @Override
    public int getGlobalModCount() {
      return records.getGlobalModCount();
    }

    @Override
    public void setConnectionStatus(final int code) throws IOException {
      records.setConnectionStatus(code);
    }

    @Override
    public void setVersion(final int version) throws IOException {
      records.setVersion(version);
    }
  }


  // ==== records operations:  ================================================================ //


  @Override
  public int allocateRecord() {
    dirty.compareAndSet(false, true);
    return allocatedRecordsCount.incrementAndGet();
  }

  // 'one field at a time' operations

  @Override
  public void setAttributeRecordId(final int recordId,
                                   final int attributeRecordId) throws IOException {
    checkValidIdField(recordId, attributeRecordId, "attributeRecordId");
    setIntField(recordId, ATTR_REF_OFFSET, attributeRecordId);
  }

  @Override
  public int getAttributeRecordId(final int recordId) throws IOException {
    return getIntField(recordId, ATTR_REF_OFFSET);
  }

  @Override
  public int getParent(final int recordId) throws IOException {
    return getIntField(recordId, PARENT_REF_OFFSET);
  }

  @Override
  public void setParent(final int recordId,
                        final int parentId) throws IOException {
    checkParentIdIsValid(parentId);
    setIntField(recordId, PARENT_REF_OFFSET, parentId);
  }

  @Override
  public int getNameId(final int recordId) throws IOException {
    return getIntField(recordId, NAME_REF_OFFSET);
  }

  @Override
  public void setNameId(final int recordId,
                        final int nameId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(nameId);
    setIntField(recordId, NAME_REF_OFFSET, nameId);
  }

  @Override
  public boolean setFlags(final int recordId,
                          final @PersistentFS.Attributes int newFlags) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      final ByteBuffer pageBuffer = page.rawPageBuffer();

      return setIntFieldIfChanged(pageBuffer, recordOffsetOnPage, FLAGS_OFFSET, newFlags);
    }
  }

  @Override
  public @PersistentFS.Attributes int getFlags(final int recordId) throws IOException {
    //noinspection MagicConstant
    return getIntField(recordId, FLAGS_OFFSET);
  }

  @Override
  public long getLength(final int recordId) throws IOException {
    return getLongField(recordId, LENGTH_OFFSET);
  }

  @Override
  public boolean setLength(final int recordId,
                           final long newLength) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    final int fieldOffsetOnPage = recordOffsetOnPage + LENGTH_OFFSET;
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      final ByteBuffer pageBuffer = page.rawPageBuffer();
      final long storedLength = (long)LONG_HANDLE.getVolatile(pageBuffer, fieldOffsetOnPage);
      if (storedLength != newLength) {
        setLongVolatile(pageBuffer, fieldOffsetOnPage, newLength);
        incrementRecordVersion(pageBuffer, recordOffsetOnPage);

        return true;
      }
      return false;
    }
  }


  @Override
  public long getTimestamp(final int recordId) throws IOException {
    return getLongField(recordId, TIMESTAMP_OFFSET);
  }

  @Override
  public boolean setTimestamp(final int recordId,
                              final long newTimestamp) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      final ByteBuffer pageBuffer = page.rawPageBuffer();

      return setLongFieldIfChanged(pageBuffer, recordOffsetOnPage, TIMESTAMP_OFFSET, newTimestamp);
    }
  }

  @Override
  public int getModCount(final int recordId) throws IOException {
    return getIntField(recordId, MOD_COUNT_OFFSET);
  }

  @Override
  public int getContentRecordId(final int recordId) throws IOException {
    return getIntField(recordId, CONTENT_REF_OFFSET);
  }

  @Override
  public boolean setContentRecordId(final int recordId,
                                    final int newContentRecordId) throws IOException {
    checkValidIdField(recordId, newContentRecordId, "contentRecordId");
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      final ByteBuffer pageBuffer = page.rawPageBuffer();
      return setIntFieldIfChanged(pageBuffer, recordOffsetOnPage, CONTENT_REF_OFFSET, newContentRecordId);
    }
  }

  @Override
  public void fillRecord(final int recordId,
                         final long timestamp,
                         final long length,
                         final int flags,
                         final int nameId,
                         final int parentId,
                         final boolean overwriteAttrRef) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      final ByteBuffer pageBuffer = page.rawPageBuffer();
      setIntVolatile(pageBuffer, recordOffsetOnPage + PARENT_REF_OFFSET, parentId);
      setIntVolatile(pageBuffer, recordOffsetOnPage + NAME_REF_OFFSET, nameId);
      setIntVolatile(pageBuffer, recordOffsetOnPage + FLAGS_OFFSET, flags);
      if (overwriteAttrRef) {
        setIntVolatile(pageBuffer, recordOffsetOnPage + ATTR_REF_OFFSET, 0);
      }
      setLongVolatile(pageBuffer, recordOffsetOnPage + TIMESTAMP_OFFSET, timestamp);
      setLongVolatile(pageBuffer, recordOffsetOnPage + LENGTH_OFFSET, length);

      incrementRecordVersion(pageBuffer, recordOffsetOnPage);
    }
  }

  @Override
  public void markRecordAsModified(final int recordId) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      incrementRecordVersion(page.rawPageBuffer(), recordOffsetOnPage);
    }
  }

  @Override
  public void cleanRecord(final int recordId) throws IOException {
    checkRecordIdIsValid(recordId);

    //fill record with zeroes, by 4 bytes at once:
    assert RECORD_SIZE_IN_BYTES % Integer.BYTES == 0 : "RECORD_SIZE_IN_BYTES(=" + RECORD_SIZE_IN_BYTES + ") is expected to be 32-aligned";
    final int recordSizeInInts = RECORD_SIZE_IN_BYTES / Integer.BYTES;

    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      final ByteBuffer pageBuffer = page.rawPageBuffer();
      for (int wordNo = 0; wordNo < recordSizeInInts; wordNo++) {
        final int offsetOfWord = recordOffsetOnPage + wordNo * Integer.BYTES;
        setIntVolatile(pageBuffer, offsetOfWord, 0);
      }
    }
  }


  @Override
  public boolean processAllRecords(final @NotNull PersistentFSRecordsStorage.FsRecordProcessor processor) throws IOException {
    final int recordsCount = maxAllocatedID();
    for (int recordId = MIN_VALID_ID; recordId <= recordsCount; recordId++) {
      processor.process(
        recordId,
        getNameId(recordId),
        getFlags(recordId),
        getParent(recordId),
        getAttributeRecordId(recordId),
        getContentRecordId(recordId),
        /* corrupted = */ false
      );
    }
    return true;
  }


  // ============== storage 'global' properties accessors: ============================= //

  @Override
  public long getTimestamp() throws IOException {
    return getLongHeaderField(HEADER_TIMESTAMP_OFFSET);
  }

  @Override
  public void setConnectionStatus(final int connectionStatus) throws IOException {
    setIntHeaderField(HEADER_CONNECTION_STATUS_OFFSET, connectionStatus);
    dirty.compareAndSet(false, true);
  }

  @Override
  public int getConnectionStatus() throws IOException {
    return getIntHeaderField(HEADER_CONNECTION_STATUS_OFFSET);
  }

  @Override
  public int getErrorsAccumulated() throws IOException {
    return getIntHeaderField(HEADER_ERRORS_ACCUMULATED_OFFSET);
  }

  @Override
  public void setErrorsAccumulated(int errors) throws IOException {
    setIntHeaderField(HEADER_ERRORS_ACCUMULATED_OFFSET, errors);
    globalModCount.incrementAndGet();
    dirty.compareAndSet(false, true);
  }

  @Override
  public void setVersion(final int version) throws IOException {
    setIntHeaderField(HEADER_VERSION_OFFSET, version);
    setLongHeaderField(HEADER_TIMESTAMP_OFFSET, System.currentTimeMillis());
    globalModCount.incrementAndGet();
    dirty.compareAndSet(false, true);
  }

  @Override
  public int getVersion() throws IOException {
    return getIntHeaderField(HEADER_VERSION_OFFSET);
  }

  @Override
  public int getGlobalModCount() {
    return globalModCount.get();
  }


  public long actualDataLength() {
    final int recordsCount = allocatedRecordsCount.get() + 1;
    return recordOffsetInFileUnchecked(recordsCount);
  }

  @Override
  public int recordsCount() {
    return allocatedRecordsCount.get();
  }

  @Override
  public int maxAllocatedID() {
    return allocatedRecordsCount.get();
  }

  @Override
  public boolean isDirty() {
    return dirty.get();
  }

  @Override
  public void force() throws IOException {
    if (dirty.compareAndSet(true, false)) {
      setIntHeaderField(HEADER_RECORDS_ALLOCATED, allocatedRecordsCount.get());
      setIntHeaderField(HEADER_GLOBAL_MOD_COUNT_OFFSET, globalModCount.get());
      //MAYBE RC: should we do fsync() here, or we could trust OS will flush mmapped pages to disk?
      storage.fsync();
    }
  }

  @Override
  public void close() throws IOException {
    force();
    storage.close();
  }

  /** Close the storage and remove all its data files */
  @Override
  public void closeAndRemoveAllFiles() throws IOException {
    close();
    if (SystemInfoRt.isWindows) {
      //On Win there are a lot of issues with removing file that was mmapped.
      // Let's give mapped buffers at least a chance to be collected & unmapped -- not a guarantee from that kind of
      // issues, but a small step in the right direction

      //noinspection CallToSystemGC
      System.gc();
    }
    FileUtil.delete(storage.storagePath());
  }

  //TODO RC: do we need method like 'unmap', which forcibly unmaps pages, or it is enough to rely
  //         on JVM which will unmap pages eventually, as they are collected by GC?
  //         Forcible unmap allows to 'clean after yourself', but carries a risk of JVM crash if
  //         somebody still tries to access unmapped pages.
  //         Seems like the best solution would be to provide 'unmap' as dedicated method, not
  //         as a part of .close() -- so it could be used in e.g. tests, and in cases there we
  //         could 100% guarantee no usages anymore. But in regular use VFS exists for
  //         the whole life of app, hence better to let JVM unmap pages on shutdown, and not
  //         carry the risk of JVM crush after too eager unmapping.

  // =============== implementation: addressing ========================================================= //

  /** Without recordId bounds checking */
  @VisibleForTesting
  protected long recordOffsetInFileUnchecked(final int recordId) {
    //recordId is 1-based, convert to 0-based recordNo:
    final int recordNo = recordId - 1;

    final int recordsOnHeaderPage = (pageSize - HEADER_SIZE) / RECORD_SIZE_IN_BYTES;
    if (recordNo < recordsOnHeaderPage) {
      return HEADER_SIZE + recordNo * (long)RECORD_SIZE_IN_BYTES;
    }

    //as-if there were no header:
    final int fullPages = recordNo / recordsPerPage;
    final int recordsOnLastPage = recordNo % recordsPerPage;

    //header on the first page "push out" few records:
    final int recordsExcessBecauseOfHeader = recordsPerPage - recordsOnHeaderPage;

    //so the last page could turn into +1 page:
    final int recordsReallyOnLastPage = recordsOnLastPage + recordsExcessBecauseOfHeader;
    return (long)(fullPages + recordsReallyOnLastPage / recordsPerPage) * pageSize
           + (long)(recordsReallyOnLastPage % recordsPerPage) * RECORD_SIZE_IN_BYTES;
  }

  private long recordOffsetInFile(final int recordId) throws IndexOutOfBoundsException {
    checkRecordIdIsValid(recordId);
    return recordOffsetInFileUnchecked(recordId);
  }


  private void checkRecordIdIsValid(final int recordId) throws IndexOutOfBoundsException {
    final int recordsAllocatedSoFar = allocatedRecordsCount.get();
    if (!(NULL_ID < recordId && recordId <= recordsAllocatedSoFar)) {
      throw new IndexOutOfBoundsException(
        "recordId(=" + recordId + ") is outside of allocated IDs range (0, " + recordsAllocatedSoFar + "]");
    }
  }

  private void checkParentIdIsValid(final int parentId) throws IndexOutOfBoundsException {
    final int recordsAllocatedSoFar = allocatedRecordsCount.get();
    if (!(NULL_ID <= parentId && parentId <= recordsAllocatedSoFar)) {
      throw new IndexOutOfBoundsException(
        "parentId(=" + parentId + ") is outside of allocated IDs range [0, " + recordsAllocatedSoFar + "]");
    }
  }

  private static void checkValidIdField(int recordId,
                                        int idFieldValue,
                                        @NotNull String fieldName) {
    if (idFieldValue < NULL_ID) {
      throw new IllegalArgumentException("file[id: " + recordId + "]." + fieldName + "(=" + idFieldValue + ") must be >=0");
    }
  }

  // =============== implementation: record field access ================================================ //

  //each access method acquires a page

  private void setLongField(final int recordId,
                            final int fieldRelativeOffset,
                            final long fieldValue) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      final ByteBuffer pageBuffer = page.rawPageBuffer();
      setLongVolatile(pageBuffer, recordOffsetOnPage + fieldRelativeOffset, fieldValue);
      incrementRecordVersion(pageBuffer, recordOffsetOnPage);
    }
  }

  private long getLongField(final int recordId,
                            final int fieldRelativeOffset) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      final ByteBuffer pageBuffer = page.rawPageBuffer();
      return (long)LONG_HANDLE.getVolatile(pageBuffer, recordOffsetOnPage + fieldRelativeOffset);
    }
  }

  private boolean setLongFieldIfChanged(final ByteBuffer pageBuffer,
                                        final int recordOffsetOnPage,
                                        final int fieldRelativeOffset,
                                        final long newValue) {
    final int fieldOffsetOnPage = recordOffsetOnPage + fieldRelativeOffset;
    final long oldValue = (long)LONG_HANDLE.getVolatile(pageBuffer, fieldOffsetOnPage);
    if (oldValue != newValue) {
      setLongVolatile(pageBuffer, fieldOffsetOnPage, newValue);
      incrementRecordVersion(pageBuffer, recordOffsetOnPage);
      return true;
    }
    return false;
  }


  private void setIntField(final int recordId,
                           final int fieldRelativeOffset,
                           final int fieldValue) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      final ByteBuffer pageBuffer = page.rawPageBuffer();
      setIntVolatile(pageBuffer, recordOffsetOnPage + fieldRelativeOffset, fieldValue);
      incrementRecordVersion(pageBuffer, recordOffsetOnPage);
    }
  }

  private int getIntField(final int recordId,
                          final int fieldRelativeOffset) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final Page page = storage.pageByOffset(recordOffsetInFile)) {
      final ByteBuffer pageBuffer = page.rawPageBuffer();
      return (int)INT_HANDLE.getVolatile(pageBuffer, recordOffsetOnPage + fieldRelativeOffset);
    }
  }

  private boolean setIntFieldIfChanged(final ByteBuffer pageBuffer,
                                       final int recordOffsetOnPage,
                                       final int fieldRelativeOffset,
                                       final int newValue) {
    final int fieldOffsetOnPage = recordOffsetOnPage + fieldRelativeOffset;
    final int oldValue = (int)INT_HANDLE.getVolatile(pageBuffer, fieldOffsetOnPage);
    if (oldValue != newValue) {
      setIntVolatile(pageBuffer, fieldOffsetOnPage, newValue);
      incrementRecordVersion(pageBuffer, recordOffsetOnPage);

      return true;
    }
    return false;
  }


  private void incrementRecordVersion(final @NotNull ByteBuffer pageBuffer,
                                      final int recordOffsetOnPage) {
    setIntVolatile(pageBuffer, recordOffsetOnPage + MOD_COUNT_OFFSET, globalModCount.incrementAndGet());
    dirty.compareAndSet(false, true);
  }

  //============ header fields access: ============================================================ //

  private void setLongHeaderField(final @HeaderOffset int headerRelativeOffsetBytes,
                                  final long headerValue) throws IOException {
    checkHeaderOffset(headerRelativeOffsetBytes);
    try (final Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      setLongVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes, headerValue);
    }
  }

  private long getLongHeaderField(final @HeaderOffset int headerRelativeOffsetBytes) throws IOException {
    checkHeaderOffset(headerRelativeOffsetBytes);
    try (final Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      return (long)LONG_HANDLE.getVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes);
    }
  }

  private void setIntHeaderField(final @HeaderOffset int headerRelativeOffsetBytes,
                                 final int headerValue) throws IOException {
    checkHeaderOffset(headerRelativeOffsetBytes);
    try (final Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      setIntVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes, headerValue);
    }
  }


  private int getIntHeaderField(final @HeaderOffset int headerRelativeOffsetBytes) throws IOException {
    checkHeaderOffset(headerRelativeOffsetBytes);
    try (final Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      return (int)INT_HANDLE.getVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes);
    }
  }

  private static void checkHeaderOffset(final int headerRelativeOffset) {
    if (!(0 <= headerRelativeOffset && headerRelativeOffset < HEADER_SIZE)) {
      throw new IndexOutOfBoundsException(
        "headerFieldOffset(=" + headerRelativeOffset + ") is outside of header [0, " + HEADER_SIZE + ") ");
    }
  }

  private static void setIntVolatile(final ByteBuffer pageBuffer,
                                     final int offsetInBuffer,
                                     final int value) {
    INT_HANDLE.setVolatile(pageBuffer, offsetInBuffer, value);
  }

  private static void setLongVolatile(final ByteBuffer pageBuffer,
                                      final int offsetInBuffer,
                                      final long value) {
    LONG_HANDLE.setVolatile(pageBuffer, offsetInBuffer, value);
  }

  @ApiStatus.Internal
  public static class MMappedFileStorage implements Closeable {
    private static final Logger LOG = Logger.getInstance(MMappedFileStorage.class);

    //Keep track of mapped buffers allocated & their total size, numbers are reported to OTel.Metrics.
    //Why: mapped buffers are limited resources (~4096 per app by default), so it is worth to monitor
    //     how we use them, and issue the alarm early on as we start to use too many
    private static final AtomicInteger storages = new AtomicInteger();
    private static final AtomicInteger totalPagesMapped = new AtomicInteger();
    private static final AtomicLong totalBytesMapped = new AtomicLong();

    /** Log warn if > PAGES_TO_WARN_THRESHOLD pages were mapped */
    private static final int PAGES_TO_WARN_THRESHOLD = getIntProperty("vfs.memory-mapped-storage.pages-to-warn-threshold", 256);

    static {
      Meter meter = TelemetryManager.getInstance().getMeter(VFS);
      ObservableLongMeasurement storagesCounter = meter.gaugeBuilder("MappedFileStorage.storages")
        .setDescription("MappedFileStorage instances in operation at the moment")
        .ofLongs()
        .buildObserver();
      ObservableLongMeasurement pagesCounter = meter.gaugeBuilder("MappedFileStorage.totalPagesMapped")
        .setDescription("MappedFileStorage.Page instances in operation at the moment")
        .ofLongs()
        .buildObserver();
      ObservableLongMeasurement pagesBytesCounter = meter.gaugeBuilder("MappedFileStorage.totalBytesMapped")
        .setDescription("Total size of MappedByteBuffers in use by MappedFileStorage at the moment")
        .setUnit("bytes")
        .ofLongs()
        .buildObserver();
      meter.batchCallback(
        () -> {
          storagesCounter.record(storages.get());
          pagesCounter.record(totalPagesMapped.get());
          pagesBytesCounter.record(totalBytesMapped.get());
        },
        storagesCounter, pagesCounter, pagesBytesCounter
      );
    }

    private final Path storagePath;

    private final int pageSize;
    private final int pageSizeMask;
    private final int pageSizeBits;

    private final FileChannel channel;

    private final AtomicReferenceArray<Page> pages;

    private final long maxFileSize;

    public MMappedFileStorage(final Path path,
                              final int pageSize) throws IOException {
      //TODO RC: maybe instead of setting maxSize -- make .pages re-allocable?
      //         It is very rare to have all 2^32 records in VFS
      this(path, pageSize, RECORD_SIZE_IN_BYTES * (long)Integer.MAX_VALUE);
    }

    public MMappedFileStorage(final Path path,
                              final int pageSize,
                              final long maxFileSize) throws IOException {
      if (pageSize <= 0) {
        throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be >0");
      }
      if (maxFileSize <= 0) {
        throw new IllegalArgumentException("maxFileSize(=" + maxFileSize + ") must be >0");
      }
      if (Integer.bitCount(pageSize) != 1) {
        throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be a power of 2");
      }


      pageSizeBits = Integer.numberOfTrailingZeros(pageSize);
      pageSizeMask = pageSize - 1;
      this.pageSize = pageSize;
      this.maxFileSize = maxFileSize;

      this.storagePath = path;

      final int maxPagesCount = Math.toIntExact(maxFileSize / pageSize);

      final long length = Files.exists(path) ? Files.size(path) : 0;

      final int pagesToMapExistingFileContent = (int)((length % pageSize == 0) ?
                                                      (length / pageSize) :
                                                      ((length / pageSize) + 1));
      if (pagesToMapExistingFileContent > maxPagesCount) {
        throw new IllegalStateException(
          "Storage size(" + length + " b) > maxFileSize(" + maxFileSize + " b): " +
          "file [" + path + "] is corrupted?");
      }

      //allocate array(1200+): not so much to worry about
      pages = new AtomicReferenceArray<>(maxPagesCount);

      channel = FileChannel.open(storagePath, READ, WRITE, CREATE);

      //map already existing file content right away:
      for (int i = 0; i < pagesToMapExistingFileContent; i++) {
        Page page = new Page(i, channel, pageSize);
        pages.set(i, page);

        totalPagesMapped.incrementAndGet();
        totalBytesMapped.addAndGet(pageSize);

        long pagesMapped = totalPagesMapped.get();
        if (pagesMapped > PAGES_TO_WARN_THRESHOLD) {
          LOG.warn(pagesMapped + " pages were mapped -- too many, > " + PAGES_TO_WARN_THRESHOLD + " threshold. " +
                   "Total mapped size: " + totalBytesMapped.get() + " bytes, storages: " + storages.get());
        }
      }

      storages.incrementAndGet();
    }


    public @NotNull Page pageByOffset(final long offsetInFile) throws IOException {
      final int pageIndex = pageIndexByOffset(offsetInFile);

      Page page = pages.get(pageIndex);
      if (page == null) {
        synchronized (pages) {
          if (!channel.isOpen()) {
            throw new ClosedStorageException("Storage already closed");
          }
          page = pages.get(pageIndex);
          if (page == null) {
            page = new Page(pageIndex, channel, pageSize);
            pages.set(pageIndex, page);

            totalPagesMapped.incrementAndGet();
            totalBytesMapped.addAndGet(pageSize);
          }
        }
      }
      return page;
    }

    public int pageIndexByOffset(final long offsetInFile) {
      if (offsetInFile < 0) {
        throw new IllegalArgumentException("offsetInFile(=" + offsetInFile + ") must be >=0");
      }
      return (int)(offsetInFile >> pageSizeBits);
    }

    public int toOffsetInPage(final long offsetInFile) {
      return (int)(offsetInFile & pageSizeMask);
    }

    @Override
    public void close() throws IOException {
      synchronized (pages) {
        if (channel.isOpen()) {
          channel.close();
          for (int i = 0; i < pages.length(); i++) {
            final Page page = pages.get(i);
            if (page != null) {
              page.close();
              //actual buffer releasing happens later, by GC, but at least we don't keep it
              totalPagesMapped.decrementAndGet();
              totalBytesMapped.addAndGet(-pageSize);
            }
            pages.set(i, null);//give GC a chance to unmap buffers
          }
          storages.decrementAndGet();
        }
      }
    }

    public void fsync() throws IOException {
      if (channel.isOpen()) {
        channel.force(true);
      }
    }

    public Path storagePath() {
      return storagePath;
    }

    public int pageSize() {
      return pageSize;
    }

    public boolean isOpen() {
      return channel.isOpen();
    }

    @Override
    public String toString() {
      return "MMappedFileStorage[" + storagePath + "]" +
             "[pageSize: " + pageSize +
             ", maxFileSize: " + maxFileSize +
             ", pages: " + pages.length() +
             ']';
    }

    public static class Page implements AutoCloseable {
      private final int pageIndex;
      private final long offsetInFile;
      private final ByteBuffer pageBuffer;

      private Page(int pageIndex,
                   FileChannel channel,
                   int pageSize) throws IOException {
        this.pageIndex = pageIndex;
        this.offsetInFile = pageIndex * (long)pageSize;
        this.pageBuffer = map(channel, pageSize);
        pageBuffer.order(nativeOrder());
      }

      private MappedByteBuffer map(FileChannel channel,
                                   int pageSize) throws IOException {
        //TODO RC: this could cause noticeable pauses, hence it may worth to enlarge file in advance, async
        IOUtil.allocateFileRegion(channel, offsetInFile, pageSize);
        return channel.map(READ_WRITE, offsetInFile, pageSize);
      }

      @Override
      public void close() {
        //nothing so far, reserved for future
      }

      public ByteBuffer rawPageBuffer() {
        return pageBuffer;
      }

      @Override
      public String toString() {
        return "Page[#" + pageIndex + "][offset: " + offsetInFile + ", length: " + pageBuffer.capacity() + " b)";
      }
    }
  }
}
