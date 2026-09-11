// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.circular;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.platform.util.io.storages.AlignmentUtils;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.CleanableStorage;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.Unmappable;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.READ;
import static java.util.Objects.requireNonNull;

/**
 * {@linkplain CircularBytesBuffer} implementation over a memory-mapped file ({@link MMappedFileStorage}).
 * <p>
 * <b>Thread-safe</b>, synchronized on a private lock.
 * <p>
 * <b>Durability</b> relies on the usual mmap contract: changes are visible in mapped memory immediately, and
 * {@link #flush()} only asks OS to sync the file to the underlying storage.
 * <p>
 * {@link #read(DataReader)} scans all 'unprocessed' records without consuming them.
 * {@link #readConsuming(DataReader)} scans all 'unprocessed' records -- records accepted by the reader are marked as
 * consumed (=processed).
 */
@ApiStatus.Internal
public final class CircularBytesBufferOverMMappedFile implements CircularBytesBuffer, Closeable, Flushable, Unmappable, CleanableStorage {
  private static final Logger LOG = Logger.getInstance(CircularBytesBufferOverMMappedFile.class);

  private static final ValueLayout.OfInt INT32_VALUE_LAYOUT = ValueLayout.JAVA_INT.withOrder(nativeOrder());
  private static final ValueLayout.OfLong INT64_UNALIGNED_VALUE_LAYOUT = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(nativeOrder());

  ///=======================================================================================================================
  ///Implementation details:
  ///
  ///The queue is described by head and tail -- persisted int64 cursors. They are not physical offsets inside
  /// the data region but monotonically growing logical positions (cursors). The occupied interval is `[head, tail)`,
  /// and the # of occupied bytes is `(tail - head)`. A physical offset == `.floorMod(position, capacity)`.
  ///Because positions are logical, `(headOffset == tailOffset)` is not ambiguous: `(head == tail)` means empty,
  /// while `(tail - head == capacity)` means full.
  ///
  ///Record layout is `[header: int32][payload?][int32-alignment-padding]` (see RecordLayout)
  /// Record offset is always int32-aligned.
  /// The record header contains: a 'type' (=data|padding), 'consumed' flag, and payload length.
  ///'Data' record is always continuous, i.e., can't be split in half: if a data record doesn't fit into the
  /// remaining bytes at the end of the buffer -- a padding record is written to fill the end region, and the
  /// data record is written at physical offset 0.
  ///'Padding' record is used to fill the space that regular record can't fit in: i.e., if we want to store 32
  /// bytes long record but there are only 16 bytes left till the end of the buffer -- we put padding record
  /// (16 bytes), and put data record at the beginning of the buffer, after the wrapping.
  ///
  ///Since records are marked 'consumed' (processed) _individually_, headCursor is not really needed -- we could
  /// always iterate over `[max(0, tail-capacity) .. tail)` region, skipping over already-consumed records. But such
  /// an iteration is quite ineffective, especially if the capacity is big, but most of the records are 'consumed'.
  /// The headCursor is as an optimization: it moves forward over the continuous region (=prefix) of 'consumed' records,
  /// until the first 'not consumed' record (or until the tail is reached) -- so the `[head .. tail)` region is the
  /// only region where 'unconsumed' records could ever be.
  ///
  ///Record 'leases': we don't want the reading to be protected by exclusive lock (for [#append] it is ok), so reading must
  /// happen outside [#lock] -- but:
  /// 1) we must ensure only-once consuming semantics
  /// 2) we must ensure mmapped buffer is not released in [#close] while some reader is still reading it
  /// For that [#activeReadOperations] and [#leasedRecordCursors] were introduced: [#activeReadOperations] ensures that
  /// buffer won't be released until all the readers leave it, while [#leasedRecordCursors] ensures only 1 reader could
  /// access a record at any given moment.
  ///
  ///It seems like 'pure' readers ([DataReader]) -- without consuming semantics -- do not need _exclusive_ record lease.
  /// This is not exactly true, because of 'consumed record shouldn't be available for reading' semantics -- at that
  /// moment the record become 'consumed'? Using the same exclusive lease by both 'pure' and 'consuming' readers solves
  /// this problem, because consuming become 'atomic' then. Without an exclusive lease 'pure' reader could read a record
  /// that is right now being processed by 'consuming' reader -- which could be ok for some specific use-cases, and not
  /// ok for others.
  /// Hence, it was decided to be on a safe side, and use exclusive leases for both 'pure' and 'consuming' readers.
  ///=======================================================================================================================


  private final MMappedFileStorage storage;

  /** bytes available in the 'records' section of the file, i.e., (buffer size - header) */
  private final int capacity;

  /** Was storage properly closed (by invoking {@linkplain #close()}) in a previous session? */
  private final boolean wasClosedProperly;

  private final transient Object lock = new Object();

  /**
   * Logical cursors of records currently read: delivered to a reader outside {@link #lock}.
   * A record could be leased to <=1 reader at any given moment -- other readers must wait {@link #waitForRecordLeaseToRelease(long)}.
   * Guarded by {@link #lock}.
   */
  private final LongSet leasedRecordCursors = new LongOpenHashSet();

