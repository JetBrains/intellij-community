// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog;

import com.intellij.openapi.util.IntRef;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.Unmappable;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import com.intellij.util.io.dev.appendonlylog.ChunkedAppendOnlyLog;
import com.intellij.util.io.dev.mmapped.MMappedFileStorage;
import com.intellij.util.io.dev.mmapped.MMappedFileStorage.Page;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Objects;

import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.io.IOUtil.magicWordToASCII;
import static com.intellij.util.io.dev.AlignmentUtils.*;
import static java.lang.invoke.MethodHandles.byteBufferViewVarHandle;
import static java.nio.ByteOrder.nativeOrder;

/**
 * Implementation over memory-mapped file ({@link MMappedFileStorage}).
 * <p>
 * Thead-safe, partially-non-blocking -- {@link #append(int)} is blocking, but writing chunk content is non-blocking, and
 * so the chunk reading (leaving aside the fact that OS page management is not non-blocking).
 * <p>
 * Chunk capacity is limited {@link #MAX_PAYLOAD_SIZE}
 * <p>
 * Durability relies on OS: written data is durable if OS not crash (i.e. not loosing mmapped file content).
 */
@ApiStatus.Internal
public final class ChunkedAppendOnlyLogOverMMappedFile implements ChunkedAppendOnlyLog, Unmappable {
  //@formatter:off
  private static final boolean MORE_DIAGNOSTIC_INFORMATION = getBooleanProperty("AppendOnlyLogOverMMappedFile.MORE_DIAGNOSTIC_INFORMATION", true);
  private static final boolean ADD_LOG_CONTENT = getBooleanProperty("AppendOnlyLogOverMMappedFile.ADD_LOG_CONTENT", true);
  /** How wide region around questionable chunk to dump for debug diagnostics (see {@link #dumpContentAroundId(long, int)}) */
  private static final int DEBUG_DUMP_REGION_WIDTH = 128;
  //@formatter:on

  private static final VarHandle INT32_OVER_BYTE_BUFFER = byteBufferViewVarHandle(int[].class, nativeOrder()).withInvokeExactBehavior();
  private static final VarHandle INT64_OVER_BYTE_BUFFER = byteBufferViewVarHandle(long[].class, nativeOrder()).withInvokeExactBehavior();

  /** We assume the mapped file is filled with 0 initially, so 0 for any field is the value before anything was set */
  private static final int UNSET_VALUE = 0;


  /** First header int32, used to recognize this storage's file type */
  public static final int MAGIC_WORD = IOUtil.asciiToMagicWord("AOL2");

  public static final int CURRENT_IMPLEMENTATION_VERSION = 1;

  public static final int MAX_PAYLOAD_SIZE = LogChunkImpl.CHUNK_LENGTH_MAX - LogChunkImpl.HEADER_SIZE;


  //Implementation details:
  //    1) 2 global cursors 'allocated' and 'committed', always{committed <= allocated}
  //       'Allocated' cursor is bumped _before_ chunk header is written, 'committed' cursor is bumped immediately after.
  //       All the chunks < 'committed' have their headers written, and key chunk header data (type, length) is unmodifiable
  //       from now on.
  //       It could be only 1 chunk in [committed..allocated] region, and it is the one which header is being written
  //       right now.
  //    2) Chunk header has immutable and mutable parts. Chunk type (data|padding) and chunk length are immutable, they both
  //       written once at chunk allocation, and never modified. Chunk allocated/committed cursors are mutable parts of the
  //       chunk header.
  //    3) Chunk appending protocol:
  //       a) Allocate space for chunk: move 'allocated' cursor for recordLength bytes forward
  //       b) Set chunk header (chunkType, chunkLength)
  //       e) Move 'committed' cursor forward to be == 'allocated'.
  //
  // Finer details:
  //    1) Alignment: records are int64 aligned: volatile/atomic instructions universally work only on int32/int64-aligned
  //       addresses, and we need at least int32 volatile write for record header => record headers must be at least
  //       int32-aligned. I double down to 64b alignment so that chunk length could be packed into 3 bits less.
  //       Chunk.length is _total_ length of the chunk(content+header) -- i.e. next chunk is (currentOffset+chunkLength)
  //       rounded up to be int64-aligned.
  //
  //    2) Padding records: chunks are also page-aligned (MMappedFileStorage.pageSize). It was done mostly for simplification:
  //       it is possible to split the chunk between the pages, but it complicates code a lot, so I decided to avoid it.
  //       Page-alignment requires 'padding chunks' to fill the gap left if the chunk not fit the current page and must be
  //       moved to the next one entirely. Padding chunks are just chunks without content: they have length but content is
  //       'unused'. Padding chunks are also used to 'clean' unfinished records during recovery.
  //
  //    3) Recovery: if app crashes, append-only log is able to keep its state, because OS is responsible for flushing
  //       memory-mapped file content regardless of app status.
  //       FIXME RC: below desciription is not correct with chunks being append-only logs themselves
  //       Chunks < committed cursor are fully written, so no problems with them.
  //       Chunks in [committed..allocated] range could be fully of partially written, so we need to sort them out: if we
  //       see (committed < allocated) on log opening => we execute 'recovery' protocol to find out which chunks from that
  //       range were finished, and which were not. For
  //       that we scan [committed..allocated] chunk-by-chunk, and check chunk header. If chunk header is not exists =>
  //       records weren't finished, and there is nothing we can do about it => need to remove them from the log.
  //       But we can't physically remove them because log is append-only => we change record type to 'padding
  //       record'. 'Padding' plays the role of 'deleted' mark here, since all public accessors treat padding
  //       record as non-existent. (See also #2 in todos about a durability hole here)
  //
  //    4) 'connectionStatus' (as in other storages) is not needed here: updates are atomic, every saved state is
  //       at least self-consistent. We use (committed < allocated) as a marker of 'not everything was committed'
  //       => recovery is needed

  //TODO/MAYBE/FIXME:
  //    1. Protect from reading by 'false id': since id is basically a record offset, one could provide any value
  //       to .read(id) method, and there is no reliable way to detect 'this is not an id of existing record'. I.e.
  //       we could reject obviously incorrect ids -- negative, outside of allocated ids range, etc -- but in general
  //       there is no way to detect is it a valid id.
  //       I don't see cheap way to solve that 100%, but it is possible to have 90+% by making record header 'recognizable',
  //       i.e. reserve 1-2 bytes for some easily identifiable bit pattern. E.g. put int8_hash(recordId) into a 1st byte
  //       of record header, and check it on record read -- this gives us ~255/256 chance to identify 'false id'.
  //       Maybe make it configurable: off by default, but if user wants to spent additional 1-2-4 bytes per record to
  //       (almost) ensure 'false id' recognition -- it could be turned on.

