// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

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
public final class PersistentInMemoryFSRecordsStorage implements PersistentFSRecordsStorage {

  /* ================ RECORD FIELDS LAYOUT (in ints = 4 bytes) ======================================== */

  private static final int HEADER_SIZE = PersistentFSHeaders.HEADER_SIZE;

  //RC: fields offsets are in int(4 bytes) because of historical reasons. Probably, switch to size in bytes later on, as
  //    code stabilizes
  private static final int PARENT_REF_OFFSET = 0;
  private static final int PARENT_REF_SIZE = 1;
  private static final int NAME_REF_OFFSET = PARENT_REF_OFFSET + PARENT_REF_SIZE;
  private static final int NAME_REF_SIZE = 1;
  private static final int FLAGS_OFFSET = NAME_REF_OFFSET + NAME_REF_SIZE;
  private static final int FLAGS_SIZE = 1;
  private static final int ATTR_REF_OFFSET = FLAGS_OFFSET + FLAGS_SIZE;
  private static final int ATTR_REF_SIZE = 1;
  private static final int CONTENT_REF_OFFSET = ATTR_REF_OFFSET + ATTR_REF_SIZE;
  private static final int CONTENT_REF_SIZE = 1;
  private static final int MOD_COUNT_OFFSET = CONTENT_REF_OFFSET + CONTENT_REF_SIZE;
  private static final int MOD_COUNT_SIZE = 1;
  //RC: moved timestamp 1 field down so both LONG fields are 8-byte aligned (for atomic accesses alignment is important)
  private static final int TIMESTAMP_OFFSET = MOD_COUNT_OFFSET + MOD_COUNT_SIZE;
  private static final int TIMESTAMP_SIZE = 2;
  private static final int LENGTH_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
  private static final int LENGTH_SIZE = 2;

  private static final int RECORD_SIZE_IN_INTS = LENGTH_OFFSET + LENGTH_SIZE;
  private static final int RECORD_SIZE_IN_BYTES = RECORD_SIZE_IN_INTS * Integer.BYTES;

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


  private final Path storagePath;


