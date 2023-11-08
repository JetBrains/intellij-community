// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog;

import com.intellij.util.io.Unmappable;
import com.intellij.util.io.dev.mmapped.MMappedFileStorage;
import com.intellij.util.io.dev.mmapped.MMappedFileStorage.Page;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.blobstorage.ByteBufferReader;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import com.intellij.util.io.dev.appendonlylog.AppendOnlyLog;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.io.IOUtil.magicWordToASCII;
import static java.lang.invoke.MethodHandles.byteBufferViewVarHandle;
import static java.nio.ByteOrder.nativeOrder;

/**
 * There are other caveats, pitfalls, and dragons, so beware
 */
@ApiStatus.Internal
public final class AppendOnlyLogOverMMappedFile implements AppendOnlyLog, Unmappable {
  //@formatter:off
  private static final boolean MORE_DIAGNOSTIC_INFORMATION = getBooleanProperty("AppendOnlyLogOverMMappedFile.MORE_DIAGNOSTIC_INFORMATION", true);
  private static final boolean ADD_LOG_CONTENT = getBooleanProperty("AppendOnlyLogOverMMappedFile.ADD_LOG_CONTENT", true);
  /** How wide region around questionable record to dump for debug diagnostics (see {@link #dumpContentAroundId(long, int)}) */
  private static final int DEBUG_DUMP_REGION_WIDTH = 256;
  //@formatter:on

  private static final VarHandle INT32_OVER_BYTE_BUFFER = byteBufferViewVarHandle(int[].class, nativeOrder()).withInvokeExactBehavior();
  private static final VarHandle INT64_OVER_BYTE_BUFFER = byteBufferViewVarHandle(long[].class, nativeOrder()).withInvokeExactBehavior();

  /** We assume the mapped file is filled with 0 initially, so 0 for any field is the value before anything was set */
  private static final int UNSET_VALUE = 0;


  /** First header int32, used to recognize this storage's file type */
  public static final int MAGIC_WORD = IOUtil.asciiToMagicWord("AOLM");

  public static final int CURRENT_IMPLEMENTATION_VERSION = 1;

  public static final int MAX_PAYLOAD_SIZE = RecordLayout.RECORD_LENGTH_MASK;


  //TODO/MAYBE:
  //    1) connectionStatus: do we need it? Updates are atomic, every saved state is at least self-consistent.
  //       We could use (committed < allocated) as a marker of 'not everything was committed'
  //    2) Make record header 'recognizable': i.e. reserve first byte for type+committed only -- so we can recognize
  //       'false id' with high probability. This leaves us with 3bytes record length, which is still enough for
  //       the most applications

  public static final class HeaderLayout {
    public static final int MAGIC_WORD_OFFSET = 0;
    public static final int IMPLEMENTATION_VERSION_OFFSET = MAGIC_WORD_OFFSET + Integer.BYTES;

    public static final int EXTERNAL_VERSION_OFFSET = IMPLEMENTATION_VERSION_OFFSET + Integer.BYTES;

    /**
     * We align records to pages, hence storage.pageSize is a parameter of binary layout.
     * E.g. if we created the log with pageSize=1Mb, and re-open it with pageSize=512Kb -- now some records could
     * break page borders, which is incorrect. Hence we need to store pageSize, and check it on opening
     */
    public static final int PAGE_SIZE_OFFSET = EXTERNAL_VERSION_OFFSET + Integer.BYTES;


    /** Offset (in file) of the next-record-to-be-allocated */
    public static final int NEXT_RECORD_TO_BE_ALLOCATED_OFFSET = PAGE_SIZE_OFFSET + Integer.BYTES;
    /** Records with offset < recordsCommittedUpToOffset are guaranteed to be already written. */
    public static final int NEXT_RECORD_TO_BE_COMMITTED_OFFSET = NEXT_RECORD_TO_BE_ALLOCATED_OFFSET + Long.BYTES;

    public static final int FIRST_UNUSED_OFFSET = NEXT_RECORD_TO_BE_COMMITTED_OFFSET + Long.BYTES;

    //reserve [8 x int64] just in the case
    public static final int HEADER_SIZE = 8 * Long.BYTES;

    static {
      if (HEADER_SIZE < FIRST_UNUSED_OFFSET) {
        throw new ExceptionInInitializerError(
          "FIRST_UNUSED_OFFSET(" + FIRST_UNUSED_OFFSET + ") is > reserved HEADER_SIZE(=" + HEADER_SIZE + ")");
      }
    }

    //Header fields below are accessed only in ctor, hence do not require volatile/VarHandle. And they're
    // also accessed from the AppendOnlyLogFactory for eager file type/param check. So they are here, while
    // more 'private' header fields constantly modified during aolog lifetime are accessed in a different way
    // see set/getHeaderField()

    public static int readMagicWord(@NotNull ByteBuffer buffer) {
      return buffer.getInt(MAGIC_WORD_OFFSET);
    }

    public static int readImplementationVersion(@NotNull ByteBuffer buffer) {
      return buffer.getInt(IMPLEMENTATION_VERSION_OFFSET);
    }