  /** New chunk allocation path is guarded by this lock */
  private final Object allocationLock = new Object();

  /** file (storage) header. Public to use from StorageFactory */
  public static final class FileHeader {
    public static final int MAGIC_WORD_OFFSET = 0;
    public static final int IMPLEMENTATION_VERSION_OFFSET = MAGIC_WORD_OFFSET + Integer.BYTES;

    public static final int EXTERNAL_VERSION_OFFSET = IMPLEMENTATION_VERSION_OFFSET + Integer.BYTES;

    /**
     * We align records to pages, hence storage.pageSize is a parameter of binary layout.
     * E.g. if we created the log with pageSize=1Mb, and re-open it with pageSize=512Kb -- now some records could
     * break page borders, which is incorrect => we need to store pageSize, and check it on opening
     */
    public static final int PAGE_SIZE_OFFSET = EXTERNAL_VERSION_OFFSET + Integer.BYTES;


    /** Offset (in file) of the next-record-to-be-allocated */
    public static final int NEXT_CHUNK_TO_BE_ALLOCATED_OFFSET = PAGE_SIZE_OFFSET + Integer.BYTES;
    /** Records with offset < recordsCommittedUpToOffset are guaranteed to be all finished (written). */
    public static final int NEXT_CHUNK_TO_BE_COMMITTED_OFFSET = NEXT_CHUNK_TO_BE_ALLOCATED_OFFSET + Long.BYTES;

    /**
     * int32: total number of data records committed to the log.
     * Only data records counted, padding records are not counted here -- they considered to be an implementation detail
     * which should not be visible outside.
     * Only committed records counted -- i.e. those < commited cursor
     */
    public static final int CHUNKS_COUNT_OFFSET = NEXT_CHUNK_TO_BE_COMMITTED_OFFSET + Long.BYTES;

    public static final int FIRST_UNUSED_OFFSET = CHUNKS_COUNT_OFFSET + Integer.BYTES;

    //reserve [8 x int64] just in the case
    public static final int HEADER_SIZE = 8 * Long.BYTES;

    static {
      if (!is64bAligned(HEADER_SIZE)) {
        throw new ExceptionInInitializerError("HEADER_SIZE(" + HEADER_SIZE + ") must be 64b-aligned");
      }
      if (HEADER_SIZE < FIRST_UNUSED_OFFSET) {
        throw new ExceptionInInitializerError(
          "FIRST_UNUSED_OFFSET(" + FIRST_UNUSED_OFFSET + ") is > reserved HEADER_SIZE(=" + HEADER_SIZE + ")");
      }
    }

    //FileHeader fields below are accessed only in ctor, hence do not require volatile/VarHandle. And they're
    // also accessed from the AppendOnlyLogFactory for eager file type/param check. So they are here, while
    // more 'private' header fields constantly modified during AOLog lifetime are accessed in a different way
    // see set/getHeaderField()

    private final ByteBuffer headerPageBuffer;

    public FileHeader(@NotNull Page headerPage) { this.headerPageBuffer = headerPage.rawPageBuffer(); }


    public int readMagicWord() {
      return headerPageBuffer.getInt(MAGIC_WORD_OFFSET);
    }

    public int readImplementationVersion() {
      return headerPageBuffer.getInt(IMPLEMENTATION_VERSION_OFFSET);
    }

    public int readPageSize() {
      return headerPageBuffer.getInt(PAGE_SIZE_OFFSET);
    }

    public void putMagicWord(int magicWord) {
      headerPageBuffer.putInt(MAGIC_WORD_OFFSET, magicWord);
    }

    public void putImplementationVersion(int implVersion) {
      headerPageBuffer.putInt(IMPLEMENTATION_VERSION_OFFSET, implVersion);
    }

    public void putPageSize(int pageSize) {
      headerPageBuffer.putInt(PAGE_SIZE_OFFSET, pageSize);
    }


    private int getIntHeaderField(int headerRelativeOffsetBytes) {
      Objects.checkIndex(headerRelativeOffsetBytes, HEADER_SIZE - Integer.BYTES + 1);
      return (int)INT32_OVER_BYTE_BUFFER.getVolatile(headerPageBuffer, headerRelativeOffsetBytes);
    }

    private long getLongHeaderField(int headerRelativeOffsetBytes) {
      Objects.checkIndex(headerRelativeOffsetBytes, HEADER_SIZE - Long.BYTES + 1);
      return (long)INT64_OVER_BYTE_BUFFER.getVolatile(headerPageBuffer, headerRelativeOffsetBytes);
    }

    private void setIntHeaderField(int headerRelativeOffsetBytes,
                                   int headerFieldValue) {
      Objects.checkIndex(headerRelativeOffsetBytes, HEADER_SIZE - Integer.BYTES + 1);
      INT32_OVER_BYTE_BUFFER.setVolatile(headerPageBuffer, headerRelativeOffsetBytes, headerFieldValue);
    }

    private void setLongHeaderField(int headerRelativeOffsetBytes,
                                    long headerFieldValue) {
      Objects.checkIndex(headerRelativeOffsetBytes, HEADER_SIZE - Long.BYTES + 1);
      INT64_OVER_BYTE_BUFFER.setVolatile(headerPageBuffer, headerRelativeOffsetBytes, headerFieldValue);
    }

    private long firstUnAllocatedOffset() {
      return getLongHeaderField(NEXT_CHUNK_TO_BE_ALLOCATED_OFFSET);
    }

    private void updateFirstUnAllocatedOffset(long newValue) {
      //setVolatile even though all updates are under the allocationLock -- because there are a lot of
      // places there we _read_ the value without lock, so visibility must be ensured
      INT64_OVER_BYTE_BUFFER.setVolatile(
        headerPageBuffer,
        NEXT_CHUNK_TO_BE_ALLOCATED_OFFSET,
        newValue
      );
    }

    private long firstUnCommittedOffset() {
      return getLongHeaderField(NEXT_CHUNK_TO_BE_COMMITTED_OFFSET);
    }

    private void updateFirstUnCommittedOffset(long newValue) {
      //setVolatile even though all updates are under the allocationLock -- because there are a lot of
      // places there we _read_ the value without lock, so visibility must be ensured
      INT64_OVER_BYTE_BUFFER.setVolatile(
        headerPageBuffer,
        NEXT_CHUNK_TO_BE_COMMITTED_OFFSET,
        newValue
      );
    }

    private int addToDataRecordsCount(int recordsCommitted) {
      return (int)INT32_OVER_BYTE_BUFFER.getAndAdd(
        headerPageBuffer,
        CHUNKS_COUNT_OFFSET,
        recordsCommitted
      );
    }
  }

