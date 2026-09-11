// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
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

  private static final ValueLayout.OfInt  INT32_VALUE_LAYOUT = ValueLayout.JAVA_INT.withOrder(nativeOrder());
  private static final ValueLayout.OfLong INT64_VALUE_LAYOUT = ValueLayout.JAVA_LONG.withOrder(nativeOrder());

  private static final class HeaderLayout {
    static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
      INT32_VALUE_LAYOUT.withName("version"),
      INT32_VALUE_LAYOUT.withName("globalModCount"),
      INT64_VALUE_LAYOUT.withName("timestamp"),
      INT32_VALUE_LAYOUT.withName("errorsAccumulated"),
      INT32_VALUE_LAYOUT.withName("flags")
    ).withName("PersistentFSRecords.InMemoryHeaderLayout")
     .withByteAlignment(8);//The header size keeps records aligned to int64.

    //@formatter:off
    static final PathElement VERSION_FIELD            = groupElement("version");
    static final PathElement GLOBAL_MOD_COUNT_FIELD   = groupElement("globalModCount");
    static final PathElement TIMESTAMP_FIELD          = groupElement("timestamp");
    static final PathElement ERRORS_ACCUMULATED_FIELD = groupElement("errorsAccumulated");
    static final PathElement FLAGS_FIELD              = groupElement("flags");
    //@formatter:on

    private static VarHandle fieldHandle(PathElement fieldPath) {
      return LAYOUT.varHandle(fieldPath).withInvokeExactBehavior();
    }

    //@formatter:off
    static final VarHandle VERSION                    = fieldHandle(VERSION_FIELD);
    static final VarHandle GLOBAL_MOD_COUNT           = fieldHandle(GLOBAL_MOD_COUNT_FIELD);
    static final VarHandle TIMESTAMP                  = fieldHandle(TIMESTAMP_FIELD);
    static final VarHandle ERRORS_ACCUMULATED         = fieldHandle(ERRORS_ACCUMULATED_FIELD);
    static final VarHandle FLAGS                      = fieldHandle(FLAGS_FIELD);


    //The header size keeps records aligned to int64
    static final int HEADER_SIZE                      = Math.toIntExact(LAYOUT.byteSize());
    //@formatter:on
  }

  private static final class RecordLayout {
    static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
      INT32_VALUE_LAYOUT.withName("parentRef"),
      INT32_VALUE_LAYOUT.withName("nameRef"),
      INT32_VALUE_LAYOUT.withName("flags"),
      INT32_VALUE_LAYOUT.withName("attributeRef"),
      INT32_VALUE_LAYOUT.withName("contentRef"),
      INT32_VALUE_LAYOUT.withName("modCount"),
      INT64_VALUE_LAYOUT.withName("timestamp"),
      INT64_VALUE_LAYOUT.withName("length")
    ).withName("PersistentFSRecords.InMemoryRecordLayout");

    //@formatter:off
    static final PathElement PARENT_REF_FIELD     = groupElement("parentRef");
    static final PathElement NAME_REF_FIELD       = groupElement("nameRef");
    static final PathElement FLAGS_FIELD          = groupElement("flags");
    static final PathElement ATTR_REF_FIELD       = groupElement("attributeRef");
    static final PathElement CONTENT_REF_FIELD    = groupElement("contentRef");
    static final PathElement MOD_COUNT_FIELD      = groupElement("modCount");
    /** The timestamp and length fields follow all int32 fields to keep the int64 fields aligned. */
    static final PathElement TIMESTAMP_FIELD      = groupElement("timestamp");
    static final PathElement LENGTH_FIELD         = groupElement("length");
    //@formatter:on

    private static VarHandle fieldHandle(PathElement fieldPath) {
      return LAYOUT.varHandle(fieldPath).withInvokeExactBehavior();
    }

    //@formatter:off
    static final VarHandle PARENT_REF             = fieldHandle(PARENT_REF_FIELD);
    static final VarHandle NAME_REF               = fieldHandle(NAME_REF_FIELD);
    static final VarHandle FLAGS                  = fieldHandle(FLAGS_FIELD);
    static final VarHandle ATTR_REF               = fieldHandle(ATTR_REF_FIELD);
    static final VarHandle CONTENT_REF            = fieldHandle(CONTENT_REF_FIELD);
    static final VarHandle MOD_COUNT              = fieldHandle(MOD_COUNT_FIELD);
    static final VarHandle TIMESTAMP              = fieldHandle(TIMESTAMP_FIELD);
    static final VarHandle LENGTH                 = fieldHandle(LENGTH_FIELD);

    static final int RECORD_SIZE_IN_BYTES         = Math.toIntExact(LAYOUT.byteSize());
    //@formatter:on
  }


  private final int maxRecords;

  private final Arena recordsArena;
  private final MemorySegment recordsMemorySegment;
  private final AtomicInteger allocatedRecordsCount = new AtomicInteger(0);
  //TODO RC: it would be better to directly access PersistentFSHeaders.HEADER_GLOBAL_MOD_COUNT_OFFSET position in a bytebuffer,
  //         but issue is with incrementAndGet(): VarHandle doesn't have this method. It could be emulated with CAS, but this is
  //         slightly less effective
  private final AtomicInteger globalModCount = new AtomicInteger(0);
  private final AtomicBoolean dirty = new AtomicBoolean(false);

  private final transient HeaderAccessor headerAccessor = new HeaderAccessor(this);


  private final Path storagePath;


  public PersistentFSRecordsOverInMemoryStorage(@NotNull Path path,
                                                int maxRecords) throws IOException {
    storagePath = Objects.requireNonNull(path, "path");
    if (maxRecords <= 0) {
      throw new IllegalArgumentException("maxRecords(=" + maxRecords + ") should be >0");
    }
    this.maxRecords = maxRecords;
    var arena = Arena.ofShared();
    try {
      long recordsSize = Math.multiplyExact((long)maxRecords, RecordLayout.RECORD_SIZE_IN_BYTES);
      long storageSize = Math.addExact(HeaderLayout.HEADER_SIZE, recordsSize);
      MemorySegment records = arena.allocate(storageSize, RecordLayout.LAYOUT.byteAlignment());

      if (Files.exists(path)) {
        long fileSize = Files.size(path);
        if (fileSize > records.byteSize()) {
          long recordsInFile = (fileSize - HeaderLayout.HEADER_SIZE) / RecordLayout.RECORD_SIZE_IN_BYTES;
          throw new IllegalArgumentException(
            "[" + path + "](=" + fileSize + "b) contains " + recordsInFile + " records > maxRecords(=" + maxRecords + ") " +
            "=> can't load all the records from file!");
        }

        ByteBuffer recordsBuffer = records.asByteBuffer().order(nativeOrder());
        try (ByteChannel channel = Files.newByteChannel(path)) {
          int actualBytesRead = channel.read(recordsBuffer);
          if (actualBytesRead <= 0) {
            allocatedRecordsCount.set(0);
          }
          else {
            int recordsRead = (actualBytesRead - HeaderLayout.HEADER_SIZE) / RecordLayout.RECORD_SIZE_IN_BYTES;
            int recordExcess = (actualBytesRead - HeaderLayout.HEADER_SIZE) % RecordLayout.RECORD_SIZE_IN_BYTES;
            if (recordExcess > 0) {
              throw new IOException(
                "[" + path + "] likely truncated: (" + actualBytesRead + "b) " +
                " = (" + recordsRead + " whole records) + " + recordExcess + "b excess");
            }
            allocatedRecordsCount.set(recordsRead);
          }
        }
      }

      globalModCount.set((int)HeaderLayout.GLOBAL_MOD_COUNT.getVolatile(records, 0L));
      recordsArena = arena;
      this.recordsMemorySegment = records;
    }
    catch (IOException | RuntimeException | Error e) {
      arena.close();
      throw e;
    }
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
                                   int recordRef) {
    checkValidIdField(recordId, recordRef, "attributeRecordId");
    setIntField(recordId, RecordLayout.ATTR_REF, recordRef);
  }

  @Override
  public int getAttributeRecordId(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.ATTR_REF);
  }

  @Override
  public int getParent(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.PARENT_REF);
  }

  @Override
  public void setParent(int recordId,
                        int parentId) throws IOException {
    checkParentIdIsValid(parentId);
    setIntField(recordId, RecordLayout.PARENT_REF, parentId);
  }

  @Override
  public int getNameId(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.NAME_REF);
  }

  @Override
  public int updateNameId(int recordId,
                          int nameId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(nameId);
    return setIntField(recordId, RecordLayout.NAME_REF, nameId);
  }


  @Override
  public boolean setFlags(int recordId,
                          @PersistentFS.Attributes int newFlags) throws IOException {
    int oldFlags = getIntField(recordId, RecordLayout.FLAGS);
    boolean reallyChanged = (oldFlags != newFlags);
    if (reallyChanged) {
      setIntField(recordId, RecordLayout.FLAGS, newFlags);
    }
    return reallyChanged;
  }

  @Override
  public @PersistentFS.Attributes int getFlags(int recordId) throws IOException {
    //noinspection MagicConstant
    return getIntField(recordId, RecordLayout.FLAGS);
  }

  @Override
  public long getLength(int recordId) throws IOException {
    return getLongField(recordId, RecordLayout.LENGTH);
  }

  @Override
  public boolean setLength(int recordId,
                           long newLength) throws IOException {
    boolean reallyChanged = getLongField(recordId, RecordLayout.LENGTH) != newLength;
    if (reallyChanged) {
      setLongField(recordId, RecordLayout.LENGTH, newLength);
    }
    return reallyChanged;
  }

  @Override
  public long getTimestamp(int recordId) throws IOException {
    return getLongField(recordId, RecordLayout.TIMESTAMP);
  }

  @Override
  public boolean setTimestamp(int recordId,
                              long newTimestamp) throws IOException {
    boolean reallyChanged = getLongField(recordId, RecordLayout.TIMESTAMP) != newTimestamp;
    if (reallyChanged) {
      setLongField(recordId, RecordLayout.TIMESTAMP, newTimestamp);
    }
    return reallyChanged;
  }

  @Override
  public int getModCount(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.MOD_COUNT);
  }

  @Override
  public void markRecordAsModified(int recordId) {
    setIntField(recordId, RecordLayout.MOD_COUNT, globalModCount.incrementAndGet());
  }

  @Override
  public int getContentRecordId(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.CONTENT_REF);
  }

  @Override
  public boolean setContentRecordId(int recordId,
                                    int contentRef) {
    checkValidIdField(recordId, contentRef, "contentRecordId");
    boolean reallyChanged = getIntField(recordId, RecordLayout.CONTENT_REF) != contentRef;
    if (reallyChanged) {
      setIntField(recordId, RecordLayout.CONTENT_REF, contentRef);
    }
    return reallyChanged;
  }

  @Override
  public void cleanRecord(int recordId) {
    long recordStartAtBytes = recordOffsetInBytes(recordId);
    recordsMemorySegment.asSlice(recordStartAtBytes, RecordLayout.LAYOUT.byteSize()).fill((byte)0);
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
    return getLongHeaderField(HeaderLayout.TIMESTAMP);
  }

  @Override
  public boolean wasClosedProperly() throws IOException {
    return true;// 'previous session' make no sense for non-persistent in-memory storage
  }

  @Override
  public int getErrorsAccumulated() {
    return getIntHeaderField(HeaderLayout.ERRORS_ACCUMULATED);
  }

  @Override
  public void setErrorsAccumulated(int errors) {
    setIntHeaderField(HeaderLayout.ERRORS_ACCUMULATED, errors);
    globalModCount.incrementAndGet();
    dirty.compareAndSet(false, true);
  }

  @Override
  public void setVersion(int version) throws IOException {
    setIntHeaderField(HeaderLayout.VERSION, version);
    setLongHeaderField(HeaderLayout.TIMESTAMP, System.currentTimeMillis());
    globalModCount.incrementAndGet();
    dirty.compareAndSet(false, true);
  }

  @Override
  public int getVersion() throws IOException {
    return getIntHeaderField(HeaderLayout.VERSION);
  }

  @Override
  public int getFlags() throws IOException {
    return getIntHeaderField(HeaderLayout.FLAGS);
  }

  @Override
  public boolean updateFlags(int flagsToAdd, int flagsToRemove) {
    int currentFlags = (int)HeaderLayout.FLAGS.getVolatile(recordsMemorySegment, 0L);
    int newFlags = (currentFlags & ~flagsToRemove) | flagsToAdd;
    if (newFlags == currentFlags) {
      return false;
    }
    //MAYBE RC: use CAS to make an update atomic?
    HeaderLayout.FLAGS.setVolatile(recordsMemorySegment, 0L, newFlags);
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
    return (RecordLayout.RECORD_SIZE_IN_BYTES * (long)recordsCount) + HeaderLayout.HEADER_SIZE;
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
      setIntHeaderField(HeaderLayout.GLOBAL_MOD_COUNT, globalModCount.get());

      long actualDataLength = actualDataLength();
      ByteBuffer actualRecordsToStore = recordsMemorySegment.asSlice(0, actualDataLength).asByteBuffer().order(nativeOrder());
      try (SeekableByteChannel channel = Files.newByteChannel(storagePath, WRITE, CREATE)) {
        channel.write(actualRecordsToStore);
      }
      markNotDirty();
    }
  }

  @Override
  public void close() throws IOException {
    if (!recordsArena.scope().isAlive()) {
      return;
    }
    try {
      force();
    }
    finally {
      if (recordsArena.scope().isAlive()) {
        recordsArena.close();
      }
    }
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
    public void setAttributeRecordId(int attributeRecordId) {
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
    public boolean setContentRecordId(int contentRecordId) {
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
                            VarHandle fieldHandle,
                            long fieldValue) {
    long recordOffset = recordOffsetInBytes(recordId);
    fieldHandle.setVolatile(recordsMemorySegment, recordOffset, fieldValue);
    markDirty();
  }

  private long getLongField(int recordId,
                            VarHandle fieldHandle) {
    long recordOffset = recordOffsetInBytes(recordId);
    return (long)fieldHandle.getVolatile(recordsMemorySegment, recordOffset);
  }

  private int setIntField(int recordId,
                          VarHandle fieldHandle,
                          int fieldValue) {
    long recordOffset = recordOffsetInBytes(recordId);
    int previousValue = (int)fieldHandle.getAndSet(recordsMemorySegment, recordOffset, fieldValue);
    markDirty();
    return previousValue;
  }

  private int getIntField(int recordId,
                          VarHandle fieldHandle) {
    long recordOffset = recordOffsetInBytes(recordId);
    return (int)fieldHandle.getVolatile(recordsMemorySegment, recordOffset);
  }

  private long recordOffsetInBytes(int recordId) throws IndexOutOfBoundsException {
    checkRecordId(recordId);
    return (RecordLayout.RECORD_SIZE_IN_BYTES * (long)(recordId - 1)) + HeaderLayout.HEADER_SIZE;
  }

  private void checkRecordId(int recordId) throws IndexOutOfBoundsException {
    if (!isValidFileId(recordId)) {
      int allocatedSoFar = allocatedRecordsCount.get();
      throw new IndexOutOfBoundsException(
        "recordId(=" + recordId + ") is outside of allocated IDs range (0, " + allocatedSoFar + "]");
    }
  }

  private void setLongHeaderField(VarHandle fieldHandle,
                                  long headerValue) {
    fieldHandle.setVolatile(recordsMemorySegment, 0L, headerValue);
    markDirty();
  }

  private long getLongHeaderField(VarHandle fieldHandle) {
    return (long)fieldHandle.getVolatile(recordsMemorySegment, 0L);
  }

  private void setIntHeaderField(VarHandle fieldHandle,
                                 int headerValue) {
    fieldHandle.setVolatile(recordsMemorySegment, 0L, headerValue);
    markDirty();
  }


  private int getIntHeaderField(VarHandle fieldHandle) {
    return (int)fieldHandle.getVolatile(recordsMemorySegment, 0L);
  }

  private void markDirty() {
    dirty.set(true);
  }

  private void markNotDirty() {
    dirty.set(false);
  }
}