    public static int readPageSize(@NotNull ByteBuffer buffer) {
      return buffer.getInt(PAGE_SIZE_OFFSET);
    }

    public static void putMagicWord(@NotNull ByteBuffer buffer,
                                    int magicWord) {
      buffer.putInt(MAGIC_WORD_OFFSET, magicWord);
    }

    public static void putImplementationVersion(@NotNull ByteBuffer buffer,
                                                int implVersion) {
      buffer.putInt(IMPLEMENTATION_VERSION_OFFSET, implVersion);
    }

    public static void putPageSize(@NotNull ByteBuffer buffer,
                                   int pageSize) {
      buffer.putInt(PAGE_SIZE_OFFSET, pageSize);
    }
  }


  private static final class RecordLayout {
    // Record = (header) + (payload)
    // Header = 32 bit, 32-bit-aligned (so it could be read/write as volatile, and not all CPU arch allow memory sync
    //          ops on non-aligned offsets)
    //          2 highest bits are reserved for record type and commitment status
    //          30 remaining bit: total record length, header included (i.e. payloadLength=recordLength-4)
    //          bit[31] (highest): record type, 0=regular record, 1=padding record
    //          bit[30]: record committed status, 0=record payload is fully written, 1=record payload is not yet
    //                   fully written

    /** 0==regular record, 1==padding record */
    private static final int RECORD_TYPE_MASK = 1 << 31;
    private static final int RECORD_TYPE_DATA = 0;
    private static final int RECORD_TYPE_PADDING = 1 << 31;

    /** 0==commited, 1==not commited */
    private static final int COMMITED_STATUS_MASK = 1 << 30;
    private static final int COMMITED_STATUS_OK = 1 << 30;
    private static final int COMMITED_STATUS_NOT_YET = 0;

    private static final int RECORD_LENGTH_MASK = 0x3FFF_FFFF;


    private static final int HEADER_OFFSET = 0;
    private static final int DATA_OFFSET = HEADER_OFFSET + Integer.BYTES;

    private static final int RECORD_HEADER_SIZE = Integer.BYTES;

    public static void putDataRecord(@NotNull ByteBuffer buffer,
                                     int offsetInBuffer,
                                     byte[] data) {
      INT32_OVER_BYTE_BUFFER.setVolatile(buffer, offsetInBuffer + HEADER_OFFSET, dataRecordHeader(data.length, /*commited: */false));
      buffer.put(offsetInBuffer + DATA_OFFSET, data);
      INT32_OVER_BYTE_BUFFER.setVolatile(buffer, offsetInBuffer + HEADER_OFFSET, dataRecordHeader(data.length, /*commited: */true));
    }

    public static void putDataRecord(@NotNull ByteBuffer buffer,
                                     int offsetInBuffer,
                                     int payloadSize,
                                     ByteBufferWriter writer) throws IOException {
      INT32_OVER_BYTE_BUFFER.setVolatile(buffer, offsetInBuffer + HEADER_OFFSET, dataRecordHeader(payloadSize, /*commited: */false));
      ByteBuffer writableRegionSlice = buffer.slice(offsetInBuffer + DATA_OFFSET, payloadSize).order(buffer.order());
      writer.write(writableRegionSlice);
      INT32_OVER_BYTE_BUFFER.setVolatile(buffer, offsetInBuffer + HEADER_OFFSET, dataRecordHeader(payloadSize, /*commited: */true));
    }

    public static void putPaddingRecord(@NotNull ByteBuffer buffer,
                                        int offsetInBuffer) {
      int remainsToPad = buffer.capacity() - offsetInBuffer;
      putPaddingRecord(buffer, offsetInBuffer, remainsToPad);
    }

    private static void putPaddingRecord(@NotNull ByteBuffer buffer,
                                         int offsetInBuffer,
                                         int remainsToPad) {
      if (remainsToPad < RECORD_HEADER_SIZE) {
        throw new IllegalArgumentException(
          "Can't create PaddingRecord for " + remainsToPad + "b leftover, must be >" + RECORD_HEADER_SIZE + " b left:" +
          "buffer.capacity(=" + buffer.capacity() + "), offsetInBuffer(=" + offsetInBuffer + ")");
      }
      int header = paddingRecordHeader(remainsToPad, /*commited: */true);
      INT32_OVER_BYTE_BUFFER.setVolatile(buffer, offsetInBuffer + HEADER_OFFSET, header);
    }


    /** generates data record header given length of payload and commited status */
    private static int dataRecordHeader(int payloadLength,
                                        boolean commited) {
      int totalRecordSize = payloadLength + RECORD_HEADER_SIZE;
      if ((totalRecordSize & COMMITED_STATUS_MASK) != 0
          || (totalRecordSize & RECORD_TYPE_MASK) != 0) {
        throw new IllegalArgumentException("totalRecordSize(=" + totalRecordSize + ") must have 2 highest bits 0");
      }

      return totalRecordSize | (commited ? COMMITED_STATUS_OK : COMMITED_STATUS_NOT_YET);
    }