  /**
   * Sort-of reference (usages) counter for a mmapped buffer:
   * if (activeReadOperations=0 under the lock) => nobody else accesses a mmapped buffer => the buffer could be released
   * Guarded by {@link #lock}.
   */
  private int activeReadOperations;

  /** Guarded by {@link #lock}. */
  private boolean closing;

  /**
   * We know the steady-state size of the queue from the start => allocate and mmap the full-sized file in one go and cache
   * the one and only page in ctor. Page is null-ed only in {@linkplain #close()}.
   */
  private volatile @Nullable MMappedFileStorage.Page cachedPage;

  public CircularBytesBufferOverMMappedFile(@NotNull MMappedFileStorage storage) throws IOException {
    this.storage = storage;

    int pageSize = storage.pageSize();
    if (!AlignmentUtils.is32bAligned(pageSize)) {
      throw new IllegalArgumentException("storage.pageSize(=" + pageSize + ") must be 32b-aligned");
    }

    boolean fileIsEmpty = (storage.actualFileSize() == 0);
    cachedPage = storage.pageByOffset(0);

    MemorySegment headerSegment = pageSegment();
    if (fileIsEmpty) {
      HeaderLayout.initHeaderFields(headerSegment, pageSize);
    }
    else {
      HeaderLayout.checkFileParamsCompatible(storage.storagePath(), headerSegment, pageSize);
    }

    capacity = HeaderLayout.readCapacity(headerSegment);

    wasClosedProperly = HeaderLayout.markStorageOpened(headerSegment);
    flush(true);//ensure 'storage opened' flag just set -- is persisted
  }

  public Path storagePath() {
    return storage.storagePath();
  }

  public int capacity() {
    return capacity;
  }

  @Override
  public int maxEntrySize() {
    return RecordLayout.maxPayloadSize(capacity);
  }

  public boolean wasClosedProperly() {
    return wasClosedProperly;
  }

  @Override
  public boolean hasUnprocessedRecords() throws IOException {
    synchronized (lock) {
      checkNotClosing();
      return advanceTailOverConsumedRecords(pageSegment());
    }
  }

  @Override
  public void append(@NotNull ByteBufferWriter writer,
                     int payloadSize) throws IOException, QueueFullException {
    RecordLayout.checkPayloadSizeIsValid(payloadSize);

    synchronized (lock) {
      checkNotClosing();
      MemorySegment pageSegment = pageSegment();

      advanceTailOverConsumedRecords(pageSegment);

      int recordLength = RecordLayout.recordLength(payloadSize);
      if (recordLength > capacity) {
        throw new QueueFullException("recordLength(=" + recordLength + ") exceeds buffer.capacity(=" + capacity + ")");
      }

      long head = HeaderLayout.readHeadCursor(pageSegment);
      long tail = HeaderLayout.readTailCursor(pageSegment);
      int used = bytesUsed(head, tail);
      int free = capacity - used;
      int tailOffset = offsetInDataSection(tail);
      int remainingToEnd = capacity - tailOffset;

      // If the record doesn't fit before the physical end of the data region, we need room both for the padding
      // record at the end and for the actual data record at offset 0.
      int required = (recordLength <= remainingToEnd) ? recordLength : remainingToEnd + recordLength;
      if (required > free) {
        throw new QueueFullException(
          "Not enough room in the queue: required=" + required + ", free=" + free + ", recordLength=" + recordLength +
          ", tail=" + tail + ", tailOffset=" + tailOffset +
          ", head=" + head + ", headOffset=" + offsetInDataSection(head)
        );
      }

      if (recordLength > remainingToEnd) {
        RecordLayout.putPaddingRecord(pageSegment, dataOffset(tailOffset), remainingToEnd);
        tail += remainingToEnd;
        tailOffset = 0;
      }

      RecordLayout.putDataRecord(pageSegment, pageBuffer(), dataOffset(tailOffset), payloadSize, writer);
      tail += recordLength;

      HeaderLayout.putTailCursor(pageSegment, tail);
    }
  }