  private static class LogChunkImpl implements LogChunk {
    //Record = (header) + (payload)
    //FileHeader: 4 bytes
    //        1 bit type (data/padding)
    //        1 bit size kind (reserved for future)
    //        8 bits: size (in 8byte-buckets => up to 2^11=2k)
    //        11+11 bits: allocated/committed cursors
    //
    //MAYBE RC: nextChunkId -- a part of standard header, or it is a client's responsibility to store it?
    //          It is more convenient to reserve the field in a header, but the downside is: most of the chunks don't need
    //          that pointer -- only a minor subset of values are large enough to occupy >1 chunk, so this is 90% waste
    //          of space.
    //          Possible solution: nextChunkId is allocated by client (i.e. not a part of standard header). Initial chunk
    //          is allocated without nextChunkId, because in most of cases value fits into the initial chunk. If value
    //          doesn't fit -- client allocated new, larger chunk, with reserved space for nextChunkId, and copy current
    //          chunk's content into the new one.
    //          Why it is good solution: is hard to find optimal tradeoff between reserving/not reserving .nextChunkId
    //          without knowledge of value's size distribution -- which is not available in log implementation. Depending
    //          on the nature of values to be stored in the log, clients could use chunk-allocation-strategies with
    //          various tradeoffs between chunk re-allocation, and chunks chaining (with .nextChunkId).
    //
    //
    //MAYBE RC: Current chunk header occupies 4bytes and stores up to ~2Kb data. There are use-cases with very small
    //          values, there 4bytes header is quite an overhead, while 2Kb capacity is (almost) never needed. There
    //          are other use-cases that require chunks larger than 2Kb, while header size overhead is un-important.
    //          => probably it is worth to think about 2-3 'kind' of chunks, e.g. 'tiny' chunk with header=2bytes, max
    //          capacity=32bytes, and/or 'huge' chunk with header=8bytes, and max capacity=~1Mb. I reserved 'size kind'
    //          bit in the current header for that purpose


    //@formatter:off

    /** 0==regular chunk, 1==padding chunk */
    protected static final int RECORD_TYPE_MASK    = 1 << 31;
    protected static final int RECORD_TYPE_DATA    = 0;
    protected static final int RECORD_TYPE_PADDING = 1 << 31;

    /** Reserved for future, currently unused */
    protected static final int SIZE_KIND_MASK      = 1 << 30;
    protected static final int SIZE_KIND_TINY      = 1 << 30;
    protected static final int SIZE_KIND_REGULAR   = 0;

    protected static final int CHUNK_LENGTH_MAX           = roundDownToInt64( (1 << 11) - 1 );
    protected static final int CHUNK_LENGTH_MASK          = 0b00111111_11000000_00000000_00000000;
    protected static final int CHUNK_LENGTH_MASK_SHR      = 22;


    protected static final int OFFSET_HEADER       = 0;
    protected static final int HEADER_SIZE = 4;

    protected static final int OFFSET_PAYLOAD   = OFFSET_HEADER + HEADER_SIZE;

    //@formatter:on

    protected final ByteBuffer pageBuffer;
    protected final int offsetInBuffer;
    protected final long offsetInFile;

    //chunkLength, chunk type (data/padding) -- are unmodifiable chunk properties, set once, at chunk allocation,
    //and never changed => they could be read once, and kept in object fields for faster access

    protected final int chunkLength;
    protected final boolean padding;

    protected LogChunkImpl(long chunkOffsetInFile,
                           @NotNull ByteBuffer pageBuffer,
                           int chunkOffsetInBuffer,
                           int totalChunkLength,
                           boolean padding) {
      assert64bAligned(totalChunkLength, "totalChunkLength");
      if (totalChunkLength <= 0 || totalChunkLength > CHUNK_LENGTH_MAX) {
        throw new IllegalArgumentException("totalChunkLength(=" + totalChunkLength + ") must be in (0, " + CHUNK_LENGTH_MAX + "]");
      }

      this.offsetInFile = chunkOffsetInFile;

      this.pageBuffer = pageBuffer;
      this.offsetInBuffer = chunkOffsetInBuffer;

      this.chunkLength = totalChunkLength;
      this.padding = padding;
    }

    @Override
    public long id() {
      return chunkOffsetToId(offsetInFile);
    }

    @Override
    public int capacity() {
      return chunkLength() - HEADER_SIZE;
    }

    /** @return total length, payload+header */
    public int chunkLength() {
      return chunkLength;
    }

    public boolean isPadding() {
      return padding;
    }

    public boolean isDataChunk() {
      return !isPadding();
    }

    public boolean isFitIntoPage() {
      return isFitIntoPage(pageBuffer, offsetInBuffer, chunkLength());
    }

    @Override
    public boolean isFull() {
      int header = readHeader();
      int allocatedCursor = unpackAllocatedCursor(header);
      return allocatedCursor == capacity();
    }

    @Override
    public int remaining() {
      int header = readHeader();
      int allocatedCursor = unpackAllocatedCursor(header);
      return capacity() - allocatedCursor;
    }


    @Override
    public ByteBuffer read() {
      int header = readHeader();
      int committedCursor = unpackCommittedCursor(header);
      return pageBuffer
        .slice(offsetInBuffer + OFFSET_PAYLOAD, committedCursor)
        .asReadOnlyBuffer()
        .order(pageBuffer.order());
    }

    @Override
    public boolean append(@NotNull ByteBufferWriter writer,
                          int recordSize) throws IOException {
      int newAllocatedCursor;
      while (true) {//CAS loop (allocatedCursor -> allocatedCursor + recordSize):
        int header = readHeader();
        int allocatedCursor = unpackAllocatedCursor(header);
        int committedCursor = unpackCommittedCursor(header);
        newAllocatedCursor = allocatedCursor + recordSize;
        if (newAllocatedCursor > capacity()) {
          return false;
        }
        if (casHeader(header, packChunkHeader(newAllocatedCursor, committedCursor))) {
          break;
        }
      }

      int recordStartingOffset = newAllocatedCursor - recordSize;
      ByteBuffer bufferForWrite = pageBuffer
        .slice(offsetInBuffer + OFFSET_PAYLOAD + recordStartingOffset, recordSize)
        .order(pageBuffer.order());
      try {
        writer.write(bufferForWrite);
      }
      finally {
        //
        while (true) {//CAS loop/spin loop:
          // wait for committedCursor=start of record
          // => cas(committedCursor -> committedCursor + recordSize)
          int _header = readHeader();
          int _allocatedCursor = unpackAllocatedCursor(_header);
          int _committedCursor = unpackCommittedCursor(_header);
          if (_committedCursor == recordStartingOffset) {
            if (casHeader(_header, packChunkHeader(_allocatedCursor, _committedCursor + recordSize))) {
              break;
            }
          }
        }
      }
      return true;
    }

