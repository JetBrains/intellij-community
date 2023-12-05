// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;
import com.intellij.util.io.pagecache.Page;
import com.intellij.util.io.pagecache.PageUnsafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSHeaders.*;

/**
 * Implementation uses new {@link PagedFileStorageWithRWLockedPageContent}
 */
@ApiStatus.Internal
public final class PersistentFSRecordsOverLockFreePagedStorage implements PersistentFSRecordsStorage, IPersistentFSRecordsStorage {


  /* ================ FILE HEADER FIELDS LAYOUT ======================================================= */

  public static final int HEADER_SIZE = PersistentFSHeaders.HEADER_SIZE;

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

  private final @NotNull PagedFileStorageWithRWLockedPageContent storage;

  /** How many records were allocated already. allocatedRecordsCount-1 == last record id */
  private final AtomicInteger allocatedRecordsCount = new AtomicInteger(0);

  /**
   * Incremented on each update of anything in the storage -- header, record. Hence be seen as 'version'
   * of storage content -- not storage format version, but current storage content.
   * Stored in {@link PersistentFSHeaders#HEADER_GLOBAL_MOD_COUNT_OFFSET} header field.
   * If a record is updated -> current value of globalModCount is 'stamped' into a record MOD_COUNT field.
   */
  private final AtomicInteger globalModCount = new AtomicInteger(0);

  //cached for faster access:
  private final transient int pageSize;
  private final transient int recordsPerPage;

  private final transient HeaderAccessor headerAccessor = new HeaderAccessor(this);


  public PersistentFSRecordsOverLockFreePagedStorage(final @NotNull PagedFileStorageWithRWLockedPageContent storage) throws IOException {
    this.storage = storage;

    pageSize = storage.getPageSize();
    recordsPerPage = pageSize / RECORD_SIZE_IN_BYTES;

    final int recordsCountInStorage = loadRecordsCount(storage.length());
    allocatedRecordsCount.set(recordsCountInStorage);

    globalModCount.set(getIntHeaderField(HEADER_GLOBAL_MOD_COUNT_OFFSET));
  }