  @Override
  public int readMaybeConsuming(@NotNull OptionallyConsumingDataReader reader) throws IOException {
    long scanCursor;
    long tailSnapshot; //don't count new records possibly added along the way
    synchronized (lock) {
      checkNotClosing();
      activeReadOperations++;
      MemorySegment pageSegment = pageSegment();
      scanCursor = HeaderLayout.readHeadCursor(pageSegment);
      tailSnapshot = HeaderLayout.readTailCursor(pageSegment);
    }

    try {
      int consumedRecords = 0;

      while (true) {
        LeasedRecord leasedRecord;
        synchronized (lock) {
          leasedRecord = fetchAndLeaseNextRecord(scanCursor, tailSnapshot, reader);
        }
        if (leasedRecord == null) {
          return consumedRecords;
        }

        ReadDecision decision = leasedRecord.decision();
        boolean readerCompletedSuccessfully = false;
        try {
          decision.process(leasedRecord.payloadData());
          readerCompletedSuccessfully = true;
        }
        finally {
          synchronized (lock) {
            try {
              MemorySegment pageSegment = pageSegment();
              if (readerCompletedSuccessfully && decision.shouldConsumeAfterProcess()) {
                RecordLayout.markConsumed(pageSegment, leasedRecord.recordOffset(), leasedRecord.header());
                consumedRecords++;
              }
              advanceTailOverConsumedRecords(pageSegment);
            }
            finally {
              leasedRecordCursors.remove(leasedRecord.cursor());
              lock.notifyAll();
            }
          }
        }

        scanCursor = leasedRecord.nextCursor();
      }
    }
    finally {
      synchronized (lock) {
        activeReadOperations--;
        lock.notifyAll();
      }
    }
  }

  @Override
  public void flush() throws IOException {
    flush(MMappedFileStorage.FSYNC_ON_FLUSH_BY_DEFAULT);
  }