    @Override
    public String toString() {
      int header = readHeader(pageBuffer, offsetInBuffer);
      return "Chunk[#" + id() + ", " + (isDataChunk() ? "data" : "padding") + "][" +
             (isDataChunk() ? "content: " + unpackCommittedCursor(header) + ".." + unpackAllocatedCursor(header) + " of " + capacity()
                            : "-") +
             "]{header: " + Integer.toHexString(header) + ", chunkLength: " + chunkLength + "}" +
             "{inFile: @" + offsetInFile + ", inPage: @" + offsetInBuffer + "}";
    }

    private static LogChunkImpl putRegularChunk(int chunkSize,
                                                @NotNull ByteBuffer pageBuffer,
                                                int chunkOffsetInBuffer,
                                                long chunkOffsetInFile) {
      LogChunkImpl chunk = new LogChunkImpl(chunkOffsetInFile, pageBuffer, chunkOffsetInBuffer, chunkSize, /*isPadding: */ false);
      chunk.writeHeaderInitial();
      return chunk;
    }

    private static void putPaddingChunk(int remainsToPad,
                                        @NotNull ByteBuffer pageBuffer,
                                        int chunkOffsetInBuffer,
                                        long chunkOffsetInFile) {
      LogChunkImpl chunk = new LogChunkImpl(chunkOffsetInFile, pageBuffer, chunkOffsetInBuffer, remainsToPad, /*isPadding: */ true);
      chunk.writeHeaderInitial();
    }

    protected static boolean isPaddingChunk(int header) {
      return (header & RECORD_TYPE_MASK) == RECORD_TYPE_PADDING;
    }

    protected static boolean isDataChunk(int header) {
      return (header & RECORD_TYPE_MASK) == RECORD_TYPE_DATA;
    }

    protected static boolean isHeaderSet(int header) {
      // a) un-allocated log area expected to be zeroed
      // b) valid header is always != 0 (because at least totalChunkSize > 0)
      return header != 0;
    }

    protected static int readHeader(ByteBuffer buffer,
                                    int offsetInBuffer) {
      return (int)INT32_OVER_BYTE_BUFFER.getVolatile(buffer, offsetInBuffer + OFFSET_HEADER);
    }

    private int readHeader() {
      return readHeader(pageBuffer, offsetInBuffer);
    }

    protected void writeHeaderInitial() {
      int chunkHeader = packChunkHeader(chunkLength(), isPadding(), 0, 0);
      INT32_OVER_BYTE_BUFFER.setVolatile(pageBuffer, offsetInBuffer + OFFSET_HEADER, chunkHeader);
    }


    /** generates chunk header given chunk length and chunk type (padding/data) */
    private static int packChunkHeader(int chunkLength,
                                       boolean isPadding,
                                       int allocatedCursor,
                                       int committedCursor) {
      assert64bAligned(chunkLength, "chunkLength");
      if (chunkLength <= 0 || chunkLength > CHUNK_LENGTH_MAX) {
        throw new IllegalArgumentException("totalChunkLength(=" + chunkLength + ") must be in (0," + CHUNK_LENGTH_MAX + "]");
      }
      if (allocatedCursor < 0 || allocatedCursor > CHUNK_LENGTH_MAX) {
        throw new IllegalArgumentException("allocatedCursor(=" + allocatedCursor + ") must be in [0," + CHUNK_LENGTH_MAX + "]");
      }
      if (committedCursor < 0 || committedCursor > CHUNK_LENGTH_MAX) {
        throw new IllegalArgumentException("committedCursor(=" + committedCursor + ") must be in [0," + CHUNK_LENGTH_MAX + "]");
      }

      int typeComponent = isPadding ? RECORD_TYPE_PADDING : RECORD_TYPE_DATA;
      int lengthComponent = (chunkLength >> 3) << CHUNK_LENGTH_MASK_SHR;
      int committedCursorComponent = (committedCursor & 0b111_11111111) << 11;
      int allocatedCursorComponent = allocatedCursor & 0b111_11111111;
      if (lengthComponent == 0) {
        throw new AssertionError("chunkLength=" + chunkLength + " => lengthComponent is 0");
      }
      return typeComponent
             | lengthComponent
             | committedCursorComponent
             | allocatedCursorComponent;
    }

    private int packChunkHeader(int allocatedCursor,
                                int committedCursor) {
      return packChunkHeader(chunkLength, padding, allocatedCursor, committedCursor);
    }

    /** @return total chunk length (including header) */
    protected static int unpackChunkLength(int header) {
      int chunkLengthField = (header & CHUNK_LENGTH_MASK) >> CHUNK_LENGTH_MASK_SHR;
      return chunkLengthField << 3;
    }

    private static int unpackAllocatedCursor(int header) {
      return (header & 0b111_11111111);
    }

    private static int unpackCommittedCursor(int header) {
      return (header >> 11) & 0b111_11111111;
    }

    private boolean casHeader(int expectedHeader,
                              int newHeader) {
      return INT32_OVER_BYTE_BUFFER.compareAndSet(pageBuffer, offsetInBuffer + OFFSET_HEADER,
                                                  expectedHeader, newHeader);
    }


    protected static int chunkLengthForPayload(int payloadSize) {
      return roundUpToInt64(payloadSize + HEADER_SIZE);
    }

    /**
     * @return true if chunk [chunkOffsetInPage + chunkLength) fits into a pageBuffer, false otherwise
     */
    protected static boolean isFitIntoPage(ByteBuffer pageBuffer,
                                           int chunkOffsetInPage,
                                           int chunkLength) {
      return chunkOffsetInPage + chunkLength <= pageBuffer.limit();
    }
  }

  private final @NotNull MMappedFileStorage storage;
  /** Cache header page since we access it on each op (read/update cursors) */
  private transient FileHeader header;


  //Suspicious region is [committedCursor..allocatedCursor] if not empty. (committedCursor < allocatedCursor) on storage
  // open means that storage wasn't properly closed -- i.e. app crash -- and chunks in that region could be partially
  // written => need to scan them to recover that could be recovered
  //
  private final long startOfSuspiciousRegion;
  private final long endOfSuspiciousRegion;


