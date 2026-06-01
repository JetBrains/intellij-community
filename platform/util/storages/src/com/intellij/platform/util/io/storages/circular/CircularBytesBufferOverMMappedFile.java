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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.READ;

/**
 * {@linkplain CircularBytesBuffer} implementation over a memory-mapped file ({@link MMappedFileStorage}).
 * <p>
 * <b>Thread-safe</b>, synchronized on a private lock.
 * <p>
 * <b>Durability</b> relies on the usual mmap contract: changes are visible in mapped memory immediately, and
 * {@link #flush()} only asks OS to sync the file to the underlying storage.
 * <p>
 * {@link #read(DataReader)} scans all 'unprocessed' records -- records accepted by the reader are marked as consumed (=processed).
 */
@ApiStatus.Internal
public final class CircularBytesBufferOverMMappedFile implements CircularBytesBuffer, Closeable, Flushable, Unmappable, CleanableStorage {
  private static final Logger LOG = Logger.getInstance(CircularBytesBufferOverMMappedFile.class);

  //=======================================================================================================================
  //Implementation details:
  //
  //The queue is described by head and tail -- persisted int64 cursors. They are not physical offsets inside
  // the data region, but monotonically growing logical positions (cursors). The occupied byte interval is
  // [head, tail), and the # of occupied bytes is (tail - head). A physical offset == .floorMod(position, capacity).
  //Because positions are logical, (headOffset == tailOffset) is not ambiguous: (head == tail) means empty,
  // while (tail - head == capacity) means full.
  //
  //Records layout is `[header: int32][payload?][int32-alignment-padding]` (see RecordLayout)
  // Record offset is always int32-aligned.
  // The record header contains a 'type' (=regular|padding), 'consumed' flag, and payload length.
  //'Data' record is always continuous, i.e., can't be split in half: if a data record doesn't fit into the
  // remaining bytes at the end of the data region, a padding record is written there, and the data record
  // is written at physical offset 0.
  //'Padding' record is used to fill the space that regular record can't fit in: i.e. if we want to store
  // 32 bytes long record but there is only 16 bytes left till the end of the buffer -- we put padding record
  // (16 bytes), and put data record at the beginning of the buffer, after the wrapping.
  //
  //Since records are marked 'consumed' (processed) _individually_, headCursor is not really needed -- we could
  // always iterate over [max(0, tail-capacity) .. tail) region, skipping over already-consumed records. But such
  // iteration could be quite ineffective, especially if the capacity is big, but most of the the records are
  // 'consumed'. The headCursor is introduced as an optimization: it moves forward over continuous region (prefix)
  // of 'consumed' records, until the first 'not consumed' record (or until the tail is reached) -- so the
  // [head .. tail) region is the only region there 'unconsumed' records could ever be.
  //
  // TODO RC: Locking is excessive now -- actually, it is enough to protect with lock only the head/tail cursor movements,
  //          while everything else could be done lock-free, with some amount of volatiles. But it is not very useful
  //          now, since in the only current use-case this class is used under the (FilePageCache.pageAllocationLock) anyway.
  //=======================================================================================================================


  private final MMappedFileStorage storage;

  /** bytes available in the 'records' section of the file, i.e., (buffer size - header) */
  private final int capacity;

  /** Was storage properly closed (by invoking {@linkplain #close()}) in a previous session? */
  private final boolean wasClosedProperly;

  private final Object lock = new Object();

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

    ByteBuffer headerBuffer = pageBuffer();
    if (fileIsEmpty) {
      HeaderLayout.initHeaderFields(headerBuffer, pageSize);
    }
    else {
      HeaderLayout.checkFileParamsCompatible(storage.storagePath(), headerBuffer, pageSize);
    }

    capacity = HeaderLayout.readCapacity(headerBuffer);