  public void flush(boolean fsync) throws IOException {
    if (fsync) {
      storage.fsync();
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (lock) {
      if (storage.isOpen()) {
        closing = true;
        waitForActiveReadOperationsToFinish();
        HeaderLayout.markStorageClosed(pageSegment());
        flush();
        storage.close();
        cachedPage = null;
      }
    }
  }

  @Override
  public void closeAndUnsafelyUnmap() throws IOException {
    close();
    storage.closeAndUnsafelyUnmap();
  }

  @Override
  public void closeAndClean() throws IOException {
    close();
    storage.closeAndClean();
  }

  public boolean isClosed() {
    return !storage.isOpen();
  }

  @Override
  public String toString() {
    Path path = storage.storagePath();
    synchronized (lock) {
      MMappedFileStorage.Page page = cachedPage;
      if (page == null || isClosed()) {
        return "CircularBytesBufferOverMMappedFile[" + path + "]{capacity=" + capacity + ", closed}";
      }

      MemorySegment pageSegment = page.rawPageSegment();
      long head = HeaderLayout.readHeadCursor(pageSegment);
      long tail = HeaderLayout.readTailCursor(pageSegment);
      return "CircularBytesBufferOverMMappedFile[" + path + "]{" +
             "capacity=" + capacity +
             ", head=" + head +
             ", headOffset=" + offsetInDataSection(head) +
             ", tail=" + tail +
             ", tailOffset=" + offsetInDataSection(tail) +
             ", used=" + (tail - head) +
             '}';
    }
  }

  /**
   * Moves tailCursor forward, over the longest continuous region of 'consumed' (processed) records possible.
   * I.e., moves tailCursor forward until the first non-consumed record -- or until it reaches headCursor.
   *
   * @return true if (head != tail) at the end => some unprocessed records remain;
   * false if (head==tail) => no unprocessed records left => queue is empty.
   */
  private boolean advanceTailOverConsumedRecords(@NotNull MemorySegment pageSegment) throws IOException {
    long headCursor = HeaderLayout.readHeadCursor(pageSegment);
    long tailCursor = HeaderLayout.readTailCursor(pageSegment);
    int used = bytesUsed(headCursor, tailCursor);

    // Only a continuous prefix could be released. Consumed records after the first unconsumed one remain
    // inside [head, tail), but read() will skip them
    while (used > 0) {
      int recordOffsetInDataSection = offsetInDataSection(headCursor);
      int recordOffsetInFile = dataOffset(recordOffsetInDataSection);
      int recordHeader = RecordLayout.readHeader(pageSegment, recordOffsetInFile);
      int recordLength = RecordLayout.recordLength(recordHeader, recordOffsetInDataSection, used, storage.storagePath());
      if (RecordLayout.isDataHeader(recordHeader) && !RecordLayout.isConsumed(recordHeader)) {
        break;
      }

      headCursor += recordLength;
      used -= recordLength;
    }

    if (used == 0) {
      HeaderLayout.putHeadCursor(pageSegment, tailCursor);
      return false;
    }
    else {
      HeaderLayout.putHeadCursor(pageSegment, headCursor);
      return true;
    }
  }

  private ByteBuffer pageBuffer() throws ClosedStorageException {
    MMappedFileStorage.Page page = cachedPage;
    if (page == null) {
      throw new ClosedStorageException("Storage[" + storage.storagePath() + "] is already closed");
    }
    return page.rawPageBuffer();
  }

  private MemorySegment pageSegment() throws ClosedStorageException {
    MMappedFileStorage.Page page = cachedPage;
    if (page == null) {
      throw new ClosedStorageException("Storage[" + storage.storagePath() + "] is already closed");
    }
    return page.rawPageSegment();
  }

  ///must be called under .lock
  private void checkNotClosing() throws ClosedStorageException {
    if (closing) {
      throw new ClosedStorageException("Storage[" + storage.storagePath() + "] is closing");
    }
  }

  private void waitForActiveReadOperationsToFinish() {
    boolean interrupted = false;
    while (activeReadOperations > 0) {
      try {
        lock.wait();
      }
      catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  /// Finds the next record selected by the reader in [fromCursor, tailSnapshot), leases it, and returns a [LeasedRecord].
  /// Returned null means the iteration should be stopped.
  /// Must be called under [#lock] by an active read operation.
  private @Nullable LeasedRecord fetchAndLeaseNextRecord(long fromCursor,
                                                         long tailSnapshot,
                                                         @NotNull OptionallyConsumingDataReader reader) throws IOException {
    MemorySegment pageSegment = pageSegment();
    ByteBuffer pageBuffer = pageBuffer();
    long cursor = Math.max(fromCursor, HeaderLayout.readHeadCursor(pageSegment));
    while (cursor < tailSnapshot) {
      int bytesLeft = bytesUsed(cursor, tailSnapshot);
      int offset = offsetInDataSection(cursor);
      int recordOffset = dataOffset(offset);
      int header = RecordLayout.readHeader(pageSegment, recordOffset);
      int recordLength = RecordLayout.recordLength(header, offset, bytesLeft, storage.storagePath());

      if (!RecordLayout.isDataHeader(header) || RecordLayout.isConsumed(header)) {
        cursor += recordLength;
        continue;
      }

      ByteBuffer payloadData = payloadData(pageBuffer, recordOffset, header);
      //MAYBE RC: calling decide() under the .lock is risky for scalability -- and also not logically required.
      //          Moving it outside would require preventing the record bytes from being consumed and reused between
      //          the header check and the decision callback -- which could be done, but a separate task by itself.
      //          So, for now we just expect decide() to be very fast (~just few memory reads)
      ReadDecision decision = reader.decide(payloadData);
      requireNonNull(decision, "reader.decide() must not return null");
      if (decision.shouldStop()) {
        return null;
      }
      if (!decision.shouldProcess()) {
        cursor += recordLength;
        continue;
      }

      if (leasedRecordCursors.contains(cursor)) {
        waitForRecordLeaseToRelease(cursor);

        //.lock is released during waiting, so re-get & re-check the crucial bits of state:
        pageSegment = pageSegment();
        pageBuffer = pageBuffer();
        long headCursor = HeaderLayout.readHeadCursor(pageSegment);
        if (cursor < headCursor) {
          cursor = headCursor;
          continue;
        }

        // Only 'consumed' bit can change during record lifetime, record type and length are immutable
        // (as long, as record is not overwritten -- which is checked above)
        header = RecordLayout.readHeader(pageSegment, recordOffset);
        if (RecordLayout.isConsumed(header)) {
          cursor += recordLength;
          continue;
        }
      }

      resetPayloadData(payloadData, pageBuffer);
      leasedRecordCursors.add(cursor);
      return new LeasedRecord(cursor, cursor + recordLength, recordOffset, header, payloadData, decision);
    }

    return null;
  }

  /** Creates a read-only payload view for the record at recordOffset */
  private static @NotNull ByteBuffer payloadData(@NotNull ByteBuffer pageBuffer,
                                                 int recordOffset,
                                                 int header) throws CorruptedException {
    int payloadLength = RecordLayout.payloadLength(header);
    return pageBuffer
      .slice(recordOffset + RecordLayout.PAYLOAD_OFFSET, payloadLength)
      .asReadOnlyBuffer()
      .order(pageBuffer.order());
  }

  /** Restores the payload view before passing the same view to the processing phase. */
  private static void resetPayloadData(@NotNull ByteBuffer payloadData, @NotNull ByteBuffer pageBuffer) {
    payloadData.clear();
    payloadData.order(pageBuffer.order());
  }

  private void waitForRecordLeaseToRelease(long cursor) throws ClosedStorageException {
    boolean interrupted = false;
    while (leasedRecordCursors.contains(cursor)) {
      try {
        lock.wait();
      }
      catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
      throw new ClosedStorageException("Interrupted while waiting for a record lease in Storage[" + storage.storagePath() + "]");
    }
  }

  private static int dataOffset(int offsetInDataSection) {
    return HeaderLayout.HEADER_SIZE + offsetInDataSection;
  }

  /** @return physical offset in the data region for a logical position. */
  private int offsetInDataSection(long position) {
    return Math.floorMod(position, capacity);
  }

  /** @return occupied bytes in the logical interval [head, tail). */
  private int bytesUsed(long headCursor,
                        long tailCursor) throws IOException {
    long used = tailCursor - headCursor;
    if (used < 0 || used > capacity) {
      throw new CorruptedException("[" + storage.storagePath() + "] is corrupted: head=" + headCursor + ", tail=" + tailCursor +
                                   ", used=" + used + ", capacity=" + capacity);
    }
    return (int)used;
  }

  private record LeasedRecord(long cursor,
                              long nextCursor,
                              int recordOffset,
                              int header,
                              @NotNull ByteBuffer payloadData,
                              @NotNull ReadDecision decision) {
  }

  public static final class Factory implements StorageFactory<CircularBytesBufferOverMMappedFile> {
    private final int requestedCapacity;
    private final boolean cleanFileIfIncompatible;

    private Factory(int requestedCapacity,
                    boolean cleanFileIfIncompatible) {
      if (requestedCapacity <= 0) {
        throw new IllegalArgumentException("requestedCapacity(=" + requestedCapacity + ") must be positive");
      }
      this.requestedCapacity = requestedCapacity;
      this.cleanFileIfIncompatible = cleanFileIfIncompatible;
    }

    @Override
    public @NotNull CircularBytesBufferOverMMappedFile open(@NotNull Path storagePath) throws IOException {
      int pageSize = roundUpToPowerOf2(HeaderLayout.HEADER_SIZE + requestedCapacity);

      if (Files.exists(storagePath) && Files.size(storagePath) > 0) {
        //Avoid mmap at first: unmap could be tricky on JVM across the platforms, so better check the params first
        // with a non-mmapped segment and only proceed if +/- sure params are correct:
        try (var arena = Arena.ofConfined();
             FileChannel channel = FileChannel.open(storagePath, READ)) {
          MemorySegment headerSegment = arena.allocate(HeaderLayout.LAYOUT);
          int bytesRead = channel.read(headerSegment.asByteBuffer());
          if (bytesRead != HeaderLayout.HEADER_SIZE) {
            throw new IOException("[" + storagePath + "]: file is not empty, but < HEADER_SIZE(=" + HeaderLayout.HEADER_SIZE + ")");
          }
          HeaderLayout.checkFileParamsCompatible(storagePath, headerSegment, pageSize);
        }
        catch (IOException ex) {
          if (!cleanFileIfIncompatible) {
            throw ex;
          }

          LOG.warn("[" + storagePath + "] storage params are incompatible [" + ex.getMessage() + "] -> re-create the storage from 0", ex);
          NioFiles.deleteRecursively(storagePath);
        }
      }

      return MMappedFileStorageFactory.withDefaults()
        .pageSize(pageSize)
        .compose(mappedFileStorage -> new CircularBytesBufferOverMMappedFile(mappedFileStorage))
        .open(storagePath);
    }

    @SuppressWarnings("unused")
    public Factory cleanIfFileIncompatible() {
      return new Factory(requestedCapacity, true);
    }

    /**
     * BEWARE: file size on disk could be up to 2x larger!
     * Use {@linkplain #withFileSizeNoMoreThan(int)} if you want to limit the file size
     */
    public static Factory withCapacityAtLeast(int capacity) {
      if (capacity <= 0) {
        throw new IllegalArgumentException("capacity(=" + capacity + ") must be positive");
      }
      return new Factory(capacity, false);
    }

    /** Configures the capacity such that on-disk file size is <= maxFileSize */
    public static Factory withFileSizeNoMoreThan(int maxFileSize) {
      int capacity = capacityByMaxFileSize(maxFileSize);
      return new Factory(capacity, false);
    }

    /** @return max capacity for the buffer such that the file on disk will be <= maxFileSize */
    public static int capacityByMaxFileSize(int maxFileSize) {
      if (maxFileSize <= HeaderLayout.HEADER_SIZE) {
        throw new IllegalArgumentException("maxFileSize(=" + maxFileSize + ") must be > headerSize(=" + HeaderLayout.HEADER_SIZE + ")");
      }
      if (roundUpToPowerOf2(maxFileSize) == maxFileSize) {
        //maxFileSize is already a 2^N:
        return maxFileSize - HeaderLayout.HEADER_SIZE;
      }

      return roundUpToPowerOf2(maxFileSize) / 2 - HeaderLayout.HEADER_SIZE;
    }

    private static int roundUpToPowerOf2(int value) {
      if (value <= 0) {
        throw new IllegalArgumentException("value(=" + value + ") must be positive");
      }
      if (value > (1 << 30)) {
        throw new IllegalArgumentException("value(=" + value + ") is too large");
      }
      return value == 1 ? 1 : 1 << (Integer.SIZE - Integer.numberOfLeadingZeros(value - 1));
    }
  }

  private static final class HeaderLayout {

    private static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
      INT32_VALUE_LAYOUT.withName("magicWord"),
      INT32_VALUE_LAYOUT.withName("implementationVersion"),
      INT32_VALUE_LAYOUT.withName("pageSize"),
      INT64_UNALIGNED_VALUE_LAYOUT.withName("headCursor"),
      INT64_UNALIGNED_VALUE_LAYOUT.withName("tailCursor"),
      INT32_VALUE_LAYOUT.withName("flags"),
      MemoryLayout.paddingLayout(32)
    ).withName("CircularBytesBuffer.HeaderLayout")
     .withByteAlignment(Integer.BYTES);

    /** First header int32. It identifies this storage file type. */
    private static final PathElement MAGIC_WORD_FIELD = groupElement("magicWord");
    private static final PathElement IMPLEMENTATION_VERSION_FIELD = groupElement("implementationVersion");
    /**
     * The data region wraps at {@code pageSize - HEADER_SIZE}.
     * The page size is part of the binary layout and must match when the storage opens again.
     */
    private static final PathElement PAGE_SIZE_FIELD = groupElement("pageSize");
    /** Logical position of the first occupied record. The physical offset is {@code headCursor % capacity}. */
    private static final PathElement HEAD_CURSOR_FIELD = groupElement("headCursor");
    /** Logical position after the last occupied record. The physical offset is {@code tailCursor % capacity}. */
    private static final PathElement TAIL_CURSOR_FIELD = groupElement("tailCursor");
    /** The flags field currently stores only the closed-properly flag. */
    private static final PathElement FLAGS_FIELD = groupElement("flags");

    private static VarHandle fieldHandle(PathElement fieldPath) {
      return LAYOUT.varHandle(fieldPath).withInvokeExactBehavior();
    }

    private static final VarHandle MAGIC_WORD = fieldHandle(MAGIC_WORD_FIELD);
    private static final VarHandle IMPLEMENTATION_VERSION = fieldHandle(IMPLEMENTATION_VERSION_FIELD);
    private static final VarHandle PAGE_SIZE = fieldHandle(PAGE_SIZE_FIELD);
    private static final VarHandle HEAD_CURSOR = fieldHandle(HEAD_CURSOR_FIELD);
    private static final VarHandle TAIL_CURSOR = fieldHandle(TAIL_CURSOR_FIELD);
    private static final VarHandle FLAGS = fieldHandle(FLAGS_FIELD);

    private static final int FILE_MAGIC_WORD = IOUtil.asciiToMagicWord("CBBQ");

    private static final int CURRENT_IMPLEMENTATION_VERSION = 1;

    private static final int FLAG_CLOSED_PROPERLY_MASK = 0b1;

    /**
     * The 64-byte header leaves space for future flags and counters.
     * The current fields use only half of this space.
     */
    private static final int HEADER_SIZE = Math.toIntExact(LAYOUT.byteSize());

    private HeaderLayout() { }

    private static void initHeaderFields(@NotNull MemorySegment headerSegment,
                                         int pageSize) {
      int capacity = pageSize - HEADER_SIZE;
      if (capacity <= RecordLayout.HEADER_SIZE) {
        throw new IllegalArgumentException("pageSize(=" + pageSize + ") leaves too small capacity(=" + capacity + ")");
      }
      if (!AlignmentUtils.is32bAligned(capacity)) {
        throw new IllegalArgumentException("capacity(=" + capacity + ") must be 32b-aligned");
      }

      MAGIC_WORD.set(headerSegment, 0L, FILE_MAGIC_WORD);
      IMPLEMENTATION_VERSION.set(headerSegment, 0L, CURRENT_IMPLEMENTATION_VERSION);
      PAGE_SIZE.set(headerSegment, 0L, pageSize);
      HEAD_CURSOR.set(headerSegment, 0L, 0L);
      TAIL_CURSOR.set(headerSegment, 0L, 0L);
      FLAGS.set(headerSegment, 0L, FLAG_CLOSED_PROPERLY_MASK);
    }

    private static void checkFileParamsCompatible(@NotNull Path storagePath,
                                                  @NotNull MemorySegment headerSegment,
                                                  int pageSize) throws IOException {
      int magicWord = (int)MAGIC_WORD.get(headerSegment, 0L);
      if (magicWord != FILE_MAGIC_WORD) {
        throw new IOException(
          "[" + storagePath + "] is of incorrect type: " +
          ".magicWord(=" + magicWord + ", '" + IOUtil.magicWordToASCII(magicWord) + "') " +
          "!= expected(" + FILE_MAGIC_WORD + ", '" + IOUtil.magicWordToASCII(FILE_MAGIC_WORD) + "')"
        );
      }

      int implementationVersion = (int)IMPLEMENTATION_VERSION.get(headerSegment, 0L);
      if (implementationVersion != CURRENT_IMPLEMENTATION_VERSION) {
        throw new IOException(
          "[" + storagePath + "].implementationVersion(=" + implementationVersion + ") is not supported: " +
          CURRENT_IMPLEMENTATION_VERSION + " is the currently supported version"
        );
      }

      int filePageSize = (int)PAGE_SIZE.get(headerSegment, 0L);
      if (filePageSize != pageSize) {
        throw new IOException("[" + storagePath + "]: file created with pageSize=" + filePageSize + " but current pageSize=" + pageSize);
      }

      int capacity = readCapacity(headerSegment);
      long head = readHeadCursor(headerSegment);
      long tail = readTailCursor(headerSegment);
      if (head < 0 || tail < 0) {
        throw new CorruptedException("[" + storagePath + "] is corrupted: both head(=" + head + ") and tail(=" + tail + ")" +
                                     " must not be negative");
      }

      long occupiedCapacity = tail - head;
      if (occupiedCapacity < 0 || occupiedCapacity > capacity) {
        throw new CorruptedException("[" + storagePath + "] is corrupted: head(=" + head + "), tail(=" + tail + "), " +
                                     "occupied(=" + occupiedCapacity + ") > capacity(=" + capacity + ")");
      }
    }

    private static int readPageSize(@NotNull MemorySegment headerSegment) {
      return (int)PAGE_SIZE.get(headerSegment, 0L);
    }

    static int readCapacity(@NotNull MemorySegment headerSegment) {
      return readPageSize(headerSegment) - HEADER_SIZE;
    }

    private static long readHeadCursor(@NotNull MemorySegment headerSegment) {
      return (long)HEAD_CURSOR.get(headerSegment, 0L);
    }

    private static void putHeadCursor(@NotNull MemorySegment headerSegment,
                                      long cursor) {
      HEAD_CURSOR.set(headerSegment, 0L, cursor);
    }

    private static long readTailCursor(@NotNull MemorySegment headerSegment) {
      return (long)TAIL_CURSOR.get(headerSegment, 0L);
    }

    private static void putTailCursor(@NotNull MemorySegment headerSegment,
                                      long cursor) {
      TAIL_CURSOR.set(headerSegment, 0L, cursor);
    }

    /** @return was storage closed properly before? */
    private static boolean markStorageOpened(@NotNull MemorySegment headerSegment) {
      int flags = (int)FLAGS.get(headerSegment, 0L);
      boolean wasClosedProperly = (flags & FLAG_CLOSED_PROPERLY_MASK) != 0;
      FLAGS.set(headerSegment, 0L, flags & ~FLAG_CLOSED_PROPERLY_MASK);
      return wasClosedProperly;
    }

    private static void markStorageClosed(@NotNull MemorySegment headerSegment) {
      int flags = (int)FLAGS.get(headerSegment, 0L);
      FLAGS.set(headerSegment, 0L, flags | FLAG_CLOSED_PROPERLY_MASK);
    }
  }

  private static final class RecordLayout {
    private static final ValueLayout.OfInt LAYOUT = INT32_VALUE_LAYOUT;

    private static final VarHandle HEADER = LAYOUT.varHandle().withInvokeExactBehavior();

    // Record = (int32 header) + (payload) + (implicit alignment padding)?
    // Header = 32 bit, at 32-bit-aligned offset:
    //          bit[31] (highest bit): record type, 0=data record, 1=padding record
    //          bit[30]: 'consumed' flag, 0=not consumed, 1=consumed
    //          bits[0..29]: 'raw' record length in bytes (without alignment padding)
    // For data record, 'raw' length is (header + payload), without implicit alignment padding. The real occupied
    // length is roundUpToInt32(rawLength), and the payload length is (rawLength-HEADER_SIZE).
    // For padding record, the raw length is the whole record length, with alignment already included (padding-record
    // has no payload => no need to separate '(aligned) record length' from '(unaligned) payload length').

    private static final int HEADER_SIZE = Math.toIntExact(LAYOUT.byteSize());
    private static final int PAYLOAD_OFFSET = HEADER_SIZE;

    //@formatter:off
    private static final int RECORD_TYPE_MASK     = 0b10000000_00000000_00000000_00000000;
    private static final int RECORD_TYPE_DATA     = 0;
    private static final int RECORD_TYPE_PADDING  = 0b10000000_00000000_00000000_00000000;

    private static final int RECORD_CONSUMED_MASK = 0b01000000_00000000_00000000_00000000;

    private static final int RECORD_LENGTH_MASK   = 0b00111111_11111111_11111111_11111111;

    /** max total record length (header+payload)  */
    private static final int MAX_RECORD_LENGTH    = RECORD_LENGTH_MASK;
    private static final int MAX_PAYLOAD_SIZE     = MAX_RECORD_LENGTH - HEADER_SIZE;
    //@formatter:on

    private RecordLayout() { }

    private static void putDataRecord(@NotNull MemorySegment pageSegment,
                                      @NotNull ByteBuffer pageBuffer,
                                      int recordOffset,
                                      int payloadSize,
                                      @NotNull ByteBufferWriter writer) throws IOException {
      // Write the payload first, _then_ publish the header. Header==0 means "no valid record here" for corruption checks,
      // so publishing it last avoids exposing a valid header with not-yet-written payload during normal append.
      ByteBuffer payloadBuffer = pageBuffer.slice(recordOffset + PAYLOAD_OFFSET, payloadSize).order(pageBuffer.order());
      writer.write(payloadBuffer);
      if (payloadBuffer.remaining() > 0) {
        throw new IllegalStateException(
          "writer must fill up all " + payloadSize + " bytes in the buffer, but it doesn't: " +
          "buffer[pos: " + payloadBuffer.position() + ", lim: " + payloadBuffer.limit() + "]"
        );
      }
      HEADER.set(pageSegment, (long)recordOffset, dataRecordHeader(payloadSize));
    }

    private static void putPaddingRecord(@NotNull MemorySegment pageSegment,
                                         int recordOffset,
                                         int paddingLength) {
      if (paddingLength <= 0) {//TODO RC: check paddingLength <= RECORD_LENGTH_MASK too
        throw new IllegalArgumentException("paddingLength(=" + paddingLength + ") must be >0");
      }
      if (!AlignmentUtils.is32bAligned(paddingLength)) {
        throw new IllegalArgumentException("paddingLength(=" + paddingLength + ") must be 32b-aligned");
      }
      // Padding record is marked as 'consumed' right from the start: it exists only to skip over it.
      HEADER.set(pageSegment, (long)recordOffset, RECORD_TYPE_PADDING | RECORD_CONSUMED_MASK | paddingLength);
    }

    private static int readHeader(@NotNull MemorySegment pageSegment,
                                  int recordOffset) {
      return (int)HEADER.get(pageSegment, (long)recordOffset);
    }

    private static int rawRecordLength(int header) {
      return header & RECORD_LENGTH_MASK;
    }

    private static int payloadLength(int header) throws CorruptedException {
      int rawRecordLength = rawRecordLength(header);
      if (rawRecordLength < PAYLOAD_OFFSET) {
        throw new CorruptedException("Data record rawLength(=" + rawRecordLength + ") is smaller than header size");
      }
      return rawRecordLength - PAYLOAD_OFFSET;
    }

    private static int recordLength(int payloadLength) {
      return AlignmentUtils.roundUpToInt32(PAYLOAD_OFFSET + payloadLength);
    }

    private static int maxPayloadSize(int capacity) {
      return Math.min(capacity - HEADER_SIZE, MAX_PAYLOAD_SIZE);
    }

    /**
     * Extracts record length (full, including alignment padding, if any) from the record header
     *
     * @param storagePathForDebug used only to format error messages
     */
    private static int recordLength(int header,
                                    int offsetInDataSection,
                                    int bytesLeft,
                                    @NotNull Path storagePathForDebug) throws IOException {
      // We should only be called for bytes inside [head, tail). A zero header there means either a torn/corrupted header or
      // an incorrect queue interval in the persisted header.
      if (header == 0) {
        throw new CorruptedException("[" + storagePathForDebug + "] is corrupted: zero record header at data offset " +
                                     offsetInDataSection + ", bytesLeft=" + bytesLeft);
      }

      int rawRecordLength = rawRecordLength(header);
      int length = isPaddingHeader(header) ? rawRecordLength : AlignmentUtils.roundUpToInt32(rawRecordLength);
      if (length <= 0 || length > bytesLeft) {
        throw new CorruptedException("[" + storagePathForDebug + "] is corrupted: recordLength(=" + length + ") at data offset " +
                                     offsetInDataSection + " is outside remaining bytes " + bytesLeft);
      }
      if (isDataHeader(header) && rawRecordLength < PAYLOAD_OFFSET) {
        throw new CorruptedException("[" + storagePathForDebug + "] is corrupted: data record rawLength(=" + rawRecordLength + ") " +
                                     "is smaller than header size at data offset " + offsetInDataSection);
      }
      if (!AlignmentUtils.is32bAligned(length)) {
        throw new CorruptedException("[" + storagePathForDebug + "] is corrupted: recordLength(=" + length + ") is not 32b-aligned");
      }
      return length;
    }

    private static boolean isDataHeader(int header) {
      return (header & RECORD_TYPE_MASK) == RECORD_TYPE_DATA;
    }

    private static boolean isPaddingHeader(int header) {
      return (header & RECORD_TYPE_MASK) == RECORD_TYPE_PADDING;
    }

    private static boolean isConsumed(int header) {
      return (header & RECORD_CONSUMED_MASK) != 0;
    }

    private static void markConsumed(@NotNull MemorySegment pageSegment,
                                     int recordOffset,
                                     int header) {
      HEADER.set(pageSegment, (long)recordOffset, header | RECORD_CONSUMED_MASK);
    }

    /** @return header (int32) for the data record (not 'consumed') with payloadSize */
    private static int dataRecordHeader(int payloadSize) {
      checkPayloadSizeIsValid(payloadSize);

      int totalRecordLength = HEADER_SIZE + payloadSize;
      if ((totalRecordLength & ~RECORD_LENGTH_MASK) != 0) {
        //checkPayloadSizeIsValid() should already cover that, which is why it is a 'code bug':
        throw new AssertionError("Code bug: payloadSize(=" + payloadSize + ") is outside valid range [0, " + MAX_PAYLOAD_SIZE + "]");
      }

      //2 highest bits are 0
      return totalRecordLength;
    }

    private static void checkPayloadSizeIsValid(int payloadSize) {
      if (payloadSize < 0 || MAX_PAYLOAD_SIZE < payloadSize) {
        throw new IllegalArgumentException("payloadSize(=" + payloadSize + ") must be in [0, " + MAX_PAYLOAD_SIZE + "]");
      }
    }
  }
}
