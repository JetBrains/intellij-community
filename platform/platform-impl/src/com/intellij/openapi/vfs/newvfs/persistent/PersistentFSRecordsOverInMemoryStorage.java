// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSHeaders.*;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * This implementation keeps all FSRecords always in RAM, but it still loads them from file,
 * and persist changes into the file on {@linkplain #close()}
 * <p>
 * Intended for use as a reference implementation, to compare other impls against
 * (e.g. by performance)
 */
@ApiStatus.Internal
@TestOnly
public final class PersistentFSRecordsOverInMemoryStorage implements PersistentFSRecordsStorage, IPersistentFSRecordsStorage {

  /* ================ RECORD FIELDS LAYOUT ======================================================== */

  private static final int HEADER_SIZE = PersistentFSHeaders.HEADER_SIZE;

  @VisibleForTesting
  @ApiStatus.Internal
  public static final class RecordLayout {
    //@formatter:off
    static final int PARENT_REF_OFFSET        = 0;   //int32
    static final int NAME_REF_OFFSET          = 4;   //int32
    static final int FLAGS_OFFSET             = 8;   //int32
    static final int ATTR_REF_OFFSET          = 12;  //int32
    static final int CONTENT_REF_OFFSET       = 16;  //int32
    static final int MOD_COUNT_OFFSET         = 20;  //int32

    //RC: moved TIMESTAMP 1 field down so both LONG fields are 8-byte aligned (for atomic accesses alignment is important)
    static final int TIMESTAMP_OFFSET         = 24;  //int64
    static final int LENGTH_OFFSET            = 32;  //int64

    public static final int RECORD_SIZE_IN_BYTES     = 40;
    //@formatter:on
  }


  /* ================ RECORD FIELDS LAYOUT end             ======================================== */

  private static final VarHandle INT_HANDLE = MethodHandles.byteBufferViewVarHandle(int[].class, nativeOrder());
  private static final VarHandle LONG_HANDLE = MethodHandles.byteBufferViewVarHandle(long[].class, nativeOrder());


  private final int maxRecords;

  private final ByteBuffer records;
  private final AtomicInteger allocatedRecordsCount = new AtomicInteger(0);
  //TODO RC: it would be better to directly access PersistentFSHeaders.HEADER_GLOBAL_MOD_COUNT_OFFSET position in a bytebuffer,
  //         but issue is with incrementAndGet(): VarHandle doesn't have this method. It could be emulated with CAS, but this is
  //         slightly less effective
  private final AtomicInteger globalModCount = new AtomicInteger(0);
  private final AtomicBoolean dirty = new AtomicBoolean(false);

  private final transient HeaderAccessor headerAccessor = new HeaderAccessor(this);


  private final Path storagePath;


  public PersistentFSRecordsOverInMemoryStorage(Path path,
                                                int maxRecords) throws IOException {
    storagePath = Objects.requireNonNull(path, "path");
    if (maxRecords <= 0) {
      throw new IllegalArgumentException("maxRecords(=" + maxRecords + ") should be >0");
    }
    this.maxRecords = maxRecords;
    //this.records = new UnsafeBuffer(maxRecords * RECORD_SIZE_IN_BYTES+ HEADER_SIZE);
    this.records = ByteBuffer.allocateDirect(maxRecords * RecordLayout.RECORD_SIZE_IN_BYTES + HEADER_SIZE)
      .order(nativeOrder());

    if (Files.exists(path)) {
      long fileSize = Files.size(path);
      if (fileSize > records.capacity()) {
        long recordsInFile = (fileSize - HEADER_SIZE) / RecordLayout.RECORD_SIZE_IN_BYTES;
        throw new IllegalArgumentException(
          "[" + path + "](=" + fileSize + "b) contains " + recordsInFile + " records > maxRecords(=" + maxRecords + ") " +
          "=> can't load all the records from file!");
      }

      try (ByteChannel channel = Files.newByteChannel(path)) {
        int actualBytesRead = channel.read(records);
        if (actualBytesRead <= 0) {
          allocatedRecordsCount.set(0);
        }
        else {
          int recordsRead = (actualBytesRead - HEADER_SIZE) / RecordLayout.RECORD_SIZE_IN_BYTES;
          int recordExcess = (actualBytesRead - HEADER_SIZE) % RecordLayout.RECORD_SIZE_IN_BYTES;
          if (recordExcess > 0) {
            throw new IOException(
              "[" + path + "] likely truncated: (" + actualBytesRead + "b) " +
              " = (" + recordsRead + " whole records) + " + recordExcess + "b excess");
          }
          allocatedRecordsCount.set(recordsRead);
        }
      }
    }
    globalModCount.set(getIntHeaderField(HEADER_GLOBAL_MOD_COUNT_OFFSET));
  }

  @Override
  public int allocateRecord() throws IOException {
    int recordId = allocatedRecordsCount.incrementAndGet();
    if (recordId > maxRecords) {
      throw new IndexOutOfBoundsException("maxRecords(=" + maxRecords + ") limit exceeded");
    }
    markRecordAsModified(recordId);
    markDirty();
    return recordId;
  }

  @Override
  public void setAttributeRecordId(int recordId,
                                   int recordRef) throws IOException {
    checkValidIdField(recordId, recordRef, "attributeRecordId");
    setIntField(recordId, RecordLayout.ATTR_REF_OFFSET, recordRef);
  }

  @Override
  public int getAttributeRecordId(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.ATTR_REF_OFFSET);
  }

  @Override
  public int getParent(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.PARENT_REF_OFFSET);
  }

  @Override
  public void setParent(int recordId,
                        int parentId) throws IOException {
    checkParentIdIsValid(parentId);
    setIntField(recordId, RecordLayout.PARENT_REF_OFFSET, parentId);
  }

  @Override
  public int getNameId(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.NAME_REF_OFFSET);
  }

  @Override
  public int updateNameId(int recordId,
                          int nameId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(nameId);
    return setIntField(recordId, RecordLayout.NAME_REF_OFFSET, nameId);
  }


  @Override
  public boolean setFlags(int recordId,
                          @PersistentFS.Attributes int newFlags) throws IOException {
    int oldFlags = getIntField(recordId, RecordLayout.FLAGS_OFFSET);
    boolean reallyChanged = (oldFlags != newFlags);
    if (reallyChanged) {
      setIntField(recordId, RecordLayout.FLAGS_OFFSET, newFlags);
    }
    return reallyChanged;
  }

  @Override
  @SuppressWarnings("MagicConstant")
  public @PersistentFS.Attributes int getFlags(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.FLAGS_OFFSET);
  }

  @Override
  public long getLength(int recordId) throws IOException {
    return getLongField(recordId, RecordLayout.LENGTH_OFFSET);
  }

  @Override
  public boolean setLength(int recordId,
                           long newLength) throws IOException {
    boolean reallyChanged = getLongField(recordId, RecordLayout.LENGTH_OFFSET) != newLength;
    if (reallyChanged) {
      setLongField(recordId, RecordLayout.LENGTH_OFFSET, newLength);
    }
    return reallyChanged;
  }

  @Override
  public long getTimestamp(int recordId) throws IOException {
    return getLongField(recordId, RecordLayout.TIMESTAMP_OFFSET);
  }

  @Override
  public boolean setTimestamp(int recordId,
                              long newTimestamp) throws IOException {
    boolean reallyChanged = getLongField(recordId, RecordLayout.TIMESTAMP_OFFSET) != newTimestamp;
    if (reallyChanged) {
      setLongField(recordId, RecordLayout.TIMESTAMP_OFFSET, newTimestamp);
    }
    return reallyChanged;
  }

  @Override
  public int getModCount(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.MOD_COUNT_OFFSET);
  }

  @Override
  public void markRecordAsModified(int recordId) throws IOException {
    setIntField(recordId, RecordLayout.MOD_COUNT_OFFSET, globalModCount.incrementAndGet());
  }

  @Override
  public int getContentRecordId(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.CONTENT_REF_OFFSET);
  }

  @Override
  public boolean setContentRecordId(int recordId,
                                    int contentRef) throws IOException {
    checkValidIdField(recordId, contentRef, "contentRecordId");
    boolean reallyChanged = getIntField(recordId, RecordLayout.CONTENT_REF_OFFSET) != contentRef;
    if (reallyChanged) {
      setIntField(recordId, RecordLayout.CONTENT_REF_OFFSET, contentRef);
    }
    return reallyChanged;
  }

  @Override
  public void cleanRecord(int recordId) throws IOException {
    checkRecordId(recordId);
    //fill record with zeros, by 4 bytes at once:
    int recordStartAtBytes = recordOffsetInBytes(recordId, 0);
    int recordSizeInInts = RecordLayout.RECORD_SIZE_IN_BYTES / Integer.BYTES;
    for (int wordNo = 0; wordNo < recordSizeInInts; wordNo++) {
      int offset = recordStartAtBytes + wordNo * Integer.BYTES;
      INT_HANDLE.setVolatile(records, offset, 0);
    }
    markDirty();
  }

  @Override
  public <R> R readRecord(int recordId,
                          @NotNull RecordReader<R> reader) throws IOException {
    RecordAccessor recordAccessor = new RecordAccessor(recordId, this);
    return reader.readRecord(recordAccessor);
  }

  @Override
  public int updateRecord(int recordId,
                          @NotNull RecordUpdater updater) throws IOException {
    int trueRecordId = (recordId <= NULL_ID) ?
                       allocateRecord() :
                       recordId;
    //RC: hope EscapeAnalysis removes the allocation here:
    RecordAccessor recordAccessor = new RecordAccessor(recordId, this);
    boolean updated = updater.updateRecord(recordAccessor);
    if (updated) {
      //incrementRecordVersion(recordAccessor.pageBuffer, recordOffsetOnPage);
    }
    return trueRecordId;
  }

  @Override
  public <R> R readHeader(@NotNull HeaderReader<R> reader) throws IOException {
    return reader.readHeader(headerAccessor);
  }

  @Override
  public void updateHeader(@NotNull HeaderUpdater updater) throws IOException {
    if (updater.updateHeader(headerAccessor)) {
      globalModCount.incrementAndGet();
    }
  }

  /* ============== global storage properties accessors ================ */

  @Override
  public boolean isDirty() {
    return dirty.get();
  }

  @Override
  public long getTimestamp() throws IOException {
    return getLongHeaderField(HEADER_TIMESTAMP_OFFSET);
  }

  @Override
  public boolean wasClosedProperly() throws IOException {
    return true;// 'previous session' make no sense for non-persistent in-memory storage
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
  public void setVersion(int version) throws IOException {
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
  public int getFlags() throws IOException {
    return getIntHeaderField(HEADER_FLAGS_OFFSET);
  }

  @Override
  public boolean updateFlags(int flagsToAdd, int flagsToRemove) throws IOException {
    int currentFlags = (int)INT_HANDLE.getVolatile(records, HEADER_FLAGS_OFFSET);
    int newFlags = (currentFlags & ~flagsToRemove) | flagsToAdd;
    if (newFlags == currentFlags) {
      return false;
    }
    //MAYBE RC: use CAS to make an update atomic?
    INT_HANDLE.setVolatile(records, HEADER_FLAGS_OFFSET, newFlags);
    markDirty();
    return true;
  }

  @Override
  public int getGlobalModCount() {
    return globalModCount.get();
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
  public boolean isValidFileId(int recordId) {
    int allocatedSoFar = allocatedRecordsCount.get();
    return FSRecords.NULL_FILE_ID < recordId && recordId <= allocatedSoFar;
  }

  public long actualDataLength() {
    int recordsCount = recordsCount();
    return (RecordLayout.RECORD_SIZE_IN_BYTES * (long)recordsCount) + HEADER_SIZE;
  }


  @Override
  public boolean processAllRecords(@NotNull FsRecordProcessor processor) throws IOException {
    int recordsCount = allocatedRecordsCount.get();
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

  @Override
  public void force() throws IOException {
    if (dirty.get()) {
      setIntHeaderField(HEADER_GLOBAL_MOD_COUNT_OFFSET, globalModCount.get());

      long actualDataLength = actualDataLength();
      ByteBuffer actualRecordsToStore = records.duplicate();
      actualRecordsToStore.position(0)
        .limit((int)actualDataLength)
        .order(records.order());
      try (SeekableByteChannel channel = Files.newByteChannel(storagePath, WRITE, CREATE)) {
        channel.write(actualRecordsToStore);
      }
      markNotDirty();
    }
  }

  @Override
  public void close() throws IOException {
    force();
  }

  @Override
  public void closeAndClean() throws IOException {
    close();
    //...and nothing to remove
  }

  /* =============== implementation =============================================================== */

  @SuppressWarnings("TestOnlyProblems")
  private static class RecordAccessor implements RecordForUpdate {
    private final int recordId;
    private final @NotNull PersistentFSRecordsOverInMemoryStorage records;

    private RecordAccessor(int recordId,
                           @NotNull PersistentFSRecordsOverInMemoryStorage records) {
      this.recordId = recordId;
      this.records = records;
    }

    @Override
    public int recordId() {
      return recordId;
    }

    @Override
    public int getAttributeRecordId() throws IOException {
      return records.getAttributeRecordId(recordId);
    }

    @Override
    public int getParent() throws IOException {
      return records.getParent(recordId);
    }

    @Override
    public int getNameId() throws IOException {
      return records.getNameId(recordId);
    }

    @Override
    public long getLength() throws IOException {
      return records.getLength(recordId);
    }

    @Override
    public long getTimestamp() throws IOException {
      return records.getTimestamp();
    }

    @Override
    public int getModCount() throws IOException {
      return records.getModCount(recordId);
    }

    @Override
    public int getContentRecordId() throws IOException {
      return records.getContentRecordId(recordId);
    }

    @Override
    public @PersistentFS.Attributes int getFlags() throws IOException {
      return records.getFlags(recordId);
    }

    @Override
    public void setAttributeRecordId(int attributeRecordId) throws IOException {
      records.setAttributeRecordId(recordId, attributeRecordId);
    }

    @Override
    public void setParent(int parentId) throws IOException {
      records.setParent(recordId, parentId);
    }

    @Override
    public void setNameId(int nameId) throws IOException {
      records.updateNameId(recordId, nameId);
    }

    @Override
    public boolean setFlags(@PersistentFS.Attributes int flags) throws IOException {
      return records.setFlags(recordId, flags);
    }

    @Override
    public boolean setLength(long length) throws IOException {
      return records.setLength(recordId, length);
    }

    @Override
    public boolean setTimestamp(long timestamp) throws IOException {
      return records.setTimestamp(recordId, timestamp);
    }

    @Override
    public boolean setContentRecordId(int contentRecordId) throws IOException {
      return records.setContentRecordId(recordId, contentRecordId);
    }
  }

  @SuppressWarnings("TestOnlyProblems")
  private static final class HeaderAccessor implements HeaderForUpdate {
    private final @NotNull PersistentFSRecordsOverInMemoryStorage records;

    private HeaderAccessor(@NotNull PersistentFSRecordsOverInMemoryStorage records) { this.records = records; }

    @Override
    public long getTimestamp() throws IOException {
      return records.getTimestamp();
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
    public void setVersion(int version) throws IOException {
      records.setVersion(version);
    }
  }


  //TODO RC: current implementation uses VarHandle as 'official' way. Unsafe could be (?) better way to do it, if performance proves itself
  //         to be less than expected

  private void checkParentIdIsValid(int parentId) throws IndexOutOfBoundsException {
    if (parentId == NULL_ID) {
      //parentId could be NULL (for root records) -- this is the difference with checkRecordIdIsValid()
      return;
    }
    if (!isValidFileId(parentId)) {
      throw new IndexOutOfBoundsException(
        "parentId(=" + parentId + ") is outside of allocated IDs range [0, " + maxAllocatedID() + "]");
    }
  }

  private static void checkValidIdField(int recordId,
                                        int idFieldValue,
                                        @NotNull String fieldName) {
    if (idFieldValue < NULL_ID) {
      throw new IllegalArgumentException("file[id: " + recordId + "]." + fieldName + "(=" + idFieldValue + ") must be >=0");
    }
  }

  private void setLongField(int recordId,
                            int fieldRelativeOffset,
                            long fieldValue) {
    int offset = recordOffsetInBytes(recordId, fieldRelativeOffset);
    LONG_HANDLE.setVolatile(records, offset, fieldValue);
    markDirty();
  }

  private long getLongField(int recordId,
                            int fieldRelativeOffset) {
    int offset = recordOffsetInBytes(recordId, fieldRelativeOffset);
    return (long)LONG_HANDLE.getVolatile(records, offset);
  }

  private int setIntField(int recordId,
                          int fieldRelativeOffset,
                          int fieldValue) {
    int offset = recordOffsetInBytes(recordId, fieldRelativeOffset);
    int previousValue = (int)INT_HANDLE.getAndSet(records, offset, fieldValue);
    markDirty();
    return previousValue;
  }

  private int getIntField(int recordId,
                          int fieldRelativeOffset) {
    int offset = recordOffsetInBytes(recordId, fieldRelativeOffset);
    return (int)INT_HANDLE.getVolatile(records, offset);
  }

  private int recordOffsetInBytes(int recordId,
                                  int fieldRelativeOffset) throws IndexOutOfBoundsException {
    checkRecordId(recordId);
    return (RecordLayout.RECORD_SIZE_IN_BYTES * (recordId - 1) + fieldRelativeOffset) + HEADER_SIZE;
  }

  private void checkRecordId(int recordId) throws IndexOutOfBoundsException {
    if (!isValidFileId(recordId)) {
      int allocatedSoFar = allocatedRecordsCount.get();
      throw new IndexOutOfBoundsException(
        "recordId(=" + recordId + ") is outside of allocated IDs range (0, " + allocatedSoFar + "]");
    }
  }

  private void setLongHeaderField(@HeaderOffset int headerRelativeOffsetBytes,
                                  long headerValue) {
    checkHeaderOffset(headerRelativeOffsetBytes);
    LONG_HANDLE.setVolatile(records, headerRelativeOffsetBytes, headerValue);
    markDirty();
  }

  private long getLongHeaderField(@HeaderOffset int headerRelativeOffsetBytes) {
    checkHeaderOffset(headerRelativeOffsetBytes);
    return (long)LONG_HANDLE.getVolatile(records, headerRelativeOffsetBytes);
  }

  private void setIntHeaderField(@HeaderOffset int headerRelativeOffsetBytes,
                                 int headerValue) {
    checkHeaderOffset(headerRelativeOffsetBytes);
    INT_HANDLE.setVolatile(records, headerRelativeOffsetBytes, headerValue);
    markDirty();
  }


  private int getIntHeaderField(@HeaderOffset int headerRelativeOffsetBytes) {
    checkHeaderOffset(headerRelativeOffsetBytes);
    return (int)INT_HANDLE.getVolatile(records, headerRelativeOffsetBytes);
  }

  private static void checkHeaderOffset(int headerRelativeOffset) {
    if (!(0 <= headerRelativeOffset && headerRelativeOffset < HEADER_SIZE)) {
      throw new IndexOutOfBoundsException(
        "headerFieldOffset(=" + headerRelativeOffset + ") is outside of header [0, " + HEADER_SIZE + ") ");
    }
  }

  private void markDirty() {
    dirty.set(true);
  }

  private void markNotDirty() {
    dirty.set(false);
  }
}
