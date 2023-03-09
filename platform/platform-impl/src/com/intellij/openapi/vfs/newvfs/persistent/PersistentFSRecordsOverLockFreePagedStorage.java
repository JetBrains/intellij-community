// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.io.PagedFileStorageLockFree;
import com.intellij.util.io.pagecache.Page;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSHeaders.*;

/**
 * Implementation uses new {@link PagedFileStorageLockFree}
 */
@ApiStatus.Internal
public class PersistentFSRecordsOverLockFreePagedStorage extends PersistentFSRecordsStorage
  implements IPersistentFSRecordsStorage {

  /* ================ RECORD FIELDS LAYOUT (in ints = 4 bytes) ======================================== */

  public static final int HEADER_SIZE = PersistentFSHeaders.HEADER_SIZE;

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

  private final @NotNull PagedFileStorageLockFree storage;

  /** How many records were allocated already. allocatedRecordsCount-1 == last record id */
  private final AtomicInteger allocatedRecordsCount = new AtomicInteger(0);
  private final AtomicInteger globalModCount = new AtomicInteger(0);

  //cached for faster access:
  private final transient int pageSize;
  private final transient int recordsPerPage;

  private final transient HeaderAccessor headerAccessor = new HeaderAccessor(this);


  public PersistentFSRecordsOverLockFreePagedStorage(final @NotNull PagedFileStorageLockFree storage) throws IOException {
    this.storage = storage;

    pageSize = storage.getPageSize();
    recordsPerPage = pageSize / RECORD_SIZE_IN_BYTES;

    final int recordsCountInStorage = loadRecordsCount(storage.length());
    allocatedRecordsCount.set(recordsCountInStorage);

    globalModCount.set(getIntHeaderField(HEADER_GLOBAL_MOD_COUNT_OFFSET));
  }

  @VisibleForTesting
  protected int loadRecordsCount(final long storageSizeBytes) throws IOException {
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
  public <R, E extends Throwable> R readRecord(final int recordId,
                                               final @NotNull RecordReader<R, E> reader) throws E, IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = recordOffsetOnPage(recordId);
    try (final Page page = storage.pageByOffset(recordOffsetInFile, /*forWrite: */false)) {
      page.lockPageForRead();
      try {
        final RecordAccessor recordAccessor = new RecordAccessor(recordId, recordOffsetOnPage, page);
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
    final int trueRecordId = (recordId == -1) ?
                             allocateRecord() :
                             recordId;
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    final int recordOffsetOnPage = recordOffsetOnPage(recordId);
    try (final Page page = storage.pageByOffset(recordOffsetInFile, /*forWrite: */true)) {
      page.lockPageForWrite();
      try {
        //RC: hope EscapeAnalysis removes the allocation here:
        final RecordAccessor recordAccessor = new RecordAccessor(recordId, recordOffsetOnPage, page);
        final boolean updated = updater.updateRecord(recordAccessor);
        if (updated) {
          incrementRecordVersion(page, recordOffsetOnPage);
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


  private static class RecordAccessor implements RecordForUpdate {
    private final int recordId;
    private final int recordOffsetInPage;
    private final Page recordPage;

    private RecordAccessor(final int recordId,
                           final int recordOffsetInPage,
                           final Page recordPage) {
      this.recordId = recordId;
      this.recordOffsetInPage = recordOffsetInPage;
      this.recordPage = recordPage;
    }

    @Override
    public int recordId() {
      return recordId;
    }

    @Override
    public int getAttributeRecordId() {
      return recordPage.getInt(recordOffsetInPage + ATTR_REF_OFFSET);
    }

    @Override
    public int getParent() {
      return recordPage.getInt(recordOffsetInPage + PARENT_REF_OFFSET);
    }

    @Override
    public int getNameId() {
      return recordPage.getInt(recordOffsetInPage + NAME_REF_OFFSET);
    }

    @Override
    public long getLength() throws IOException {
      return recordPage.getLong(recordOffsetInPage + LENGTH_OFFSET);
    }

    @Override
    public long getTimestamp() throws IOException {
      return recordPage.getLong(recordOffsetInPage + TIMESTAMP_OFFSET);
    }

    @Override
    public int getModCount() throws IOException {
      return recordPage.getInt(recordOffsetInPage + MOD_COUNT_OFFSET);
    }

    @Override
    public int getContentRecordId() throws IOException {
      return recordPage.getInt(recordOffsetInPage + CONTENT_REF_OFFSET);
    }

    @Override
    public @PersistentFS.Attributes int getFlags() throws IOException {
      return recordPage.getInt(recordOffsetInPage + FLAGS_OFFSET);
    }

    @Override
    public void setAttributeRecordId(final int attributeRecordId) throws IOException {
      recordPage.putInt(recordOffsetInPage + ATTR_REF_OFFSET, attributeRecordId);
    }

    @Override
    public void setParent(final int parentId) throws IOException {
      recordPage.putInt(recordOffsetInPage + PARENT_REF_OFFSET, parentId);
    }

    @Override
    public void setNameId(final int nameId) throws IOException {
      recordPage.putInt(recordOffsetInPage + NAME_REF_OFFSET, nameId);
    }

    @Override
    public boolean setFlags(final @PersistentFS.Attributes int flags) throws IOException {
      final int fieldOffsetInPage = recordOffsetInPage + FLAGS_OFFSET;
      if (recordPage.getInt(fieldOffsetInPage) != flags) {
        recordPage.putInt(fieldOffsetInPage, flags);
        return true;
      }
      return false;
    }

    @Override
    public boolean setLength(final long length) throws IOException {
      final int fieldOffsetInPage = recordOffsetInPage + LENGTH_OFFSET;
      if (recordPage.getLong(fieldOffsetInPage) != length) {
        recordPage.putLong(fieldOffsetInPage, length);
        return true;
      }
      return false;
    }

    @Override
    public boolean setTimestamp(final long timestamp) throws IOException {
      final int fieldOffsetInPage = recordOffsetInPage + TIMESTAMP_OFFSET;
      if (recordPage.getLong(fieldOffsetInPage) != timestamp) {
        recordPage.putLong(fieldOffsetInPage, timestamp);
        return true;
      }
      return false;
    }

    @Override
    public boolean setContentRecordId(final int contentRecordId) throws IOException {
      final int fieldOffsetInPage = recordOffsetInPage + CONTENT_REF_OFFSET;
      if (recordPage.getInt(fieldOffsetInPage) != contentRecordId) {
        recordPage.putInt(fieldOffsetInPage, contentRecordId);
        return true;
      }
      return false;
    }
  }

  private static class HeaderAccessor implements HeaderForUpdate {
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


  /* ==== access methods:  =================================================================== */

  @Override
  public int allocateRecord() {
    final int recordId = allocatedRecordsCount.getAndIncrement();
    return recordId;
  }

  @Override
  public void setAttributeRecordId(final int recordId,
                                   final int recordRef) throws IOException {
    setIntField(recordId, ATTR_REF_OFFSET, recordRef);
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
    final boolean reallyChanged = getIntField(recordId, FLAGS_OFFSET) != newFlags;
    if (reallyChanged) {
      setIntField(recordId, FLAGS_OFFSET, newFlags);
    }
    return reallyChanged;
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
    final boolean reallyChanged = getLongField(recordId, LENGTH_OFFSET) != newLength;
    if (reallyChanged) {
      setLongField(recordId, LENGTH_OFFSET, newLength);
    }
    return reallyChanged;
  }


  @Override
  public long getTimestamp(final int recordId) throws IOException {
    return getLongField(recordId, TIMESTAMP_OFFSET);
  }


  @Override
  public boolean setTimestamp(final int recordId,
                              final long newTimestamp) throws IOException {
    final boolean reallyChanged = getLongField(recordId, TIMESTAMP_OFFSET) != newTimestamp;
    if (reallyChanged) {
      setLongField(recordId, TIMESTAMP_OFFSET, newTimestamp);
    }
    return reallyChanged;
  }


  public int getModCount(final int recordId) throws IOException {
    return getIntField(recordId, MOD_COUNT_OFFSET);
  }


  public void markRecordAsModified(final int recordId) throws IOException {
    setIntField(recordId, MOD_COUNT_OFFSET, globalModCount.incrementAndGet());
  }


  @Override
  public int getContentRecordId(final int recordId) throws IOException {
    return getIntField(recordId, CONTENT_REF_OFFSET);
  }


  @Override
  public boolean setContentRecordId(final int recordId,
                                    final int contentRef) throws IOException {
    final boolean reallyChanged = getIntField(recordId, CONTENT_REF_OFFSET) != contentRef;
    if (reallyChanged) {
      setIntField(recordId, CONTENT_REF_OFFSET, contentRef);
    }
    return reallyChanged;
  }


  @Override
  public void fillRecord(final int recordId,
                         final long timestamp,
                         final long length,
                         final int flags,
                         final int nameId,
                         final int parentId,
                         final boolean overwriteAttrRef) throws IOException {
    final int recordOffsetOnPage = recordOffsetOnPage(recordId);
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    try (final Page page = storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.putInt(recordOffsetOnPage + PARENT_REF_OFFSET, parentId);
      page.putInt(recordOffsetOnPage + NAME_REF_OFFSET, nameId);
      page.putInt(recordOffsetOnPage + FLAGS_OFFSET, flags);
      if (overwriteAttrRef) {
        page.putInt(recordOffsetOnPage + ATTR_REF_OFFSET, 0);
      }
      page.putLong(recordOffsetOnPage + TIMESTAMP_OFFSET, timestamp);
      page.putLong(recordOffsetOnPage + LENGTH_OFFSET, length);
      incrementRecordVersion(page, recordOffsetOnPage);
    }
  }



  @Override
  public void cleanRecord(final int recordId) throws IOException {
    allocatedRecordsCount.updateAndGet(allocatedRecords -> Math.max(recordId + 1, allocatedRecords));

    //fill record with zeros, by 4 bytes at once:
    assert RECORD_SIZE_IN_BYTES % Integer.BYTES == 0 : "RECORD_SIZE_IN_BYTES(=" + RECORD_SIZE_IN_BYTES + ") is expected to be 32-aligned";
    final int recordSizeInInts = RECORD_SIZE_IN_BYTES / Integer.BYTES;

    final int recordOffsetOnPage = recordOffsetOnPage(recordId);
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    try (final Page page = storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.lockPageForWrite();
      try {
        for (int wordNo = 0; wordNo < recordSizeInInts; wordNo++) {
          final int offsetOfWord = recordOffsetOnPage + wordNo * Integer.BYTES;
          page.putInt(offsetOfWord, 0);
        }
      }
      finally {
        page.unlockPageForWrite();
      }
    }
  }

  /* ============== storage 'global' properties accessors: ================ */

  public long getTimestamp() throws IOException {
    return getLongHeaderField(HEADER_TIMESTAMP_OFFSET);
  }


  public void setConnectionStatus(final int connectionStatus) throws IOException {
    setIntHeaderField(HEADER_CONNECTION_STATUS_OFFSET, connectionStatus);
  }


  public int getConnectionStatus() throws IOException {
    return getIntHeaderField(HEADER_CONNECTION_STATUS_OFFSET);
  }


  public void setVersion(final int version) throws IOException {
    setIntHeaderField(HEADER_VERSION_OFFSET, version);
    setLongHeaderField(HEADER_TIMESTAMP_OFFSET, System.currentTimeMillis());
    globalModCount.incrementAndGet();
  }


  public int getVersion() throws IOException {
    return getIntHeaderField(HEADER_VERSION_OFFSET);
  }


  public int getGlobalModCount() {
    return globalModCount.get();
  }


  public long length() {
    final int recordsCount = allocatedRecordsCount.get();
    final boolean anythingChanged = globalModCount.get() > 0;
    if (recordsCount == 0 && !anythingChanged) {
      //Try to mimic other implementations behavior: they return actual file size, which is 0
      //  before first record allocated -- should be >0, since even no-record storage contains
      //  header, but other implementations use 0-th record as header...
      //TODO RC: it is better to have recordsCount() method
      return 0;
    }
    return actualDataLength();
  }

  public long actualDataLength() {
    final int recordsCount = allocatedRecordsCount.get();
    return recordOffsetInFileUnchecked(recordsCount);
  }


  public boolean processAllRecords(final @NotNull PersistentFSRecordsStorage.FsRecordProcessor processor) throws IOException {
    final int recordsCount = allocatedRecordsCount.get();
    for (int recordId = 0; recordId < recordsCount; recordId++) {
      processor.process(
        recordId,
        getNameId(recordId),
        getFlags(recordId),
        getParent(recordId),
        /* corrupted = */ false
      );
    }
    return true;
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
    force();
    try {
      storage.close();
    }
    catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  /* =============== implementation: addressing ==================================================== */

  /** Without recordId bounds checking */
  @VisibleForTesting
  protected long recordOffsetInFileUnchecked(final int recordId) {
    final int recordsOnHeaderPage = (pageSize - HEADER_SIZE) / RECORD_SIZE_IN_BYTES;
    if (recordId < recordsOnHeaderPage) {
      return HEADER_SIZE + recordId * (long)RECORD_SIZE_IN_BYTES;
    }

    //as-if there were no header:
    final int fullPages = recordId / recordsPerPage;
    final int recordsOnLastPage = recordId % recordsPerPage;

    //header on the first page "push out" few records:
    final int recordsExcessBecauseOfHeader = recordsPerPage - recordsOnHeaderPage;

    //so the last page could turn into +1 page:
    final int recordsReallyOnLastPage = recordsOnLastPage + recordsExcessBecauseOfHeader;
    return (long)(fullPages + recordsReallyOnLastPage / recordsPerPage) * pageSize
           + (long)(recordsReallyOnLastPage % recordsPerPage) * RECORD_SIZE_IN_BYTES;
  }

  private int recordOffsetOnPage(final int recordId) throws IndexOutOfBoundsException {
    checkRecordId(recordId);

    final int recordsOnHeaderPage = (pageSize - HEADER_SIZE) / RECORD_SIZE_IN_BYTES;
    if (recordId < recordsOnHeaderPage) {
      return HEADER_SIZE + recordId * RECORD_SIZE_IN_BYTES;
    }

    //as-if there were no header:
    final int recordsOnLastPage = recordId % recordsPerPage;

    //header on the first page "push out" few records:
    final int recordsExcessBecauseOfHeader = recordsPerPage - recordsOnHeaderPage;

    //so the last page could turn into +1 page:
    final int recordsReallyOnLastPage = recordsOnLastPage + recordsExcessBecauseOfHeader;
    return (recordsReallyOnLastPage % recordsPerPage) * RECORD_SIZE_IN_BYTES;
  }

  private void checkRecordId(final int recordId) throws IndexOutOfBoundsException {
    if (!(0 <= recordId && recordId < allocatedRecordsCount.get())) {
      throw new IndexOutOfBoundsException(
        "recordId(=" + recordId + ") is outside of allocated IDs range [0, " + allocatedRecordsCount + ")");
    }
  }

  /* =============== implementation: record access ================================================ */


  private void setLongField(final int recordId,
                            final int fieldRelativeOffset,
                            final long fieldValue) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    //TODO RC: just recordOffsetInFile % pageSize?
    final int recordOffsetOnPage = recordOffsetOnPage(recordId);
    try (final Page page = storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.putLong(recordOffsetOnPage + fieldRelativeOffset, fieldValue);
      incrementRecordVersion(page, recordOffsetOnPage);
    }
  }

  private long getLongField(final int recordId,
                            final int fieldRelativeOffset) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    //TODO RC: just recordOffsetInFile % pageSize?
    final int recordOffsetOnPage = recordOffsetOnPage(recordId);
    try (final Page page = storage.pageByOffset(recordOffsetInFile, /*forWrite: */ false)) {
      return page.getLong(recordOffsetOnPage + fieldRelativeOffset);
    }
  }

  private void setIntField(final int recordId,
                           final int fieldRelativeOffset,
                           final int fieldValue) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    //TODO RC: just recordOffsetInFile % pageSize?
    final int recordOffsetOnPage = recordOffsetOnPage(recordId);
    try (final Page page = storage.pageByOffset(recordOffsetInFile, /*forWrite: */ true)) {
      page.putInt(recordOffsetOnPage + fieldRelativeOffset, fieldValue);
      incrementRecordVersion(page, recordOffsetOnPage);
    }
  }

  private int getIntField(final int recordId,
                          final int fieldRelativeOffset) throws IOException {
    final long recordOffsetInFile = recordOffsetInFile(recordId);
    //TODO RC: just recordOffsetInFile % pageSize?
    final int recordOffsetOnPage = recordOffsetOnPage(recordId);
    try (final Page page = storage.pageByOffset(recordOffsetInFile, /*forWrite: */ false)) {
      return page.getInt(recordOffsetOnPage + fieldRelativeOffset);
    }
  }

  private long recordOffsetInFile(final int recordId) throws IndexOutOfBoundsException {
    checkRecordId(recordId);
    return recordOffsetInFileUnchecked(recordId);
  }

  private void incrementRecordVersion(final @NotNull Page page,
                                      final int recordOffsetOnPage) {
    page.putInt(recordOffsetOnPage + MOD_COUNT_OFFSET, globalModCount.incrementAndGet());
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