  @VisibleForTesting
  int loadRecordsCount(final long storageSizeBytes) throws IOException {
    if (storageSizeBytes == 0) {
      return 0;
    }
    final long fullPagesOccupied = storageSizeBytes / pageSize;
    if (fullPagesOccupied == 0) {
      final long fullRecordsOnHeaderPage = (storageSizeBytes - HEADER_SIZE) / RECORD_SIZE_IN_BYTES;
      final long recordExcess = (storageSizeBytes - HEADER_SIZE) % RECORD_SIZE_IN_BYTES;
      if (recordExcess > 0) {
        throw new IOException(
          this.storage + " is corrupted: " +
          "(" + storageSizeBytes + "b on a header page) = (" + fullRecordsOnHeaderPage + " whole records)" +
          " + (" + recordExcess + "b excess)" +
          " -> storage is likely truncated, or was created with pageSize != current(" + pageSize + "b)"
        );
      }

      return (int)fullRecordsOnHeaderPage;
    }
    else {
      final long lastPageOccupiedBytes = storageSizeBytes % pageSize;

      final long fullRecordsOnLastPage = lastPageOccupiedBytes / RECORD_SIZE_IN_BYTES;
      final long recordExcess = lastPageOccupiedBytes % RECORD_SIZE_IN_BYTES;
      if (recordExcess > 0) {
        throw new IOException(
          this.storage + " is corrupted: " +
          "(" + lastPageOccupiedBytes + "b on last page) = (" + fullRecordsOnLastPage + " whole records)" +
          " + (" + recordExcess + "b excess)" +
          " -> storage is likely truncated, or was created with pageSize != current(" + pageSize + "b)"
        );
      }

      return (int)(
        (pageSize - HEADER_SIZE) / RECORD_SIZE_IN_BYTES //header page has fewer records!
        + (fullPagesOccupied - 1) * recordsPerPage
        + fullRecordsOnLastPage);
    }
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
  public <R, E extends Throwable> R readRecord(final int recordId,
                                               final @NotNull RecordReader<R, E> reader) throws E, IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */false)) {
      page.lockPageForRead();
      try {
        final RecordAccessor recordAccessor = new RecordAccessor(recordId, recordOffsetOnPage, page, allocatedRecordsCount);
        return reader.readRecord(recordAccessor);
      }
      finally {
        page.unlockPageForRead();
      }
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
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */true)) {
      page.lockPageForWrite();
      try {
        //RC: hope EscapeAnalysis removes the allocation here:
        final RecordAccessor recordAccessor = new RecordAccessor(recordId, recordOffsetOnPage, page, allocatedRecordsCount);
        final boolean updated = updater.updateRecord(recordAccessor);
        if (updated) {
          incrementRecordVersion(recordAccessor.pageBuffer, recordOffsetOnPage);
          page.regionModified(recordOffsetOnPage, RECORD_SIZE_IN_BYTES);
        }
        return trueRecordId;
      }
      finally {
        page.unlockPageForWrite();
      }
    }
  }

  @Override
  public <R, E extends Throwable> R readHeader(final @NotNull HeaderReader<R, E> reader) throws E, IOException {
    try (final Page page = storage.pageByOffset(0, /*forWrite: */false)) {
      page.lockPageForRead();
      try {
        return reader.readHeader(headerAccessor);
      }
      finally {
        page.unlockPageForRead();
      }
    }
  }

  @Override
  public <E extends Throwable> void updateHeader(final @NotNull HeaderUpdater<E> updater) throws E, IOException {
    try (final Page page = storage.pageByOffset(0, /*forWrite: */true)) {
      page.lockPageForWrite();
      try {
        if (updater.updateHeader(headerAccessor)) {
          globalModCount.incrementAndGet();
        }
      }
      finally {
        page.unlockPageForWrite();
      }
    }
  }


  private static final class RecordAccessor implements RecordForUpdate {
    private final int recordId;
    private final int recordOffsetInPage;
    private final Page recordPage;
    private final transient ByteBuffer pageBuffer;
    private final AtomicInteger allocatedRecordsCount;

    private RecordAccessor(int recordId,
                           int recordOffsetInPage,
                           PageUnsafe recordPage,
                           AtomicInteger allocatedRecordsCount) {
      this.recordId = recordId;
      this.recordOffsetInPage = recordOffsetInPage;
      this.recordPage = recordPage;
      pageBuffer = recordPage.rawPageBuffer();
      this.allocatedRecordsCount = allocatedRecordsCount;
    }

    @Override
    public int recordId() {
      return recordId;
    }

    @Override
    public int getAttributeRecordId() {
      return pageBuffer.getInt(recordOffsetInPage + ATTR_REF_OFFSET);
    }

    @Override
    public int getParent() {
      return pageBuffer.getInt(recordOffsetInPage + PARENT_REF_OFFSET);
    }

    @Override
    public int getNameId() {
      return pageBuffer.getInt(recordOffsetInPage + NAME_REF_OFFSET);
    }

    @Override
    public long getLength() {
      return pageBuffer.getLong(recordOffsetInPage + LENGTH_OFFSET);
    }

    @Override
    public long getTimestamp() {
      return pageBuffer.getLong(recordOffsetInPage + TIMESTAMP_OFFSET);
    }

    @Override
    public int getModCount() {
      return pageBuffer.getInt(recordOffsetInPage + MOD_COUNT_OFFSET);
    }

    @Override
    public int getContentRecordId() {
      return pageBuffer.getInt(recordOffsetInPage + CONTENT_REF_OFFSET);
    }

    @Override
    public @PersistentFS.Attributes int getFlags() {
      return pageBuffer.getInt(recordOffsetInPage + FLAGS_OFFSET);
    }

    @Override
    public void setAttributeRecordId(final int attributeRecordId) {
      checkValidIdField(recordId, attributeRecordId, "attributeRecordId");
      pageBuffer.putInt(recordOffsetInPage + ATTR_REF_OFFSET, attributeRecordId);
    }

    @Override
    public void setParent(final int parentId) {
      checkParentIdIsValid(parentId);
      pageBuffer.putInt(recordOffsetInPage + PARENT_REF_OFFSET, parentId);
    }

    @Override
    public void setNameId(final int nameId) {
      pageBuffer.putInt(recordOffsetInPage + NAME_REF_OFFSET, nameId);
    }

    @Override
    public boolean setFlags(final @PersistentFS.Attributes int flags) {
      final int fieldOffsetInPage = recordOffsetInPage + FLAGS_OFFSET;
      if (pageBuffer.getInt(fieldOffsetInPage) != flags) {
        pageBuffer.putInt(fieldOffsetInPage, flags);
        return true;
      }
      return false;
    }

    @Override
    public boolean setLength(final long length) {
      final int fieldOffsetInPage = recordOffsetInPage + LENGTH_OFFSET;
      if (pageBuffer.getLong(fieldOffsetInPage) != length) {
        pageBuffer.putLong(fieldOffsetInPage, length);
        return true;
      }
      return false;
    }

    @Override
    public boolean setTimestamp(final long timestamp) {
      final int fieldOffsetInPage = recordOffsetInPage + TIMESTAMP_OFFSET;
      if (pageBuffer.getLong(fieldOffsetInPage) != timestamp) {
        pageBuffer.putLong(fieldOffsetInPage, timestamp);
        return true;
      }
      return false;
    }

    @Override
    public boolean setContentRecordId(final int contentRecordId) {
      checkValidIdField(recordId, contentRecordId, "contentRecordId");
      final int fieldOffsetInPage = recordOffsetInPage + CONTENT_REF_OFFSET;
      if (pageBuffer.getInt(fieldOffsetInPage) != contentRecordId) {
        pageBuffer.putInt(fieldOffsetInPage, contentRecordId);
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

  private static final class HeaderAccessor implements HeaderForUpdate {
    private final @NotNull PersistentFSRecordsOverLockFreePagedStorage records;

    private HeaderAccessor(final @NotNull PersistentFSRecordsOverLockFreePagedStorage records) { this.records = records; }

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
    int recordId = allocatedRecordsCount.incrementAndGet();
    try {
      //Issue a dummy write (0 is default value of unallocated file regions) to ensure storage file
      // is extended to fit new record. We calculate allocated records via file size on load, hence
      // file size must extend to include new record, otherwise it will be lost
      setIntField(recordId, RECORD_SIZE_IN_BYTES - Integer.BYTES, 0);
    }
    catch (IOException e) {
      throw new UncheckedIOException("Can't ensure room for recordId=" + recordId, e);
    }
    return recordId;
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
  public int setNameId(final int recordId,
                       final int nameId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(nameId);
    return setIntField(recordId, NAME_REF_OFFSET, nameId);
  }

  @Override
  public boolean setFlags(final int recordId,
                          final @PersistentFS.Attributes int newFlags) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.lockPageForWrite();
      try {
        final ByteBuffer pageBuffer = page.rawPageBuffer();
        final int storedFlags = pageBuffer.getInt(recordOffsetOnPage + FLAGS_OFFSET);
        final boolean reallyChanged = storedFlags != newFlags;
        if (reallyChanged) {
          pageBuffer.putInt(recordOffsetOnPage + FLAGS_OFFSET, newFlags);
          incrementRecordVersion(pageBuffer, recordOffsetOnPage);

          page.regionModified(recordOffsetOnPage, RECORD_SIZE_IN_BYTES);
        }
        return reallyChanged;
      }
      finally {
        page.unlockPageForWrite();
      }
    }
  }

  @Override
  public @PersistentFS.Attributes int getFlags(final int recordId) throws IOException {
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
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.lockPageForWrite();
      try {
        final ByteBuffer pageBuffer = page.rawPageBuffer();
        final long storedLength = pageBuffer.getLong(recordOffsetOnPage + LENGTH_OFFSET);
        final boolean reallyChanged = storedLength != newLength;
        if (reallyChanged) {
          pageBuffer.putLong(recordOffsetOnPage + LENGTH_OFFSET, newLength);
          incrementRecordVersion(pageBuffer, recordOffsetOnPage);
          page.regionModified(recordOffsetOnPage, RECORD_SIZE_IN_BYTES);
        }
        return reallyChanged;
      }
      finally {
        page.unlockPageForWrite();
      }
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
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.lockPageForWrite();
      try {
        final ByteBuffer pageBuffer = page.rawPageBuffer();
        final long storedTimestamp = pageBuffer.getLong(recordOffsetOnPage + TIMESTAMP_OFFSET);
        final boolean reallyChanged = storedTimestamp != newTimestamp;
        if (reallyChanged) {
          pageBuffer.putLong(recordOffsetOnPage + TIMESTAMP_OFFSET, newTimestamp);
          incrementRecordVersion(pageBuffer, recordOffsetOnPage);
          page.regionModified(recordOffsetOnPage, RECORD_SIZE_IN_BYTES);
        }
        return reallyChanged;
      }
      finally {
        page.unlockPageForWrite();
      }
    }
  }

  @Override
  public int getModCount(final int recordId) throws IOException {
    return getIntField(recordId, MOD_COUNT_OFFSET);
  }

  @Override
  public void markRecordAsModified(final int recordId) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.lockPageForWrite();
      try {
        incrementRecordVersion(page.rawPageBuffer(), recordOffsetOnPage);
        page.regionModified(recordOffsetOnPage, RECORD_SIZE_IN_BYTES);
      }
      finally {
        page.unlockPageForWrite();
      }
    }
  }

  @Override
  public int getContentRecordId(final int recordId) throws IOException {
    return getIntField(recordId, CONTENT_REF_OFFSET);
  }

  @Override
  public boolean setContentRecordId(final int recordId,
                                    final int newContentRef) throws IOException {
    checkValidIdField(recordId, newContentRef, "contentRecordId");
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.lockPageForWrite();
      try {
        final ByteBuffer pageBuffer = page.rawPageBuffer();
        final int storedContentRefId = pageBuffer.getInt(recordOffsetOnPage + CONTENT_REF_OFFSET);
        final boolean reallyChanged = storedContentRefId != newContentRef;
        if (reallyChanged) {
          pageBuffer.putInt(recordOffsetOnPage + CONTENT_REF_OFFSET, newContentRef);
          incrementRecordVersion(pageBuffer, recordOffsetOnPage);

          page.regionModified(recordOffsetOnPage, RECORD_SIZE_IN_BYTES);
        }
        return reallyChanged;
      }
      finally {
        page.unlockPageForWrite();
      }
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
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.lockPageForWrite();
      try {
        final ByteBuffer pageBuffer = page.rawPageBuffer();
        pageBuffer.putInt(recordOffsetOnPage + PARENT_REF_OFFSET, parentId);
        pageBuffer.putInt(recordOffsetOnPage + NAME_REF_OFFSET, nameId);
        pageBuffer.putInt(recordOffsetOnPage + FLAGS_OFFSET, flags);
        if (overwriteAttrRef) {
          pageBuffer.putInt(recordOffsetOnPage + ATTR_REF_OFFSET, 0);
        }
        pageBuffer.putLong(recordOffsetOnPage + TIMESTAMP_OFFSET, timestamp);
        pageBuffer.putLong(recordOffsetOnPage + LENGTH_OFFSET, length);

        incrementRecordVersion(pageBuffer, recordOffsetOnPage);

        page.regionModified(recordOffsetOnPage, RECORD_SIZE_IN_BYTES);
      }
      finally {
        page.unlockPageForWrite();
      }
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
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.lockPageForWrite();
      try {
        final ByteBuffer pageBuffer = page.rawPageBuffer();
        for (int wordNo = 0; wordNo < recordSizeInInts; wordNo++) {
          final int offsetOfWord = recordOffsetOnPage + wordNo * Integer.BYTES;
          pageBuffer.putInt(offsetOfWord, 0);
        }

        page.regionModified(recordOffsetOnPage, RECORD_SIZE_IN_BYTES);
      }
      finally {
        page.unlockPageForWrite();
      }
    }
  }

  @Override
  public boolean processAllRecords(final @NotNull PersistentFSRecordsStorage.FsRecordProcessor processor) throws IOException {
    final int recordsCount = allocatedRecordsCount.get();
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
    //intentionally don't increment globalModCount
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
  }

  @Override
  public void setVersion(final int version) throws IOException {
    setIntHeaderField(HEADER_VERSION_OFFSET, version);
    setLongHeaderField(HEADER_TIMESTAMP_OFFSET, System.currentTimeMillis());
    globalModCount.incrementAndGet();
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
  public boolean isDirty() {
    return storage.isDirty();
  }

  @Override
  public void force() throws IOException {
    if (storage.isDirty()) {
      setIntHeaderField(HEADER_GLOBAL_MOD_COUNT_OFFSET, globalModCount.get());
      storage.force();
    }
  }

  @Override
  public void close() throws IOException {
    if (!storage.isClosed()) {
      force();
      storage.close();
    }
  }

  @Override
  public void closeAndClean() throws IOException {
    close();
    try {
      storage.closeAndRemoveAllFiles();
    }
    catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  // =============== implementation: addressing ========================================================= //

  /** Without recordId bounds checking */
  @VisibleForTesting
  long recordOffsetInFileUnchecked(final int recordId) {
    //recordId is 1-based, but 0-based is more convenient for the following:
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

  private void checkParentIdIsValid(int parentId) throws IndexOutOfBoundsException {
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

  //each access method acquires a page & acquires appropriate kind of page lock:

  private void setLongField(final int recordId,
                            final int fieldRelativeOffset,
                            final long fieldValue) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.lockPageForWrite();
      try {
        final ByteBuffer pageBuffer = page.rawPageBuffer();
        pageBuffer.putLong(recordOffsetOnPage + fieldRelativeOffset, fieldValue);
        incrementRecordVersion(pageBuffer, recordOffsetOnPage);
        page.regionModified(recordOffsetOnPage, RECORD_SIZE_IN_BYTES);
      }
      finally {
        page.unlockPageForWrite();
      }
    }
  }

  private long getLongField(final int recordId,
                            final int fieldRelativeOffset) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */ false)) {
      page.lockPageForRead();
      try {
        final ByteBuffer pageBuffer = page.rawPageBuffer();
        return pageBuffer.getLong(recordOffsetOnPage + fieldRelativeOffset);
      }
      finally {
        page.unlockPageForRead();
      }
    }
  }

  private int setIntField(final int recordId,
                          final int fieldRelativeOffset,
                          final int fieldValue) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.lockPageForWrite();
      try {
        final ByteBuffer pageBuffer = page.rawPageBuffer();
        int previousValue = pageBuffer.getInt(recordOffsetOnPage + fieldRelativeOffset);
        pageBuffer.putInt(recordOffsetOnPage + fieldRelativeOffset, fieldValue);
        incrementRecordVersion(pageBuffer, recordOffsetOnPage);
        page.regionModified(recordOffsetOnPage, RECORD_SIZE_IN_BYTES);
        return previousValue;
      }
      finally {
        page.unlockPageForWrite();
      }
    }
  }

  private int getIntField(final int recordId,
                          final int fieldRelativeOffset) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    try (final PageUnsafe page = (PageUnsafe)storage.pageByOffset(recordOffsetInFile, /*forWrite: */ false)) {
      page.lockPageForRead();
      try {
        final ByteBuffer pageBuffer = page.rawPageBuffer();
        return pageBuffer.getInt(recordOffsetOnPage + fieldRelativeOffset);
      }
      finally {
        page.unlockPageForRead();
      }
    }
  }

  private void incrementRecordVersion(final @NotNull ByteBuffer pageBuffer,
                                      final int recordOffsetOnPage) {
    pageBuffer.putInt(recordOffsetOnPage + MOD_COUNT_OFFSET, globalModCount.incrementAndGet());
  }

  //============ header fields access: ============================================================ //

  private void setLongHeaderField(final @HeaderOffset int headerRelativeOffsetBytes,
                                  final long headerValue) throws IOException {
    checkHeaderOffset(headerRelativeOffsetBytes);
    try (final Page page = storage.pageByOffset(headerRelativeOffsetBytes, /*forWrite: */ true)) {
      page.putLong(headerRelativeOffsetBytes, headerValue);
    }
  }

  private long getLongHeaderField(final @HeaderOffset int headerRelativeOffsetBytes) throws IOException {
    checkHeaderOffset(headerRelativeOffsetBytes);
    try (final Page page = storage.pageByOffset(headerRelativeOffsetBytes, /*forWrite: */ false)) {
      return page.getLong(headerRelativeOffsetBytes);
    }
  }

  private void setIntHeaderField(final @HeaderOffset int headerRelativeOffsetBytes,
                                 final int headerValue) throws IOException {
    checkHeaderOffset(headerRelativeOffsetBytes);
    try (final Page page = storage.pageByOffset(headerRelativeOffsetBytes, /*forWrite: */ true)) {
      page.putInt(headerRelativeOffsetBytes, headerValue);
    }
  }


  private int getIntHeaderField(final @HeaderOffset int headerRelativeOffsetBytes) throws IOException {
    checkHeaderOffset(headerRelativeOffsetBytes);
    try (final Page page = storage.pageByOffset(headerRelativeOffsetBytes, /*forWrite: */ false)) {
      return page.getInt(headerRelativeOffsetBytes);
    }
  }

  private static void checkHeaderOffset(final int headerRelativeOffset) {
    if (!(0 <= headerRelativeOffset && headerRelativeOffset < HEADER_SIZE)) {
      throw new IndexOutOfBoundsException(
        "headerFieldOffset(=" + headerRelativeOffset + ") is outside of header [0, " + HEADER_SIZE + ") ");
    }
  }
}