  public ChunkedAppendOnlyLogOverMMappedFile(@NotNull MMappedFileStorage storage) throws IOException {
    this.storage = storage;
    boolean fileIsEmpty = (storage.actualFileSize() == 0);

    int pageSize = storage.pageSize();
    if (!is64bAligned(pageSize)) {
      throw new IllegalArgumentException("storage.pageSize(=" + pageSize + ") must be 64b-aligned");
    }

    header = new FileHeader(storage.pageByOffset(0L));

    if (fileIsEmpty) {
      header.putMagicWord(MAGIC_WORD);
      header.putImplementationVersion(CURRENT_IMPLEMENTATION_VERSION);
      header.putPageSize(pageSize);
    }
    else {
      checkFileParamsCompatible(storage.storagePath(), header, pageSize);
    }


    long nextRecordToBeAllocatedOffset = header.getLongHeaderField(FileHeader.NEXT_CHUNK_TO_BE_ALLOCATED_OFFSET);
    if (nextRecordToBeAllocatedOffset == UNSET_VALUE) {//log is just created:
      nextRecordToBeAllocatedOffset = FileHeader.HEADER_SIZE;
      header.setLongHeaderField(FileHeader.NEXT_CHUNK_TO_BE_ALLOCATED_OFFSET, nextRecordToBeAllocatedOffset);
    }

    long nextRecordToBeCommittedOffset = header.getLongHeaderField(FileHeader.NEXT_CHUNK_TO_BE_COMMITTED_OFFSET);
    if (nextRecordToBeCommittedOffset == UNSET_VALUE) {//log is just created:
      nextRecordToBeCommittedOffset = FileHeader.HEADER_SIZE;
      header.setLongHeaderField(FileHeader.NEXT_CHUNK_TO_BE_COMMITTED_OFFSET, nextRecordToBeCommittedOffset);
    }

    if (nextRecordToBeCommittedOffset < nextRecordToBeAllocatedOffset) {
      //storage wasn't closed correctly -- probably forced restart? -> need to recover
      //FIXME RC: now this is not enough -- it could be all chunks are written, but chunk content is not finished
      //          Seems like we need closedProperly field in header for that


      //For a recovery we need 2 things:
      // - convert all non-commited records to padding records (we can't remove them, but can't recover either)
      // - clear everything after nextRecordToBeAllocatedOffset (log mechanics assumes file tail after
      //   .nextRecordToBeAllocatedOffset is always filled with 0)
      startOfSuspiciousRegion = nextRecordToBeCommittedOffset;
      endOfSuspiciousRegion = nextRecordToBeAllocatedOffset;
      long successfullyRecoveredUntil = recoverRegion(nextRecordToBeCommittedOffset, nextRecordToBeAllocatedOffset);

      long fileSize = storage.actualFileSize();
      if (fileSize < successfullyRecoveredUntil) {
        //Mapped storage must enlarge file so that all non-0 values in mapped buffers are within file bounds
        // (because it is 'undefined behavior' to have something in mapped buffer beyond the actual end-of-file)
        // -- and there are non-0 values up until successfullyRecoveredUntil, because at least record header is
        // non-0 for valid records.
        // So fileSize < successfullyRecoveredUntil must never happen:
        throw new AssertionError(
          "file(=" + storage.storagePath() + ").size(=" + fileSize + ") < recoveredUntil(=" + successfullyRecoveredUntil + ")");
      }
      //zero file suffix:
      storage.zeroizeTillEOF(successfullyRecoveredUntil);


      nextRecordToBeCommittedOffset = successfullyRecoveredUntil;
      nextRecordToBeAllocatedOffset = successfullyRecoveredUntil;

      //records count could be incorrect if wasn't properly closed => re-count records:
      IntRef recordsCount = new IntRef(0);
      forEachRecord(chunk -> {
        recordsCount.inc();
        return true;
      }, successfullyRecoveredUntil);
      header.setIntHeaderField(FileHeader.CHUNKS_COUNT_OFFSET, recordsCount.get());
    }
    else {
      startOfSuspiciousRegion = -1;
      endOfSuspiciousRegion = -1;
    }


    header.setLongHeaderField(FileHeader.NEXT_CHUNK_TO_BE_ALLOCATED_OFFSET, nextRecordToBeAllocatedOffset);
    header.setLongHeaderField(FileHeader.NEXT_CHUNK_TO_BE_COMMITTED_OFFSET, nextRecordToBeCommittedOffset);
  }


  /**
   * @return version of the log implementation (i.e., this class) used to create the file.
   * Current version is {@link #CURRENT_IMPLEMENTATION_VERSION}
   */
  public int getImplementationVersion() {
    return header.readImplementationVersion();
  }

  /** @return version of _data_ stored in records -- up to the client to define/recognize it */
  public int getDataVersion() {
    return header.getIntHeaderField(FileHeader.EXTERNAL_VERSION_OFFSET);
  }

  public void setDataVersion(int version) {
    header.setIntHeaderField(FileHeader.EXTERNAL_VERSION_OFFSET, version);
  }

  @Override
  public int chunksCount() {
    //only data chunks count -- padding chunks are implementation detail, shouldn't be visible outside the class
    return header.getIntHeaderField(FileHeader.CHUNKS_COUNT_OFFSET);
  }

  /** @return arbitrary (user-defined) value from the Log's header, previously set by {@link #setUserDefinedHeaderField(int, int)} */
  public int getUserDefinedHeaderField(int fieldNo) {
    int headerOffset = FileHeader.FIRST_UNUSED_OFFSET + fieldNo * Integer.BYTES;
    return header.getIntHeaderField(headerOffset);
  }

  /**
   * Sets arbitrary (user-defined) value in a Log's header.
   * There are 5 slots fieldNo=[0..5] available so far
   */
  public void setUserDefinedHeaderField(int fieldNo,
                                        int headerFieldValue) {
    int headerOffset = FileHeader.FIRST_UNUSED_OFFSET + fieldNo * Integer.BYTES;
    header.setIntHeaderField(headerOffset, headerFieldValue);
  }

  /** @return true if the log wasn't properly closed and did some compensating recovery measured on open */
  public boolean wasRecoveryNeeded() {
    return startOfSuspiciousRegion >= 0 && endOfSuspiciousRegion > startOfSuspiciousRegion;
  }

  public Path storagePath() {
    return storage.storagePath();
  }