    wasClosedProperly = HeaderLayout.markStorageOpened(headerBuffer);
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
      ByteBuffer pageBuffer = pageBuffer();
      return advanceTailOverConsumedRecords(pageBuffer);
    }
  }

  @Override
  public void append(@NotNull ByteBufferWriter writer,
                     int payloadSize) throws IOException, QueueFullException {
    RecordLayout.checkPayloadSizeIsValid(payloadSize);

    synchronized (lock) {
      ByteBuffer pageBuffer = pageBuffer();
      advanceTailOverConsumedRecords(pageBuffer);

      int recordLength = RecordLayout.recordLength(payloadSize);
      if (recordLength > capacity) {
        throw new QueueFullException("recordLength(=" + recordLength + ") exceeds buffer.capacity(=" + capacity + ")");
      }

      long head = HeaderLayout.readHeadCursor(pageBuffer);
      long tail = HeaderLayout.readTailCursor(pageBuffer);
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
        RecordLayout.putPaddingRecord(pageBuffer, dataOffset(tailOffset), remainingToEnd);
        tail += remainingToEnd;
        tailOffset = 0;
      }

      RecordLayout.putDataRecord(pageBuffer, dataOffset(tailOffset), payloadSize, writer);
      tail += recordLength;

      HeaderLayout.putHeadCursor(pageBuffer, head);
      HeaderLayout.putTailCursor(pageBuffer, tail);
    }
  }

  //TODO RC: locking is not very convenient now -- the .lock is acquired for all the duration of .read(), including
  //         reader.read() invocation. The locking around reader.read() is required for 'once-and-only-once' semantic
  //         of record 'consumed' status -- but reader.read() could (and will) involve IO in many use-cases, which makes
  //         locked window quite long, preventing new records to be added to the WAL.
  //         The simplest way to deal with it is to add .read(reader, maxRecord) param -- use-site may call
  //         .read(reader, maxRecord=1-2-3) to periodically release the lock, and allow new records to be inserted.
  //         The better approach would be to separate locks: one lock for head/tail cursors management, and another
  //         lock protecting record.consumed status. 'Cursors' lock acquisitions could be made very short, with only fast
  //         cursors manipulation under it. 'Records' lock is acquired for reader.read(), so it could be quite long, but
  //         it doesn't prevent adding new records, and it could be made segmented (by recordOffset) to reduce contention
  //         even around records processing
  @Override
  public int read(@NotNull DataReader reader) throws IOException {
    synchronized (lock) {
      ByteBuffer pageBuffer = pageBuffer();
      int consumedRecords = 0;
      long headCursor = HeaderLayout.readHeadCursor(pageBuffer);
      long tailCursor = HeaderLayout.readTailCursor(pageBuffer);
      int bytesLeft = bytesUsed(headCursor, tailCursor);

      // Iterate only over the interval [head, tail): it could be no unconsumed records outside this interval
      while (bytesLeft > 0) {
        int offset = offsetInDataSection(headCursor);
        int recordOffset = dataOffset(offset);
        int header = RecordLayout.readHeader(pageBuffer, recordOffset);
        int recordLength = RecordLayout.recordLength(header, offset, bytesLeft, storage.storagePath());

        if (RecordLayout.isDataHeader(header) && !RecordLayout.isConsumed(header)) {
          int payloadLength = RecordLayout.payloadLength(header);
          ByteBuffer payloadData = pageBuffer
            .slice(recordOffset + RecordLayout.PAYLOAD_OFFSET, payloadLength)
            .order(pageBuffer.order());
          boolean successfullyConsumed = reader.read(payloadData);
          if (successfullyConsumed) {
            RecordLayout.markConsumed(pageBuffer, recordOffset, header);
            consumedRecords++;
          }
        }

        headCursor += recordLength;
        bytesLeft -= recordLength;
      }

      advanceTailOverConsumedRecords(pageBuffer);
      return consumedRecords;
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
        HeaderLayout.markStorageClosed(pageBuffer());
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

      ByteBuffer pageBuffer = page.rawPageBuffer();
      long head = HeaderLayout.readHeadCursor(pageBuffer);
      long tail = HeaderLayout.readTailCursor(pageBuffer);
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
   * @return true if (head != tail) at the end == some unprocessed records remain;
   * false if (head==tail) == no unprocessed records left == queue is empty.
   */
  private boolean advanceTailOverConsumedRecords(@NotNull ByteBuffer pageBuffer) throws IOException {
    long headCursor = HeaderLayout.readHeadCursor(pageBuffer);
    long tailCursor = HeaderLayout.readTailCursor(pageBuffer);
    int used = bytesUsed(headCursor, tailCursor);

    // Only a continuous prefix could be released. Consumed records after the first unconsumed one remain
    // inside [head, tail), but read() will skip them by the consumed bit.
    while (used > 0) {
      int recordOffsetInDataSection = offsetInDataSection(headCursor);
      int recordOffsetInFile = dataOffset(recordOffsetInDataSection);
      int recordHeader = RecordLayout.readHeader(pageBuffer, recordOffsetInFile);
      int recordLength = RecordLayout.recordLength(recordHeader, recordOffsetInDataSection, used, storage.storagePath());
      if (RecordLayout.isDataHeader(recordHeader) && !RecordLayout.isConsumed(recordHeader)) {
        break;
      }

      headCursor += recordLength;
      used -= recordLength;
    }

    if (used == 0) {
      HeaderLayout.putHeadCursor(pageBuffer, tailCursor);
      return false;
    }
    else {
      HeaderLayout.putHeadCursor(pageBuffer, headCursor);
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

  private static int dataOffset(int offsetInDataSection) {
    return HeaderLayout.HEADER_SIZE + offsetInDataSection;
  }

  /** @return physical offset in the data region for a logical position. */
  private int offsetInDataSection(long position) {
    return Math.floorMod(position, capacity);
  }

  /**
   * @return occupied bytes in the logical interval [head, tail).
   */
  private int bytesUsed(long headCursor,
                        long tailCursor) throws IOException {
    long used = tailCursor - headCursor;
    if (used < 0 || used > capacity) {
      throw new CorruptedException("[" + storage.storagePath() + "] is corrupted: head=" + headCursor + ", tail=" + tailCursor +
                                   ", used=" + used + ", capacity=" + capacity);
    }
    return (int)used;
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
        // with non-mmapped buffer and only proceed if +/- sure params are correct:
        ByteBuffer headerBuffer = ByteBuffer.allocateDirect(HeaderLayout.HEADER_SIZE).order(nativeOrder());
        try (FileChannel channel = FileChannel.open(storagePath, READ)) {
          int bytesRead = channel.read(headerBuffer);
          if (bytesRead != HeaderLayout.HEADER_SIZE) {
            throw new IOException("[" + storagePath + "]: file is not empty, but < HEADER_SIZE(=" + HeaderLayout.HEADER_SIZE + ")");
          }
          HeaderLayout.checkFileParamsCompatible(storagePath, headerBuffer, pageSize);
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

    /** First header int32, used to recognize this storage's file type. */
    private static final int MAGIC_WORD = IOUtil.asciiToMagicWord("CBBQ");

    private static final int CURRENT_IMPLEMENTATION_VERSION = 1;

    private static final int FLAG_CLOSED_PROPERLY_MASK = 0b1;

    //========= Offsets:

    private static final int MAGIC_WORD_OFFSET = 0;
    private static final int IMPL_VERSION_OFFSET = MAGIC_WORD_OFFSET + Integer.BYTES;

    /**
     * The data region wraps at capacity=pageSize-HEADER_SIZE.
     * Since pageSize _defines_ that capacity, it is a part of the binary layout and must match on reopen.
     */
    private static final int PAGE_SIZE_OFFSET = IMPL_VERSION_OFFSET + Integer.BYTES;
    /** Logical position of the first occupied record. Physical offset is (headCursor % capacity). */
    private static final int HEAD_CURSOR_OFFSET = PAGE_SIZE_OFFSET + Integer.BYTES;
    /** Logical position right after the last occupied record. Physical offset is (tailCursor % capacity). */
    private static final int TAIL_CURSOR_OFFSET = HEAD_CURSOR_OFFSET + Long.BYTES;
    /** int32 flags: currently only 'closed properly' is stored here. */
    private static final int FLAGS_OFFSET = TAIL_CURSOR_OFFSET + Long.BYTES;

    /**
     * Reserve 64 bytes for a header even though the current fields use less space: keeps the binary layout simple
     * and leaves room for future flags/counters.
     */
    private static final int HEADER_SIZE = 64;

    static {
      assert (FLAGS_OFFSET + Integer.BYTES <= HEADER_SIZE)
        : "Total fields size (" + (FLAGS_OFFSET + Integer.BYTES) + ") must fit into header size (" + HEADER_SIZE + ")";
    }

    private HeaderLayout() { }

    private static void initHeaderFields(@NotNull ByteBuffer headerBuffer,
                                         int pageSize) {
      if (headerBuffer.order() != nativeOrder()) {
        throw new IllegalArgumentException("headerBuffer.order=" + headerBuffer.order() + "; must be native (= " + nativeOrder() + ")");
      }
      int capacity = pageSize - HEADER_SIZE;
      if (capacity <= RecordLayout.HEADER_SIZE) {
        throw new IllegalArgumentException("pageSize(=" + pageSize + ") leaves too small capacity(=" + capacity + ")");
      }
      if (!AlignmentUtils.is32bAligned(capacity)) {
        throw new IllegalArgumentException("capacity(=" + capacity + ") must be 32b-aligned");
      }

      headerBuffer.putInt(MAGIC_WORD_OFFSET, MAGIC_WORD);
      headerBuffer.putInt(IMPL_VERSION_OFFSET, CURRENT_IMPLEMENTATION_VERSION);
      headerBuffer.putInt(PAGE_SIZE_OFFSET, pageSize);
      headerBuffer.putLong(HEAD_CURSOR_OFFSET, 0);
      headerBuffer.putLong(TAIL_CURSOR_OFFSET, 0);
      headerBuffer.putInt(FLAGS_OFFSET, FLAG_CLOSED_PROPERLY_MASK);
    }

    private static void checkFileParamsCompatible(@NotNull Path storagePath,
                                                  @NotNull ByteBuffer headerBuffer,
                                                  int pageSize) throws IOException {
      int magicWord = headerBuffer.getInt(MAGIC_WORD_OFFSET);
      if (magicWord != MAGIC_WORD) {
        throw new IOException(
          "[" + storagePath + "] is of incorrect type: " +
          ".magicWord(=" + magicWord + ", '" + IOUtil.magicWordToASCII(magicWord) + "') " +
          "!= expected(" + MAGIC_WORD + ", '" + IOUtil.magicWordToASCII(MAGIC_WORD) + "')"
        );
      }

      int implementationVersion = headerBuffer.getInt(IMPL_VERSION_OFFSET);
      if (implementationVersion != CURRENT_IMPLEMENTATION_VERSION) {
        throw new IOException(
          "[" + storagePath + "].implementationVersion(=" + implementationVersion + ") is not supported: " +
          CURRENT_IMPLEMENTATION_VERSION + " is the currently supported version"
        );
      }

      int filePageSize = headerBuffer.getInt(PAGE_SIZE_OFFSET);
      if (filePageSize != pageSize) {
        throw new IOException("[" + storagePath + "]: file created with pageSize=" + filePageSize + " but current pageSize=" + pageSize);
      }

      int capacity = readCapacity(headerBuffer);
      long head = readHeadCursor(headerBuffer);
      long tail = readTailCursor(headerBuffer);
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

    private static int readPageSize(@NotNull ByteBuffer headerBuffer) {
      return headerBuffer.getInt(PAGE_SIZE_OFFSET);
    }

    static int readCapacity(@NotNull ByteBuffer headerBuffer) {
      return readPageSize(headerBuffer) - HEADER_SIZE;
    }

    private static long readHeadCursor(@NotNull ByteBuffer headerBuffer) {
      return headerBuffer.getLong(HEAD_CURSOR_OFFSET);
    }

    private static void putHeadCursor(@NotNull ByteBuffer headerBuffer,
                                      long cursor) {
      headerBuffer.putLong(HEAD_CURSOR_OFFSET, cursor);
    }

    private static long readTailCursor(@NotNull ByteBuffer headerBuffer) {
      return headerBuffer.getLong(TAIL_CURSOR_OFFSET);
    }

    private static void putTailCursor(@NotNull ByteBuffer headerBuffer,
                                      long cursor) {
      headerBuffer.putLong(TAIL_CURSOR_OFFSET, cursor);
    }

    /** @return was storage closed properly before? */
    private static boolean markStorageOpened(@NotNull ByteBuffer headerBuffer) {
      int flags = headerBuffer.getInt(FLAGS_OFFSET);
      boolean wasClosedProperly = (flags & FLAG_CLOSED_PROPERLY_MASK) != 0;
      headerBuffer.putInt(FLAGS_OFFSET, flags & ~FLAG_CLOSED_PROPERLY_MASK);
      return wasClosedProperly;
    }

    private static void markStorageClosed(@NotNull ByteBuffer headerBuffer) {
      headerBuffer.putInt(FLAGS_OFFSET, headerBuffer.getInt(FLAGS_OFFSET) | FLAG_CLOSED_PROPERLY_MASK);
    }
  }

  private static final class RecordLayout {
    // Record = (int32 header) + (payload) + (implicit alignment padding)?
    // Header = 32 bit, at 32-bit-aligned offset:
    //          bit[31] (highest bit): record type, 0=data record, 1=padding record
    //          bit[30]: 'consumed' flag, 0=not consumed, 1=consumed
    //          bits[0..29]: 'raw' record length in bytes (without alignment padding)
    // For data record, 'raw' length is (header + payload), without implicit alignment padding. The real occupied
    // length is roundUpToInt32(rawLength), and the payload length is (rawLength-HEADER_SIZE).
    // For padding record, the raw length is the whole record length, with alignment already included (padding-record
    // has no payload => no need to separate '(aligned) record length' from '(unaligned) payload length').

    private static final int HEADER_SIZE = Integer.BYTES;
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

    private static void putDataRecord(@NotNull ByteBuffer buffer,
                                      int recordOffset,
                                      int payloadSize,
                                      @NotNull ByteBufferWriter writer) throws IOException {
      // Write the payload first, _then_ publish the header. Header==0 means "no valid record here" for corruption checks,
      // so publishing it last avoids exposing a valid header with not-yet-written payload during normal append.
      ByteBuffer payloadBuffer = buffer.slice(recordOffset + PAYLOAD_OFFSET, payloadSize).order(buffer.order());
      writer.write(payloadBuffer);
      if (payloadBuffer.remaining() > 0) {
        throw new IllegalStateException(
          "writer must fill up all " + payloadSize + " bytes in the buffer, but it doesn't: " +
          "buffer[pos: " + payloadBuffer.position() + ", lim: " + payloadBuffer.limit() + "]"
        );
      }
      buffer.putInt(recordOffset, dataRecordHeader(payloadSize));
    }

    private static void putPaddingRecord(@NotNull ByteBuffer buffer,
                                         int recordOffset,
                                         int paddingLength) {
      if (paddingLength <= 0) {
        throw new IllegalArgumentException("paddingLength(=" + paddingLength + ") must be >0");
      }
      if (!AlignmentUtils.is32bAligned(paddingLength)) {
        throw new IllegalArgumentException("paddingLength(=" + paddingLength + ") must be 32b-aligned");
      }
      // Padding record is marked as 'consumed' right from the start: it exists only to skip over it.
      buffer.putInt(recordOffset, RECORD_TYPE_PADDING | RECORD_CONSUMED_MASK | paddingLength);
    }

    private static int readHeader(@NotNull ByteBuffer buffer,
                                  int recordOffset) {
      return buffer.getInt(recordOffset);
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
     * @param storagePath used only to format error messages
     */
    private static int recordLength(int header,
                                    int offsetInDataSection,
                                    int bytesLeft,
                                    @NotNull Path storagePath) throws IOException {
      // We should only be called for bytes inside [head, tail). A zero header there means either a torn/corrupted header or
      // an incorrect queue interval in the persisted header.
      if (header == 0) {
        throw new CorruptedException("[" + storagePath + "] is corrupted: zero record header at data offset " +
                                     offsetInDataSection + ", bytesLeft=" + bytesLeft);
      }

      int rawRecordLength = rawRecordLength(header);
      int length = isPaddingHeader(header) ? rawRecordLength : AlignmentUtils.roundUpToInt32(rawRecordLength);
      if (length <= 0 || length > bytesLeft) {
        throw new CorruptedException("[" + storagePath + "] is corrupted: recordLength(=" + length + ") at data offset " +
                                     offsetInDataSection + " is outside remaining bytes " + bytesLeft);
      }
      if (isDataHeader(header) && rawRecordLength < PAYLOAD_OFFSET) {
        throw new CorruptedException("[" + storagePath + "] is corrupted: data record rawLength(=" + rawRecordLength + ") " +
                                     "is smaller than header size at data offset " + offsetInDataSection);
      }
      if (!AlignmentUtils.is32bAligned(length)) {
        throw new CorruptedException("[" + storagePath + "] is corrupted: recordLength(=" + length + ") is not 32b-aligned");
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

    private static void markConsumed(@NotNull ByteBuffer buffer,
                                     int recordOffset,
                                     int header) {
      buffer.putInt(recordOffset, header | RECORD_CONSUMED_MASK);
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