    /** generates padding record header given total length of padding, and commited status */
    private static int paddingRecordHeader(int lengthToPad,
                                           boolean commited) {
      if (lengthToPad == 0) {
        throw new IllegalArgumentException("lengthToPad(=" + lengthToPad + ") must be >0");
      }
      int totalRecordSize = lengthToPad;
      if ((totalRecordSize & (~RECORD_LENGTH_MASK)) != 0) {
        throw new IllegalArgumentException("lengthToPad(=" + lengthToPad + ") must have 2 highest bits 0");
      }

      return totalRecordSize | RECORD_TYPE_MASK | (commited ? 0 : COMMITED_STATUS_MASK);
    }

    /** @return total record length (including header) */
    private static int readRecordLength(ByteBuffer buffer,
                                        int offsetInBuffer) {
      int header = readHeader(buffer, offsetInBuffer);
      return extractRecordLength(header);
    }

    /** @return total record length (including header) */
    private static int extractRecordLength(int header) {
      return roundUpToInt32(header & RECORD_LENGTH_MASK);
    }

    /** @return record payload length (excluding header) */
    private static int extractPayloadLength(int header) {
      return (header & RECORD_LENGTH_MASK) - RECORD_HEADER_SIZE;
    }

    private static boolean isPaddingHeader(int header) {
      return (header & RECORD_TYPE_MASK) == RECORD_TYPE_PADDING;
    }

    private static boolean isDataHeader(int header) {
      return (header & RECORD_TYPE_MASK) == RECORD_TYPE_DATA;
    }

    private static boolean isRecordCommitted(int header) {
      return (header & COMMITED_STATUS_MASK) == COMMITED_STATUS_OK;
    }

    public static int readHeader(ByteBuffer buffer,
                                 int offsetInBuffer) {
      return (int)INT32_OVER_BYTE_BUFFER.getVolatile(buffer, offsetInBuffer + HEADER_OFFSET);
    }

    public static int calculateRecordLength(int payloadSize) {
      return roundUpToInt32(payloadSize + RECORD_HEADER_SIZE);
    }

    /**
     * @return true if payload of payloadLength fits into a pageBuffer, given a record
     * header starts at recordOffsetInFile, false otherwise
     */
    public static boolean isFitIntoPage(ByteBuffer pageBuffer,
                                        int recordOffsetInPage,
                                        int payloadLength) {
      return recordOffsetInPage + DATA_OFFSET + payloadLength <= pageBuffer.limit();
    }
  }

  private final @NotNull MMappedFileStorage storage;
  /** Cache header page since we access it on each op (read/update cursors) */
  private transient Page headerPage;


  private final long startOfRecoveredRegion;
  private final long endOfRecoveredRegion;