  @Override
  public LogChunk append(int chunkPayloadCapacity) throws IOException {
    if (chunkPayloadCapacity <= 0) {
      throw new IllegalArgumentException("Can't append chunk with payloadCapacity(=" + chunkPayloadCapacity + ") <= 0");
    }
    if (chunkPayloadCapacity > MAX_PAYLOAD_SIZE) {
      throw new IllegalArgumentException("payloadCapacity(=" + chunkPayloadCapacity + ") > MAX(" + MAX_PAYLOAD_SIZE + ")");
    }

    int pageSize = storage.pageSize();
    if (chunkPayloadCapacity > pageSize - LogChunkImpl.HEADER_SIZE) {
      throw new IllegalArgumentException("Requested chunkPayloadCapacity(=" + chunkPayloadCapacity + ") is too big: " +
                                         "chunk with header must fit pageSize(=" + pageSize + ")");
    }

    //RC: It is teasing to 'pre-touch' the mmapped buffer before acquiring the lock to trigger page-fault (if any) outside
    //    the lock, thus reducing the chance of getting stuck on page-fault while keeping the lock.
    //    But it seems it gives no additional parallelism, since even with page-fault moved out of lock -- any other thread
    //    that comes here needs to access the same pages (header + current data page) => will get stuck on exactly the
    //    same page-fault anyway. 'Pre-touching' provides no additional parallelism, the only difference is _how_ the threads
    //    will wait: either one thread waits on the page-fault, and others waiting on lock, OR all the threads are waiting on
    //    page-fault. 
    int totalChunkLength = LogChunkImpl.chunkLengthForPayload(chunkPayloadCapacity);
    synchronized (allocationLock) {
      long chunkOffsetInFile = allocateSpaceForChunk(totalChunkLength);
      assert64bAligned(chunkOffsetInFile, "chunkOffsetInFile");

      Page page = storage.pageByOffset(chunkOffsetInFile);
      int offsetInPage = storage.toOffsetInPage(chunkOffsetInFile);

      LogChunkImpl chunk = LogChunkImpl.putRegularChunk(totalChunkLength, page.rawPageBuffer(), offsetInPage, chunkOffsetInFile);

      header.addToDataRecordsCount(1);
      header.updateFirstUnCommittedOffset(chunkOffsetInFile + totalChunkLength);

      return chunk;
    }
  }