  public PersistentInMemoryFSRecordsStorage(final Path path,
                                            final int maxRecords) throws IOException {
    storagePath = Objects.requireNonNull(path, "path");
    if (maxRecords <= 0) {
      throw new IllegalArgumentException("maxRecords(=" + maxRecords + ") should be >0");
    }
    this.maxRecords = maxRecords;
    //this.records = new UnsafeBuffer(maxRecords * RECORD_SIZE_IN_BYTES+ HEADER_SIZE);
    this.records = ByteBuffer.allocate(maxRecords * RECORD_SIZE_IN_BYTES + HEADER_SIZE)
      .order(nativeOrder());

    if (Files.exists(path)) {
      final long fileSize = Files.size(path);
      if (fileSize > records.capacity()) {
        final long recordsInFile = (fileSize - HEADER_SIZE) / RECORD_SIZE_IN_BYTES;
        throw new IllegalArgumentException(
          "[" + path + "](=" + fileSize + "b) contains " + recordsInFile + " records > maxRecords(=" + maxRecords + ") " +
          "=> can't load all the records from file!");
      }

      try (ByteChannel channel = Files.newByteChannel(path)) {
        final int actualBytesRead = channel.read(records);
        if (actualBytesRead <= 0) {
          allocatedRecordsCount.set(0);
        }
        else {
          final int recordsRead = (actualBytesRead - HEADER_SIZE) / RECORD_SIZE_IN_BYTES;
          final int recordExcess = (actualBytesRead - HEADER_SIZE) % RECORD_SIZE_IN_BYTES;
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
  public int allocateRecord() {
    final int recordId = allocatedRecordsCount.incrementAndGet();
    if (recordId > maxRecords) {
      throw new IndexOutOfBoundsException("maxRecords(=" + maxRecords + ") limit exceeded");
    }
    markDirty();
    return recordId;
  }

  @Override
  public void setAttributeRecordId(final int recordId,
                                   final int recordRef) throws IOException {
    if (recordRef < NULL_ID) {
      throw new IllegalArgumentException("file[id: " + recordId + "].attributeRecordId(=" + recordRef + ") must be >=0");
    }
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
  public int updateNameId(final int recordId,
                          final int nameId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(nameId);
    return setIntField(recordId, NAME_REF_OFFSET, nameId);
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

  @Override
  public int getModCount(final int recordId) throws IOException {
    return getIntField(recordId, MOD_COUNT_OFFSET);
  }

  @Override
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
    setParent(recordId, parentId);
    updateNameId(recordId, nameId);
    setFlags(recordId, flags);
    if (overwriteAttrRef) {
      setAttributeRecordId(recordId, 0);
    }
    setTimestamp(recordId, timestamp);
    setLength(recordId, length);
  }

  @Override
  public void cleanRecord(final int recordId) throws IOException {
    checkRecordId(recordId);
    //fill record with zeros, by 4 bytes at once:
    final int recordStartAtBytes = recordOffsetInBytes(recordId, 0);
    for (int wordNo = 0; wordNo < RECORD_SIZE_IN_INTS; wordNo++) {
      final int offset = recordStartAtBytes + wordNo * Integer.BYTES;
      INT_HANDLE.setVolatile(records, offset, 0);
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
  public void setConnectionStatus(final int connectionStatus) throws IOException {
    setIntHeaderField(HEADER_CONNECTION_STATUS_OFFSET, connectionStatus);
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

  @Override
  public int recordsCount() {
    return allocatedRecordsCount.get();
  }

  @Override
  public int maxAllocatedID() {
    return allocatedRecordsCount.get();
  }

  public long actualDataLength() {
    final int recordsCount = recordsCount();
    return (RECORD_SIZE_IN_INTS * (long)recordsCount) * Integer.BYTES + HEADER_SIZE;
  }


  @Override
  public boolean processAllRecords(final @NotNull FsRecordProcessor processor) throws IOException {
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

  @Override
  public void force() throws IOException {
    if (dirty.get()) {
      setIntHeaderField(HEADER_GLOBAL_MOD_COUNT_OFFSET, globalModCount.get());

      final long actualDataLength = actualDataLength();
      final ByteBuffer actualRecordsToStore = records.duplicate();
      actualRecordsToStore.position(0)
        .limit((int)actualDataLength)
        .order(records.order());
      try (final SeekableByteChannel channel = Files.newByteChannel(storagePath, WRITE, CREATE)) {
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

  //TODO RC: current implementation uses VarHandle as 'official' way. Unsafe could be (?) better way to do it, if performance proves itself
  //         to be less than expected

  private void setLongField(final int recordId,
                            final int fieldRelativeOffset,
                            final long fieldValue) {
    final int offset = recordOffsetInBytes(recordId, fieldRelativeOffset);
    LONG_HANDLE.setVolatile(records, offset, fieldValue);
    markDirty();
  }

  private long getLongField(final int recordId,
                            final int fieldRelativeOffset) {
    final int offset = recordOffsetInBytes(recordId, fieldRelativeOffset);
    return (long)LONG_HANDLE.getVolatile(records, offset);
  }

  private int setIntField(final int recordId,
                          final int fieldRelativeOffset,
                          final int fieldValue) {
    final int offset = recordOffsetInBytes(recordId, fieldRelativeOffset);
    int previousValue = (int)INT_HANDLE.getAndSet(records, offset, fieldValue);
    markDirty();
    return previousValue;
  }

  private int getIntField(final int recordId,
                          final int fieldRelativeOffset) {
    final int offset = recordOffsetInBytes(recordId, fieldRelativeOffset);
    return (int)INT_HANDLE.getVolatile(records, offset);
  }

  private int recordOffsetInBytes(final int recordId,
                                  final int fieldRelativeOffset) throws IndexOutOfBoundsException {
    checkRecordId(recordId);
    return (RECORD_SIZE_IN_INTS * (recordId - 1) + fieldRelativeOffset) * Integer.BYTES + HEADER_SIZE;
  }

  private void checkRecordId(final int recordId) throws IndexOutOfBoundsException {
    final int allocatedSoFar = allocatedRecordsCount.get();
    if (!(FSRecords.NULL_FILE_ID < recordId && recordId <= allocatedSoFar)) {
      throw new IndexOutOfBoundsException(
        "recordId(=" + recordId + ") is outside of allocated IDs range (0, " + allocatedSoFar + "]");
    }
  }

  private void setLongHeaderField(final @HeaderOffset int headerRelativeOffsetBytes,
                                  final long headerValue) {
    checkHeaderOffset(headerRelativeOffsetBytes);
    LONG_HANDLE.setVolatile(records, headerRelativeOffsetBytes, headerValue);
    markDirty();
  }

  private long getLongHeaderField(final @HeaderOffset int headerRelativeOffsetBytes) {
    checkHeaderOffset(headerRelativeOffsetBytes);
    return (long)LONG_HANDLE.getVolatile(records, headerRelativeOffsetBytes);
  }

  private void setIntHeaderField(final @HeaderOffset int headerRelativeOffsetBytes,
                                 final int headerValue) {
    checkHeaderOffset(headerRelativeOffsetBytes);
    INT_HANDLE.setVolatile(records, headerRelativeOffsetBytes, headerValue);
    markDirty();
  }


  private int getIntHeaderField(final @HeaderOffset int headerRelativeOffsetBytes) {
    checkHeaderOffset(headerRelativeOffsetBytes);
    return (int)INT_HANDLE.getVolatile(records, headerRelativeOffsetBytes);
  }

  private static void checkHeaderOffset(final int headerRelativeOffset) {
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