  public AppendOnlyLogOverMMappedFile(@NotNull MMappedFileStorage storage) throws IOException {
    this.storage = storage;
    boolean fileIsEmpty = (storage.actualFileSize() == 0);

    int pageSize = storage.pageSize();
    if (!is32bAligned(pageSize)) {
      throw new IllegalArgumentException("storage.pageSize(=" + pageSize + ") must be 32b-aligned");
    }

    headerPage = storage.pageByOffset(0L);

    ByteBuffer headerPageBuffer = headerPage.rawPageBuffer();
    if (fileIsEmpty) {
      HeaderLayout.putMagicWord(headerPageBuffer, MAGIC_WORD);
      HeaderLayout.putImplementationVersion(headerPageBuffer, CURRENT_IMPLEMENTATION_VERSION);
      HeaderLayout.putPageSize(headerPageBuffer, pageSize);
    }
    else {
      checkFileParamsCompatible(storage.storagePath(), headerPageBuffer, pageSize);
    }


    long nextRecordToBeAllocatedOffset = getLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_ALLOCATED_OFFSET);
    if (nextRecordToBeAllocatedOffset == UNSET_VALUE) {//log is just created:
      nextRecordToBeAllocatedOffset = HeaderLayout.HEADER_SIZE;
      setLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_ALLOCATED_OFFSET, nextRecordToBeAllocatedOffset);
    }

    long nextRecordToBeCommittedOffset = getLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET);
    if (nextRecordToBeCommittedOffset == UNSET_VALUE) {//log is just created:
      nextRecordToBeCommittedOffset = HeaderLayout.HEADER_SIZE;
      setLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET, nextRecordToBeCommittedOffset);
    }

    if (nextRecordToBeCommittedOffset < nextRecordToBeAllocatedOffset) {
      //storage wasn't closed correctly -- probably forced restart? -> need to recover

      //For recovery we need 2 things:
      // - convert all non-commited records to padding records (we can't remove them, but can't recover either)
      // - clear everything after nextRecordToBeAllocatedOffset (log mechanics assumes file tail after
      //   .nextRecordToBeAllocatedOffset is always filled with 0)
      startOfRecoveredRegion = nextRecordToBeCommittedOffset;
      endOfRecoveredRegion = nextRecordToBeAllocatedOffset;
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
    }
    else {
      startOfRecoveredRegion = -1;
      endOfRecoveredRegion = -1;
    }


    setLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_ALLOCATED_OFFSET, nextRecordToBeAllocatedOffset);
    setLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET, nextRecordToBeCommittedOffset);
  }


  /**
   * @return version of the log implementation (i.e., this class) used to create the file.
   * Current version is {@link #CURRENT_IMPLEMENTATION_VERSION}
   */
  public int getImplementationVersion() {
    return HeaderLayout.readImplementationVersion(headerPage.rawPageBuffer());
  }

  /** @return version of _data_ stored in records -- up to the client to define/recognize it */
  public int getDataVersion() {
    return getIntHeaderField(HeaderLayout.EXTERNAL_VERSION_OFFSET);
  }

  public void setDataVersion(int version) {
    setIntHeaderField(HeaderLayout.EXTERNAL_VERSION_OFFSET, version);
  }

  /** @return true if the log wasn't properly closed and did some compensating recovery measured on open */
  public boolean wasRecoveryNeeded() {
    return startOfRecoveredRegion >= 0 && endOfRecoveredRegion > startOfRecoveredRegion;
  }

  public Path storagePath() {
    return storage.storagePath();
  }

  @Override
  public long append(@NotNull ByteBufferWriter writer,
                     int payloadSize) throws IOException {
    if (payloadSize < 0) {
      throw new IllegalArgumentException("Can't write record with payloadSize(=" + payloadSize + ") < 0");
    }
    int pageSize = storage.pageSize();
    if (payloadSize > pageSize - RecordLayout.RECORD_HEADER_SIZE) {
      throw new IllegalArgumentException("payloadSize(=" + payloadSize + ") is too big: " +
                                         "record with header must fit pageSize(=" + pageSize + ")");
    }

    int totalRecordLength = RecordLayout.calculateRecordLength(payloadSize);
    long recordOffsetInFile = allocateSpaceForRecord(totalRecordLength);
    assert32bAligned(recordOffsetInFile, "recordOffsetInFile");

    Page page = storage.pageByOffset(recordOffsetInFile);
    int offsetInPage = storage.toOffsetInPage(recordOffsetInFile);

    RecordLayout.putDataRecord(page.rawPageBuffer(), offsetInPage, payloadSize, writer);

    tryCommitRecord(recordOffsetInFile, totalRecordLength);

    return recordOffsetToId(recordOffsetInFile);
  }

  //MAYBE RC: Implementation below is a bit faster than default version (no lambda, no buffer slice).
  //          But does it worth it?
  //@Override
  //public long append(byte[] data) throws IOException {
  //  if (data.length == 0) {
  //    throw new IllegalArgumentException("Can't write record with length=0");
  //  }
  //  int totalRecordLength = data.length + RecordLayout.RECORD_HEADER_SIZE;
  //  long recordOffsetInFile = allocateSpaceForRecord(totalRecordLength);
  //
  //  Page page = storage.pageByOffset(recordOffsetInFile);
  //  int offsetInPage = storage.toOffsetInPage(recordOffsetInFile);
  //  RecordLayout.putDataRecord(page.rawPageBuffer(), offsetInPage, data);
  //
  //  tryCommitRecord(recordOffsetInFile, totalRecordLength);
  //
  //  return recordOffsetToId(recordOffsetInFile);
  //}

  @Override
  public <T> T read(long recordId,
                    @NotNull ByteBufferReader<T> reader) throws IOException {
    long recordOffsetInFile = recordIdToOffset(recordId);

    //RC: Records between 'committed' and 'allocated' cursors are not all fully written -> we permit
    //    reading the records that are still not fully commited. This is good from API-consistency
    //    PoV: if .append() returns an id -- that id must be valid for .read(id), even though some
    //    records _before_ that id are not yet finished.

    long recordsAllocatedUpTo = firstUnAllocatedOffset();
    if (recordOffsetInFile >= recordsAllocatedUpTo) {
      throw new IllegalArgumentException(
        "Can't read recordId(=" + recordId + ", offset: " + recordOffsetInFile + "]: " +
        "outside of allocated region [<" + recordsAllocatedUpTo + "] " +
        moreDiagnosticInfo(recordOffsetInFile));
    }


    Page page = storage.pageByOffset(recordOffsetInFile);
    int recordOffsetInPage = storage.toOffsetInPage(recordOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();

    int recordHeader = RecordLayout.readHeader(pageBuffer, recordOffsetInPage);
    if (!RecordLayout.isDataHeader(recordHeader)) {
      throw new IOException("record[" + recordId + "][@" + recordOffsetInFile + "] is " +
                            "PaddingRecord(header=" + Integer.toHexString(recordHeader) + ") -- i.e. has no data. " +
                            moreDiagnosticInfo(recordOffsetInFile)
      );
    }
    if (!RecordLayout.isRecordCommitted(recordHeader)) {
      throw new IOException("record[" + recordId + "][@" + recordOffsetInFile + "] is not commited: " +
                            "(header=" + Integer.toHexString(recordHeader) + ") either not yet written or corrupted. " +
                            moreDiagnosticInfo(recordOffsetInFile) +
                            (ADD_LOG_CONTENT ? "\n" + dumpContentAroundId(recordId, DEBUG_DUMP_REGION_WIDTH) : "")
      );
    }
    int payloadLength = RecordLayout.extractPayloadLength(recordHeader);
    if (!RecordLayout.isFitIntoPage(pageBuffer, recordOffsetInPage, payloadLength)) {
      throw new IOException("record[" + recordId + "][@" + recordOffsetInFile + "].payloadLength(=" + payloadLength + ") " +
                            "is incorrect: page[0.." + pageBuffer.limit() + "], " +
                            "committedUpTo: " + firstUnCommittedOffset() + ", allocatedUpTo: " + firstUnAllocatedOffset() + ". " +
                            moreDiagnosticInfo(recordOffsetInFile) +
                            (ADD_LOG_CONTENT ? "\n" + dumpContentAroundId(recordId, DEBUG_DUMP_REGION_WIDTH) : "")
      );
    }
    ByteBuffer recordDataSlice = pageBuffer.slice(recordOffsetInPage + RecordLayout.DATA_OFFSET, payloadLength)
      //.asReadOnlyBuffer()
      .order(pageBuffer.order());
    return reader.read(recordDataSlice);
  }

  @Override
  public boolean isValidId(long recordId) {
    if (recordId <= 0) {
      return false;
    }
    long recordOffset = recordIdToOffsetUnchecked(recordId);
    if (!is32bAligned(recordOffset)) {
      return false;
    }
    return recordOffset < firstUnAllocatedOffset();
  }

  @Override
  public boolean forEachRecord(@NotNull RecordReader reader) throws IOException {
    int pageSize = storage.pageSize();
    long firstUnallocatedOffset = firstUnAllocatedOffset();

    for (long recordOffsetInFile = HeaderLayout.HEADER_SIZE; recordOffsetInFile < firstUnallocatedOffset; ) {

      Page page = storage.pageByOffset(recordOffsetInFile);
      int recordOffsetInPage = storage.toOffsetInPage(recordOffsetInFile);
      ByteBuffer pageBuffer = page.rawPageBuffer();

      if (pageSize - recordOffsetInPage < RecordLayout.RECORD_HEADER_SIZE) {
        throw new IOException(
          getClass().getSimpleName() + " corrupted: recordOffsetInPage(=" + recordOffsetInPage + ") less than " +
          "RECORD_HEADER(=" + RecordLayout.RECORD_HEADER_SIZE + "b) left until " +
          "pageEnd(" + pageSize + ") -- all records must be 32b-aligned"
        );
      }

      int recordHeader = RecordLayout.readHeader(pageBuffer, recordOffsetInPage);
      if (recordHeader == 0) {
        //the record wasn't even started to be written
        // -> can't read the following records since we don't know there they are
        return true;
      }

      int recordLength = RecordLayout.extractRecordLength(recordHeader);

      if (RecordLayout.isDataHeader(recordHeader)) {
        if (RecordLayout.isRecordCommitted(recordHeader)) {
          int payloadLength = RecordLayout.extractPayloadLength(recordHeader);
          long recordId = recordOffsetToId(recordOffsetInFile);

          if (!RecordLayout.isFitIntoPage(pageBuffer, recordOffsetInPage, payloadLength)) {
            throw new IOException("record[" + recordId + "][@" + recordOffsetInFile + "].payloadLength(=" + payloadLength + "): " +
                                  " is incorrect: page[0.." + pageBuffer.limit() + "]" +
                                  moreDiagnosticInfo(recordOffsetInFile));
          }
          ByteBuffer recordDataSlice = pageBuffer.slice(recordOffsetInPage + RecordLayout.DATA_OFFSET, payloadLength)
            //.asReadOnlyBuffer()
            .order(pageBuffer.order());

          boolean shouldContinue = reader.read(recordId, recordDataSlice);
          if (!shouldContinue) {
            return false;
          }
        }//else: record not yet commited, we can't _read_ it -- but maybe _next_ record(s) are committed?
      }
      else if (RecordLayout.isPaddingHeader(recordHeader)) {
        //just skip it
      }
      else {
        //if header != 0 => it must be either padding, or (uncommitted?) data record:
        throw new IOException("header(=" + recordHeader + "](@offset=" + recordOffsetInFile + "): not a padding, nor a data record");
      }


      recordOffsetInFile = nextRecordOffset(recordOffsetInFile, recordLength);
    }

    return true;
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
    return firstUnAllocatedOffset() == HeaderLayout.HEADER_SIZE
           && firstUnCommittedOffset() == HeaderLayout.HEADER_SIZE;
  }

  @Override
  public void close() throws IOException {
    if (storage.isOpen()) {
      flush();
      storage.close();
      headerPage = null;//help GC unmap pages sooner
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
    return "AppendOnlyLogOverMMappedFile[" + storage.storagePath() + "]";
  }

  /**
   * Reads key storage params from the header byte buffer, and checks them against params supported by this
   * implementation. Throws {@link IOException} if there is an incompatibility.
   */
  public static void checkFileParamsCompatible(@NotNull Path storagePath,
                                               @NotNull ByteBuffer headerPageBuffer,
                                               int pageSize) throws IOException {
    int magicWord = HeaderLayout.readMagicWord(headerPageBuffer);
    if (magicWord != MAGIC_WORD) {
      throw new IOException(
        "[" + storagePath + "] is of incorrect type: " +
        ".magicWord(=" + magicWord + ", '" + magicWordToASCII(magicWord) + "') != " + MAGIC_WORD + " expected");
    }

    int implementationVersion = HeaderLayout.readImplementationVersion(headerPageBuffer);
    if (implementationVersion != CURRENT_IMPLEMENTATION_VERSION) {
      throw new IOException(
        "[" + storagePath + "].implementationVersion(=" + implementationVersion + ") is not supported: " +
        CURRENT_IMPLEMENTATION_VERSION + " is the currently supported version.");
    }

    int filePageSize = HeaderLayout.readPageSize(headerPageBuffer);
    if (pageSize != filePageSize) {
      throw new IOException(
        "[" + storagePath + "]: file created with pageSize=" + filePageSize +
        " but current storage.pageSize=" + pageSize);
    }
  }

  // ============== implementation: ======================================================================

  private long allocateSpaceForRecord(int totalRecordLength) throws IOException {
    int pageSize = storage.pageSize();
    if (totalRecordLength > pageSize) {
      throw new IllegalArgumentException("totalRecordLength(=" + totalRecordLength + ") must fit the page(=" + pageSize + ")");
    }
    while (true) {//CAS loop:
      long recordOffsetInFile = firstUnAllocatedOffset();
      int recordOffsetInPage = storage.toOffsetInPage(recordOffsetInFile);
      int remainingOnPage = pageSize - recordOffsetInPage;
      if (totalRecordLength <= remainingOnPage) {
        if (casFirstUnAllocatedOffset(recordOffsetInFile, recordOffsetInFile + totalRecordLength)) {
          return recordOffsetInFile;
        }
        continue;
      }
      //not enough room on page for a record => fill page up with padding record
      // and try again on the next page:
      if (remainingOnPage >= RecordLayout.RECORD_HEADER_SIZE) {
        if (casFirstUnAllocatedOffset(recordOffsetInFile, recordOffsetInFile + remainingOnPage)) {
          Page page = storage.pageByOffset(recordOffsetInFile);
          RecordLayout.putPaddingRecord(page.rawPageBuffer(), recordOffsetInPage);
        }//else: somebody else dealt with that offset -> retry anyway
        continue;
      }

      //With record offsets AND lengths AND pageSize all 32b-aligned -- it could be either 0 or 4 bytes at the end
      // of the page -- all those options processed above -- but never 1-2-3 bytes.
      throw new AssertionError("Bug: remainingOnPage(=" + remainingOnPage + ") < RECORD_HEADER(=" + RecordLayout.RECORD_HEADER_SIZE + ")," +
                               "but records must be 32b-aligned, so it must never happen. " +
                               "recordOffsetInFile(=" + recordOffsetInFile + "), " +
                               "recordOffsetInPage(=" + recordOffsetInPage + "), " +
                               "totalRecordLength(=" + totalRecordLength + ")");
    }
  }

  private void tryCommitRecord(long currentRecordOffsetInFile,
                               int totalRecordLength) throws IOException {
    long committedOffset = firstUnCommittedOffset();
    if (committedOffset == currentRecordOffsetInFile) {
      long nextRecordOffsetInFile = nextRecordOffset(currentRecordOffsetInFile, totalRecordLength);
      if (casFirstUnCommittedOffset(currentRecordOffsetInFile, nextRecordOffsetInFile)) {
        //Now we're responsible for moving the 'committed' pointer as much forward as possible:
        tryCommitFinalizedRecords();
      }
    }

    if (committedOffset < currentRecordOffsetInFile) {
      //some records before us are not yet committed

      //FIXME: Ideally, we shouldn't do anything here -- the most lagging thread is responsible for
      //       committing finalized records, to avoid useless concurrency on updating 'committed'
      //       cursor.
      //       But currently this logic breaks up on a end-of-page-padding record there currentRecord
      //       points to the actual record on the next page, while nextRecordToBeCommitted keeps pointing
      //       to padding record left on previous page.
      //       I work it around by trying to commit records always.
      //       Review it later, find way to avoid spending CPU
      tryCommitFinalizedRecords();

      return;
    }
  }

  private void tryCommitFinalizedRecords() throws IOException {
    while (true) {
      long firstYetUncommittedRecord = firstUnCommittedOffset();
      long allocatedUpTo = firstUnAllocatedOffset();
      if (firstYetUncommittedRecord == allocatedUpTo) {
        return; //nothing more to commit (yet)
      }
      Page page = storage.pageByOffset(firstYetUncommittedRecord);
      int offsetInPage = storage.toOffsetInPage(firstYetUncommittedRecord);
      int totalRecordLength = RecordLayout.readRecordLength(page.rawPageBuffer(), offsetInPage);
      if (totalRecordLength == 0) {
        return; //firstYetUncommittedRecord is not finalized (yet)
      }
      long nextUncommittedRecord = nextRecordOffset(firstYetUncommittedRecord, totalRecordLength);
      casFirstUnCommittedOffset(firstYetUncommittedRecord, nextUncommittedRecord);
    }
  }

  /**
   * @return offset of the next record, given current record starting (=header) offset, and the record length.
   * Takes into account record alignment to word/pages boundaries, etc.
   */
  private long nextRecordOffset(long recordOffsetInFile,
                                int totalRecordLength) {
    assert32bAligned(recordOffsetInFile, "recordOffsetInFile");
    long nextRecordOffset = roundUpToInt32(recordOffsetInFile + totalRecordLength);

    int pageSize = storage.pageSize();
    int offsetInPage = storage.toOffsetInPage(nextRecordOffset);
    int remainingOnPage = pageSize - offsetInPage;
    if (remainingOnPage < RecordLayout.RECORD_HEADER_SIZE) {
      throw new IllegalStateException(
        "remainingOnPage(=" + remainingOnPage + ") <= recordHeader(=" + RecordLayout.RECORD_HEADER_SIZE + ")");
    }
    return nextRecordOffset;
  }


  private long firstUnAllocatedOffset() {
    return getLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_ALLOCATED_OFFSET);
  }

  private boolean casFirstUnAllocatedOffset(long currentValue,
                                            long newValue) {
    return INT64_OVER_BYTE_BUFFER.compareAndSet(
      headerPage.rawPageBuffer(),
      HeaderLayout.NEXT_RECORD_TO_BE_ALLOCATED_OFFSET,
      currentValue, newValue
    );
  }

  private long firstUnCommittedOffset() {
    return getLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET);
  }

  private boolean casFirstUnCommittedOffset(long currentValue, long newValue) {
    return INT64_OVER_BYTE_BUFFER.compareAndSet(
      headerPage.rawPageBuffer(),
      HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET,
      currentValue, newValue
    );
  }


  private long recoverRegion(long nextRecordToBeCommittedOffset,
                             long nextRecordToBeAllocatedOffset) throws IOException {
    int pageSize = storage.pageSize();
    for (long offsetInFile = nextRecordToBeCommittedOffset;
         offsetInFile < nextRecordToBeAllocatedOffset; ) {
      Page page = storage.pageByOffset(offsetInFile);
      int recordOffsetInPage = storage.toOffsetInPage(offsetInFile);
      ByteBuffer pageBuffer = page.rawPageBuffer();

      if (pageSize - recordOffsetInPage <= RecordLayout.RECORD_HEADER_SIZE) {
        throw new IOException(
          getClass().getSimpleName() + " corrupted: recordOffsetInPage(=" + recordOffsetInPage + ") less than " +
          "RECORD_HEADER(=" + RecordLayout.RECORD_HEADER_SIZE + "b) left until " +
          "pageEnd(" + pageSize + ") -- all records must be 32b-aligned"
        );
      }

      int recordHeader = RecordLayout.readHeader(pageBuffer, recordOffsetInPage);
      int recordLength = RecordLayout.extractRecordLength(recordHeader);

      if (recordLength == 0) {
        //Can't recover farther: actual length of record is unknown length
        return offsetInFile;
      }
      if (RecordLayout.isDataHeader(recordHeader)) {
        if (!RecordLayout.isRecordCommitted(recordHeader)) {
          //Unfinished record: convert it to padding record
          RecordLayout.putPaddingRecord(pageBuffer, recordOffsetInPage, recordLength);
        }//else: record OK -> move to the next one
      }
      else if (RecordLayout.isPaddingHeader(recordHeader)) {
        //padding is always committed -> move to the next one
      }
      else {
        //Unrecognizable garbage: we could just stop recovering here, and erase everything from
        //  here and up -- but for now I'd prefer to know how that could even happen (could it?)
        throw new IOException("header(=" + recordHeader + "](@offset=" + offsetInFile + "): not a padding, nor a data record");
      }

      offsetInFile = nextRecordOffset(offsetInFile, recordLength);
    }
    return nextRecordToBeAllocatedOffset;
  }

  private String moreDiagnosticInfo(long recordOffsetInFile) {
    if (!MORE_DIAGNOSTIC_INFORMATION) {
      return "";
    }

    if (startOfRecoveredRegion < 0 && endOfRecoveredRegion < 0) {
      return "(There was no recovery, it can't be related to it)";
    }
    if (recordOffsetInFile >= startOfRecoveredRegion && recordOffsetInFile < endOfRecoveredRegion) {
      return "(Record is in the recovered region [" + startOfRecoveredRegion + ".." + endOfRecoveredRegion + ") " +
             "so it may be due to some un-recovered records)";
    }

    return "(There was a recovery so it may be due to some un-recovered records, " +
           "but the record is outside the region [" + startOfRecoveredRegion + ".." + endOfRecoveredRegion + ") recovered)";
  }

  /**
   * @return log records in a region [aroundRecordId-regionWidth..aroundRecordId+regionWidth],
   * one record per line. Record content formatted as hex-string
   */
  private String dumpContentAroundId(long aroundRecordId,
                                     int regionWidth) throws IOException {
    StringBuilder sb = new StringBuilder("Log content around id: " + aroundRecordId + " +/- " + regionWidth +
                                         " (first uncommitted offset: " + firstUnCommittedOffset() +
                                         ", first unallocated: " + firstUnAllocatedOffset() + ")\n");
    forEachRecord((recordId, buffer) -> {
      long nextRecordId = recordOffsetToId(
        nextRecordOffset(recordIdToOffset(recordId), buffer.remaining())
      );
      //MAYBE RC: only use insideQuestionableRecord? Seems like records around are of little use
      boolean insideQuestionableRecord = (recordId <= aroundRecordId && aroundRecordId <= nextRecordId);
      boolean insideNeighbourRegion = (aroundRecordId - regionWidth <= recordId
                                       && recordId <= aroundRecordId + regionWidth);

      if (insideQuestionableRecord || insideNeighbourRegion) {
        String bufferAsHex = IOUtil.toHexString(buffer);
        sb.append(insideQuestionableRecord ? "*" : "")
          .append("[id: ").append(recordId).append("][offset: ").append(recordIdToOffset(recordId)).append("][hex: ")
          .append(bufferAsHex).append("]\n");
      }
      return recordId <= aroundRecordId + regionWidth;
    });
    return sb.toString();
  }


  //MAYBE RC: since record offsets are now 32b-aligned, we could drop 2 lowest bits from an offset while
  //          converting it to the id -> this way we could address wider offsets range with int id

  @VisibleForTesting
  static long recordOffsetToId(long recordOffset) {
    assert32bAligned(recordOffset, "recordOffsetInFile");
    //0 is considered invalid id (NULL_ID) everywhere in our code, so '+1' for first id to be 1
    return recordOffset - HeaderLayout.HEADER_SIZE + 1;
  }

  @VisibleForTesting
  static long recordIdToOffset(long recordId) {
    long offset = recordIdToOffsetUnchecked(recordId);
    if (!is32bAligned(offset)) {
      throw new IllegalArgumentException("recordId(=" + recordId + ") is invalid: recordOffsetInFile(=" + offset + ") is not 32b-aligned");
    }
    return offset;
  }

  private static long recordIdToOffsetUnchecked(long recordId) {
    return recordId - 1 + HeaderLayout.HEADER_SIZE;
  }


  private int getIntHeaderField(int headerRelativeOffsetBytes) {
    return (int)INT32_OVER_BYTE_BUFFER.getVolatile(headerPage.rawPageBuffer(), headerRelativeOffsetBytes);
  }

  private long getLongHeaderField(int headerRelativeOffsetBytes) {
    return (long)INT64_OVER_BYTE_BUFFER.getVolatile(headerPage.rawPageBuffer(), headerRelativeOffsetBytes);
  }

  private void setIntHeaderField(int headerRelativeOffsetBytes,
                                 int headerFieldValue) {
    INT32_OVER_BYTE_BUFFER.setVolatile(headerPage.rawPageBuffer(), headerRelativeOffsetBytes, headerFieldValue);
  }

  private void setLongHeaderField(int headerRelativeOffsetBytes,
                                  long headerFieldValue) {
    INT64_OVER_BYTE_BUFFER.setVolatile(headerPage.rawPageBuffer(), headerRelativeOffsetBytes, headerFieldValue);
  }

  //================== alignment: ========================================================================
  // Record headers must be 32b-aligned so they could be accessed with volatile semantics -- because not
  // all CPU arch support unaligned access with memory sync semantics

  private static int roundUpToInt32(int value) {
    if (is32bAligned(value)) {
      return value;
    }
    return ((value >> 2) + 1) << 2;
  }

  private static long roundUpToInt32(long value) {
    if (is32bAligned(value)) {
      return value;
    }
    return ((value >> 2) + 1) << 2;
  }

  private static boolean is32bAligned(int value) {
    return (value & 0b11) == 0;
  }

  private static boolean is32bAligned(long value) {
    return (value & 0b11L) == 0;
  }

  private static void assert32bAligned(long value,
                                       @NotNull String name) {
    if (!is32bAligned(value)) {
      throw new AssertionError("Bug: " + name + "(=" + value + ") is not 32b-aligned");
    }
  }

  private static void assert32bAligned(int value,
                                       @NotNull String name) {
    if (!is32bAligned(value)) {
      throw new AssertionError("Bug: " + name + "(=" + value + ") is not 32b-aligned");
    }
  }
}