  @Override
  public LogChunk read(long chunkId) throws IOException {
    long chunkOffsetInFile = chunkIdToOffset(chunkId);

    long chunksCommittedUpTo = header.firstUnCommittedOffset();
    if (chunkOffsetInFile >= chunksCommittedUpTo) {
      throw new IllegalArgumentException(
        "Can't read chunk(id: " + chunkId + ", offset: " + chunkOffsetInFile + "): " +
        "outside of committed region [<" + chunksCommittedUpTo + "] " +
        moreDiagnosticInfo(chunkOffsetInFile));
    }


    Page page = storage.pageByOffset(chunkOffsetInFile);
    int chunkOffsetInPage = storage.toOffsetInPage(chunkOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();

    LogChunkImpl chunk = readChunkAt(
      pageBuffer,
      chunkOffsetInPage,
      chunkOffsetInFile
    );
    if (!chunk.isFitIntoPage()) {
      throw new CorruptedException(chunk + ".chunkLength(=" + chunk.chunkLength() + ") " +
                                   "is incorrect: page[0.." + pageBuffer.limit() + "], " +
                                   "committedUpTo: " + header.firstUnCommittedOffset() + ", " +
                                   "allocatedUpTo: " + header.firstUnAllocatedOffset() + ". " +
                                   moreDiagnosticInfo(chunkOffsetInFile) +
                                   (ADD_LOG_CONTENT ? "\n" + dumpContentAroundId(chunk.id(), DEBUG_DUMP_REGION_WIDTH) : "")
      );
    }


    if (chunk.isPadding()) {
      throw new IOException(chunk + " is a PaddingChunk -- i.e. has no data. " + moreDiagnosticInfo(chunkOffsetInFile));
    }

    return chunk;
  }

  public boolean isValidId(long chunkId) {
    if (chunkId <= 0) {
      return false;
    }
    long chunkOffset = chunkIdToOffsetUnchecked(chunkId);
    if (!is64bAligned(chunkOffset)) {
      return false;
    }
    return chunkOffset < header.firstUnAllocatedOffset();
  }

  @Override
  public boolean forEachChunk(@NotNull ChunkReader reader) throws IOException {
    long firstUnallocatedOffset = header.firstUnAllocatedOffset();
    return forEachRecord(reader, firstUnallocatedOffset);
  }

  public void clear() throws IOException {
    throw new UnsupportedOperationException("Method not implemented yet");
  }

  @Override
  public void flush() throws IOException {
    flush(MMappedFileStorage.FSYNC_ON_FLUSH_BY_DEFAULT);
  }

  /** fsync=true should be used in a rare occasions only: see {@link MMappedFileStorage#FSYNC_ON_FLUSH_BY_DEFAULT} */
  public void flush(boolean fsync) throws IOException {
    if (fsync) {
      storage.fsync();
    }
    //else: nothing to do -- everything is already in the mapped buffer
  }

  @Override
  public boolean isEmpty() {
    return header.firstUnAllocatedOffset() == FileHeader.HEADER_SIZE
           && header.firstUnCommittedOffset() == FileHeader.HEADER_SIZE;
  }

  @Override
  public void close() throws IOException {
    if (storage.isOpen()) {
      flush();
      storage.close();
      header = null;//help GC unmap pages sooner
    }
  }

  @Override
  public void closeAndUnsafelyUnmap() throws IOException {
    close();
    storage.closeAndUnsafelyUnmap();
  }

  /**
   * Closes the file, releases the mapped buffers, and tries to delete the file.
   * <p/>
   * Implementation note: the exact moment file memory-mapping is actually released and the file could be
   * deleted -- is very OS/platform-dependent. E.g., Win is known to keep file 'in use' for some time even
   * after unmap() call is already finished. In JVM, GC is responsible for releasing mapped buffers -- which
   * adds another level of uncertainty. Hence, if one needs to re-create the storage, it may be more reliable
   * to just .clear() the current storage, than to closeAndRemove -> create-fresh-new.
   */
  @Override
  public void closeAndClean() throws IOException {
    close();
    storage.closeAndClean();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + storage.storagePath() + "]";
  }

  public String dumpDebugInfo() {
    return getClass().getSimpleName() +
           "[" + header.firstUnCommittedOffset() + ".." + header.firstUnAllocatedOffset() + "]" +
           "{" + chunksCount() + " data chunks}";
  }

  private String dumpContentAroundId(long aroundChunkId,
                                     int chunksAround) throws IOException {
    StringBuilder sb = new StringBuilder("Log content around id: " + aroundChunkId + " +/- " + chunksAround +
                                         " (first uncommitted offset: " + header.firstUnCommittedOffset() +
                                         ", first unallocated: " + header.firstUnAllocatedOffset() + ")\n");
    forEachChunk(chunk -> {
      long chunkId = chunk.id();
      ByteBuffer buffer = chunk.read();
      long nextRecordId = chunkIdToOffset(
        nextChunkOffset(chunkIdToOffset(chunkId), buffer.remaining())
      );
      //MAYBE RC: only dump 'questionableRecord'? Seems like records around are of little use
      boolean insideQuestionableRecord = (chunkId <= aroundChunkId && aroundChunkId <= nextRecordId);
      boolean insideNeighbourRegion = (aroundChunkId - chunksAround <= chunkId
                                       && chunkId <= aroundChunkId + chunksAround);

      if (insideQuestionableRecord || insideNeighbourRegion) {
        String bufferAsHex = IOUtil.toHexString(buffer);
        sb.append(insideQuestionableRecord ? "*" : "")
          .append("[id: ").append(chunkId).append("][offset: ").append(chunkIdToOffset(chunkId)).append("][hex: ")
          .append(bufferAsHex).append("]\n");
      }
      return chunkId <= aroundChunkId + chunksAround;
    });
    return sb.toString();
  }

  private String moreDiagnosticInfo(long chunkOffsetInFile) {
    if (!MORE_DIAGNOSTIC_INFORMATION) {
      return "";
    }

    if (startOfSuspiciousRegion < 0 && endOfSuspiciousRegion < 0) {
      return "(There was no recovery, it can't be related to it)";
    }
    if (chunkOffsetInFile >= startOfSuspiciousRegion && chunkOffsetInFile < endOfSuspiciousRegion) {
      return "(Chunk is in the recovered region [" + startOfSuspiciousRegion + ".." + endOfSuspiciousRegion + ") " +
             "so it may be due to some un-recovered records)";
    }

    return "(There was a recovery so it may be due to some un-recovered records, " +
           "but the Chunk is outside the region [" + startOfSuspiciousRegion + ".." + endOfSuspiciousRegion + ") recovered)";
  }


  /**
   * Reads key storage params from the header byte buffer, and checks them against params supported by this
   * implementation. Throws {@link IOException} if there is an incompatibility.
   */
  public static void checkFileParamsCompatible(@NotNull Path storagePath,
                                               @NotNull ChunkedAppendOnlyLogOverMMappedFile.FileHeader header,
                                               int pageSize) throws IOException {

    int magicWord = header.readMagicWord();
    if (magicWord != MAGIC_WORD) {
      throw new IOException(
        "[" + storagePath + "] is of incorrect type: " +
        ".magicWord(=" + magicWord + ", '" + magicWordToASCII(magicWord) + "') != " + MAGIC_WORD + " expected");
    }

    int implementationVersion = header.readImplementationVersion();
    if (implementationVersion != CURRENT_IMPLEMENTATION_VERSION) {
      throw new IOException(
        "[" + storagePath + "].implementationVersion(=" + implementationVersion + ") is not supported: " +
        CURRENT_IMPLEMENTATION_VERSION + " is the currently supported version.");
    }

    int filePageSize = header.readPageSize();
    if (pageSize != filePageSize) {
      throw new IOException(
        "[" + storagePath + "]: file created with pageSize=" + filePageSize +
        " but current storage.pageSize=" + pageSize);
    }
  }

  // ============== implementation: ======================================================================

  private LogChunkImpl readChunkAt(@NotNull ByteBuffer pageBuffer,
                                   int chunkOffsetInBuffer,
                                   long chunkOffsetInFile) throws IOException {
    int header = LogChunkImpl.readHeader(pageBuffer, chunkOffsetInBuffer);
    if (!LogChunkImpl.isHeaderSet(header)) {
      long chunkId = chunkOffsetToId(chunkOffsetInFile);
      throw new IOException("chunk[" + chunkId + "][@" + chunkOffsetInFile + "].header is not written: " +
                            "(header=" + Integer.toHexString(header) + ") either unfinished or corrupted. " +
                            moreDiagnosticInfo(chunkOffsetInFile) +
                            (ADD_LOG_CONTENT ? "\n" + dumpContentAroundId(chunkId, DEBUG_DUMP_REGION_WIDTH) : "")
      );
    }
    int chunkLength = LogChunkImpl.unpackChunkLength(header);
    boolean isPadding = LogChunkImpl.isPaddingChunk(header);
    return new LogChunkImpl(chunkOffsetInFile, pageBuffer, chunkOffsetInBuffer, chunkLength, isPadding);
  }

  //@GuardedBy(allocationLock)
  private long allocateSpaceForChunk(int totalChunkLength) throws IOException {
    int pageSize = storage.pageSize();
    if (totalChunkLength > pageSize) {
      throw new AssertionError("totalRecordLength(=" + totalChunkLength + ") must fit the page(=" + pageSize + ")");
    }

    long chunkOffsetInFile = header.firstUnAllocatedOffset();
    long firstUnCommittedOffset = header.firstUnCommittedOffset();
    if (chunkOffsetInFile != firstUnCommittedOffset) {
      throw new AssertionError(
        "Invariant violation: " +
        "firstUnAllocatedCursor(=" + chunkOffsetInFile + ") must be == firstUnCommitted(=" + firstUnCommittedOffset + ")");
    }

    int chunkOffsetInPage = storage.toOffsetInPage(chunkOffsetInFile);
    int remainingOnPage = pageSize - chunkOffsetInPage;
    if (totalChunkLength <= remainingOnPage) {
      header.updateFirstUnAllocatedOffset(chunkOffsetInFile + totalChunkLength);
      return chunkOffsetInFile;
    }

    //not enough room on page for a chunk => fill page up with padding chunk
    // and try again on the next page:
    if (remainingOnPage >= LogChunkImpl.HEADER_SIZE) {
      //FIXME RC: could it be there is >2k leftover on the page, so remainsToPad > max chunk capacity?
      //          Actually, since padding chunks don't utilize allocated/committed cursors, that space in header could
      //          be re-used for larger length -- but first we need to find out do we really need larger padding chunks?

      header.updateFirstUnAllocatedOffset(chunkOffsetInFile + remainingOnPage);
      Page page = storage.pageByOffset(chunkOffsetInFile);
      LogChunkImpl.putPaddingChunk(remainingOnPage, page.rawPageBuffer(), chunkOffsetInPage, chunkOffsetInFile);
      header.updateFirstUnCommittedOffset(chunkOffsetInFile + remainingOnPage);

      return allocateSpaceForChunk(totalChunkLength);
    }

    //With chunk offsets AND lengths AND pageSize all 64b-aligned -- it could be either 0 or 8 bytes at the end
    // of the page -- all those options are processed above -- but never [1..8) bytes.
    throw new AssertionError(
      "Bug: remainingOnPage(=" + remainingOnPage + ") < RECORD_HEADER(=" + LogChunkImpl.HEADER_SIZE + ")," +
      "but chunks must be 64b-aligned, so it must never happen. " +
      "chunkOffsetInFile(=" + chunkOffsetInFile + "), " +
      "recordOffsetInPage(=" + chunkOffsetInPage + "), " +
      "totalRecordLength(=" + totalChunkLength + ")");
  }

  //@GuardedBy(allocationLock)

  /**
   * @return offset of the next chunk, given current chunk starting (=header) offset, and the chunk length.
   * Takes into account chunks alignment to word/pages boundaries, etc.
   */
  private long nextChunkOffset(long chunkOffsetInFile,
                               int totalChunkLength) {
    assert64bAligned(chunkOffsetInFile, "chunkOffsetInFile");
    long nextRecordOffset = roundUpToInt64(chunkOffsetInFile + totalChunkLength);

    int pageSize = storage.pageSize();
    int offsetInPage = storage.toOffsetInPage(nextRecordOffset);
    int remainingOnPage = pageSize - offsetInPage;
    if (remainingOnPage < LogChunkImpl.HEADER_SIZE) {
      throw new IllegalStateException(
        "remainingOnPage(=" + remainingOnPage + ") <= chunkHeader(=" + LogChunkImpl.HEADER_SIZE + ")");
    }
    return nextRecordOffset;
  }

  /**
   * reads all the data records untilOffset (exclusive)
   *
   * @return true if stopped by itself because untilOffset reached, or un-traversable record met, false
   * if iteration stopped by reader returning false
   */
  private boolean forEachRecord(@NotNull ChunkReader reader,
                                long untilOffset) throws IOException {
    int pageSize = storage.pageSize();
    for (long chunkOffsetInFile = FileHeader.HEADER_SIZE; chunkOffsetInFile < untilOffset; ) {

      Page page = storage.pageByOffset(chunkOffsetInFile);
      int chunkOffsetInPage = storage.toOffsetInPage(chunkOffsetInFile);
      ByteBuffer pageBuffer = page.rawPageBuffer();

      if (pageSize - chunkOffsetInPage < LogChunkImpl.HEADER_SIZE) {
        throw new CorruptedException(
          getClass().getSimpleName() + " corrupted: chunkOffsetInPage(=" + chunkOffsetInPage + ") less than " +
          "RECORD_HEADER(=" + LogChunkImpl.HEADER_SIZE + "b) left until " +
          "pageEnd(" + pageSize + ") -- all chunks must be 64b-aligned"
        );
      }

      int chunkHeader = LogChunkImpl.readHeader(pageBuffer, chunkOffsetInPage);
      if (chunkHeader == 0) {
        //the chunk wasn't even started to be written
        // -> can't read the following records since we don't know there they are
        return true;
      }

      int chunkLength = LogChunkImpl.unpackChunkLength(chunkHeader);

      if (LogChunkImpl.isDataChunk(chunkHeader)) {
        long chunkId = chunkOffsetToId(chunkOffsetInFile);

        if (!LogChunkImpl.isFitIntoPage(pageBuffer, chunkOffsetInPage, chunkLength)) {
          throw new IOException("chunk[" + chunkId + "][@" + chunkOffsetInFile + "].chunkLength(=" + chunkLength + "): " +
                                " is incorrect: page[0.." + pageBuffer.limit() + "]" +
                                moreDiagnosticInfo(chunkOffsetInFile));
        }

        LogChunkImpl chunk = readChunkAt(pageBuffer, chunkOffsetInPage, chunkOffsetInFile);
        boolean shouldContinue = reader.read(chunk);
        if (!shouldContinue) {
          return false;
        }
      }
      else if (LogChunkImpl.isPaddingChunk(chunkHeader)) {
        //just skip it
      }
      else {
        //if header != 0 => it must be either padding, or (uncommitted?) data record:
        throw new IOException("header(=" + chunkHeader + "](@offset=" + chunkOffsetInFile + "): not a padding, nor a data chunk");
      }


      chunkOffsetInFile = nextChunkOffset(chunkOffsetInFile, chunkLength);
    }

    return true;
  }

  private long recoverRegion(long nextRecordToBeCommittedOffset,
                             long nextRecordToBeAllocatedOffset) throws IOException {
    int pageSize = storage.pageSize();
    for (long chunkOffsetInFile = nextRecordToBeCommittedOffset;
         chunkOffsetInFile < nextRecordToBeAllocatedOffset; ) {
      Page page = storage.pageByOffset(chunkOffsetInFile);
      int chunkOffsetInPage = storage.toOffsetInPage(chunkOffsetInFile);
      ByteBuffer pageBuffer = page.rawPageBuffer();

      if (pageSize - chunkOffsetInPage <= LogChunkImpl.HEADER_SIZE) {
        throw new CorruptedException(
          getClass().getSimpleName() + " corrupted: chunkOffsetInPage(=" + chunkOffsetInPage + ") less than " +
          "RECORD_HEADER(=" + LogChunkImpl.HEADER_SIZE + "b) left until " +
          "pageEnd(" + pageSize + ") -- all chunks must be 64b-aligned"
        );
      }

      int chunkHeader = LogChunkImpl.readHeader(pageBuffer, chunkOffsetInPage);

      if (!LogChunkImpl.isHeaderSet(chunkHeader)) {
        //Can't recover farther: actual length of record is unknown
        return chunkOffsetInFile;
      }


      if (LogChunkImpl.isDataChunk(chunkHeader)) {
        //FIXME RC: check chunk _content_, and zero region (committed..capacity], if committed < allocated
        //else: record OK -> move to the next one
      }
      else if (LogChunkImpl.isPaddingChunk(chunkHeader)) {
        //padding is always committed -> move to the next one
      }
      else {
        //Unrecognizable garbage: we could just stop recovering here, and erase everything from
        //  here and up -- but for now I'd prefer to know how that could even happen (could it?)
        throw new CorruptedException("header(=" + chunkHeader + "](@offset=" + chunkOffsetInFile + "): not a padding, nor a data record");
      }

      int chunkLength = LogChunkImpl.unpackChunkLength(chunkHeader);
      chunkOffsetInFile = nextChunkOffset(chunkOffsetInFile, chunkLength);
    }
    return nextRecordToBeAllocatedOffset;
  }


  @VisibleForTesting
  static long chunkOffsetToId(long chunkOffsetInFile) {
    assert64bAligned(chunkOffsetInFile, "chunkOffsetInFile");

    //Since chunk offsets are 64b-aligned, we could drop 3 lowest bits from an offset while converting it to the id
    // => this way we could address wider offsets range with smaller ids

    //0 is considered invalid id (NULL_ID) everywhere in our code, so '+1' for first id to be 1
    return ((chunkOffsetInFile - FileHeader.HEADER_SIZE) >> 3) + 1;
  }

  private static long chunkIdToOffsetUnchecked(long chunkId) {
    return ((chunkId - 1) << 3) + FileHeader.HEADER_SIZE;
  }

  @VisibleForTesting
  static long chunkIdToOffset(long chunkId) {
    long offset = chunkIdToOffsetUnchecked(chunkId);
    if (!is64bAligned(offset)) {
      throw new IllegalArgumentException("chunkId(=" + chunkId + ") is invalid: chunkOffsetInFile(=" + offset + ") is not 64b-aligned");
    }
    return offset;
  }
}
