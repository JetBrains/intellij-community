// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.appendonlylog;

import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.io.ContentTooBigException;
import com.intellij.platform.util.io.storages.AlignmentUtils;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.Unmappable;
import com.intellij.util.io.blobstorage.ByteBufferReader;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Objects;

import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.SystemProperties.getIntProperty;
import static com.intellij.util.io.IOUtil.magicWordToASCII;
import static java.lang.invoke.MethodHandles.byteBufferViewVarHandle;
import static java.nio.ByteOrder.nativeOrder;

/**
 * Implementation over memory-mapped file ({@link MMappedFileStorage}).
 * <p>
 * Thead-safe, non-blocking (leaving aside the fact that OS page management is not non-blocking).
 * <p>
 * Record size is limited by the underlying {@link MMappedFileStorage#pageSize()} (minus 4 bytes for record header)
 * <p>
 * Durability relies on OS: appended record is durable if OS not crash (i.e. not loosing mmapped file content).
 */
@ApiStatus.Internal
public final class AppendOnlyLogOverMMappedFile implements AppendOnlyLog, Unmappable {

  //@formatter:off
  /**
   * On error, provide more verbose diagnostic info about AOLog state (i.e. was there a recovery?) and content
   * around record in question
   */
  private static final boolean MORE_DIAGNOSTIC_INFORMATION = getBooleanProperty("AppendOnlyLogOverMMappedFile.MORE_DIAGNOSTIC_INFORMATION", true);

  /** Append to the exceptions/errors a dump (hex) of log's content around questionable region */
  private static final boolean APPEND_LOG_DUMP_ON_ERROR = getBooleanProperty("AppendOnlyLogOverMMappedFile.APPEND_LOG_DUMP_ON_ERROR", true);
  /** How wide region around questionable record to dump for debug diagnostics (see {@link #dumpContentAroundId(long, int, int)}) */
  private static final int DEBUG_DUMP_REGION_WIDTH = 256;
  /** If record content is larger -- don't print remaining part in the dump */
  private static final int MAX_RECORD_SIZE_TO_DUMP = getIntProperty("AppendOnlyLogOverMMappedFile.MAX_RECORD_SIZE_TO_DUMP", 256);
  //@formatter:on

  private static final VarHandle INT32_OVER_BYTE_BUFFER = byteBufferViewVarHandle(int[].class, nativeOrder()).withInvokeExactBehavior();
  private static final VarHandle INT64_OVER_BYTE_BUFFER = byteBufferViewVarHandle(long[].class, nativeOrder()).withInvokeExactBehavior();

  /** We assume the mapped file is filled with 0 initially, so 0 for any field is the value before anything was set */
  private static final int UNSET_VALUE = 0;


  /** First header int32, used to recognize this storage's file type */
  public static final int MAGIC_WORD = IOUtil.asciiToMagicWord("AOLM");

  // version bump (1->2): recordId assignment changed
  public static final int CURRENT_IMPLEMENTATION_VERSION = 2;

  public static final int MAX_PAYLOAD_SIZE = RecordLayout.RECORD_LENGTH_MASK;

  //Implementation details:
  //    1) 2 global cursors 'allocated' and 'committed', always{committed <= allocated}
  //       'Allocated' cursor is bumped _before_ actual record write.
  //       All the records < 'committed' cursor are fully written, and unmodifiable from now on.
  //       Records in [committed..allocated] region are being written right now.
  //    2) Record has 'length' and 'committed' status. Committed status is false initially, and set to true after all the
  //       writes to the record are finished. If there is a continuous sequence of 'committed' records right after 'committed'
  //       cursor => 'committed' cursor is moved forward through all the 'committed' records. I.e. there is an invariant:
  //       "for all the records < committed cursor record.committed=true"
  //    3) Record appending protocol:
  //       a) Allocate space for record: atomically move 'allocated' cursor for recordLength bytes forward
  //       b) Set record header (length=recordLength, committed=false)
  //       c) Write record content
  //       d) Set record header (committed=true)
  //       e) Check is there a continuous sequence of 'committed' records right after 'committed' cursor
  //          => atomically move 'committed' cursor forward as much as possible.
  //
  // Finer details:
  //    1) Alignment: records are int32 aligned, because volatile/atomic instructions universally work only on
  //       int32/int64-aligned addresses, and we need int32 volatile write for record header => record headers
  //       must be int32-aligned. Record length in a record header is _actual_ length of the record(content+header).
  //       Next record is (currentOffset+recordLength) rounded up to be int32-aligned.
  //
  //    2) Padding records: records are also page-aligned (MMappedFileStorage.pageSize). It was done mostly for
  //       simplification: it is possible to split the record between the pages, but it complicates code a lot,
  //       so I decided to avoid it. But page-alignment requires 'padding records' to fill the gap left if the
  //       record not fit the current page and must be moved to the next one entirely. Padding records are just
  //       records without content: they have length and committed status, but content is 'unused'. Padding
  //       records are also used to 'clean' unfinished records during recovery.
  //
  //    3) Recovery: if app crashes, append-only log is able to keep its state, because OS is responsible for
  //       flushing memory-mapped file content regardless of app status. Records < committed cursor are fully
  //       written, so no problems with them. Records in [committed..allocated] range could be fully of partially
  //       written, so we need to sort them out: if we see (committed < allocated) on log opening => we execute
  //       'recovery' protocol to find out which records from that range were finished, and which were not. For
  //       that we scan [committed..allocated] record-by-record, and check record 'committed' status. 'Un-committed'
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
  //    2. Current implementation is not fully durable (even if OS don't crash): it could be .append() returns recordId,
  //       but after app crash such record disappears. This could happen because record becomes unreachable for recovery
  //       if some previous record header wasn't put in place. And this could happen because there is small but non-0
  //       time window between record allocation ('allocated' cursor bumped up) and the write of 'uncommited' record header
  //       with record length.
  //       If app crashes during that window, record is left in not only uncommitted state, but in 'unknown length' state,
  //       which means all the records after it can't be reached during recovery.
  //       This could be solved by sacrificing 'non-blocking' property: we must lock {allocate record, put down 'uncommited'
  //       header}, so following record append could NOT be started concurrently with those 2 actions. This is enough to get
  //       rid of that hole in a durability. It is not a big sacrifice, really: those 2 actions are, basically, just 2 memory
  //       writes (but keep in mind it is a writes to _mmapped_ memory, so it could be an IO behind the scene), so the lock
  //       is very short, and very unlikely ever contended -- but still :)
  //

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
    /** Records with offset < recordsCommittedUpToOffset are guaranteed to be all finished (written). */
    public static final int NEXT_RECORD_TO_BE_COMMITTED_OFFSET = NEXT_RECORD_TO_BE_ALLOCATED_OFFSET + Long.BYTES;

    /**
     * int32: total number of data records committed to the log.
     * Only data records counted, padding records are not counted here -- they considered to be an implementation detail
     * which should not be visible outside.
     * Only committed records counted -- i.e. those < commited cursor
     */
    public static final int RECORDS_COUNT_OFFSET = NEXT_RECORD_TO_BE_COMMITTED_OFFSET + Long.BYTES;

    /**
     * int32: opened(=1)/closed(=0).
     * Ideally, append-only log doesn't need 'was closed properly' field/status -- nextRecordXXX cursors are enough
     * to identify finalized/not finalized records, and recover that could be recovered (see ctor for details).
     * This is true even if app crashed/killed, but since OS is responsible for persisting mmapped buffers changes
     * even if app crashed.
     * But if OS itself crashed -- mmapped buffers content could be persisted or lost unpredictably, and all sorts
     * of inconsistencies could arise (see IJPL-1016 comments for examples).
     * Possibility of reliable recovery after an OS crash is doubtful, but at least we could identify such a scenario
     * -- this is what the field is for.
     */
    public static final int STORAGE_STATUS = RECORDS_COUNT_OFFSET + Integer.BYTES;
    private static final int STORAGE_STATUS_OPENED = 1;
    private static final int STORAGE_STATUS_CLOSED = 0;

    public static final int FIRST_UNUSED_OFFSET = STORAGE_STATUS + Integer.BYTES;

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

    /** generates data record header given length of payload and commited status */
    private static int dataRecordHeader(int payloadLength,
                                        boolean commited) {
      int totalRecordSize = payloadLength + RECORD_HEADER_SIZE;
      if ((totalRecordSize & (~RECORD_LENGTH_MASK)) != 0) {
        throw new IllegalArgumentException("totalRecordSize(=" + totalRecordSize + ") must have 2 highest bits 0");
      }

      return totalRecordSize | (commited ? COMMITED_STATUS_OK : COMMITED_STATUS_NOT_YET);
    }

    private static void putPaddingRecord(@NotNull ByteBuffer buffer,
                                         int offsetInBuffer,
                                         int remainsToPad) {
      if (remainsToPad < RECORD_HEADER_SIZE) {
        throw new IllegalArgumentException(
          "Can't create PaddingRecord for " + remainsToPad + "b leftover, must be >" + RECORD_HEADER_SIZE + " b left:" +
          "buffer.capacity(=" + buffer.capacity() + "), offsetInBuffer(=" + offsetInBuffer + ")");
      }
      int header = paddingRecordHeader(remainsToPad);
      INT32_OVER_BYTE_BUFFER.setVolatile(buffer, offsetInBuffer + HEADER_OFFSET, header);
    }

    /** generates padding record header given total length of padding, and commited status */
    private static int paddingRecordHeader(int lengthToPad) {
      if (lengthToPad == 0) {
        throw new IllegalArgumentException("lengthToPad(=" + lengthToPad + ") must be >0");
      }
      int totalRecordSize = lengthToPad;
      if ((totalRecordSize & (~RECORD_LENGTH_MASK)) != 0) {
        throw new IllegalArgumentException("lengthToPad(=" + lengthToPad + ") must have 2 highest bits 0");
      }

      //padding record has no content => immediately committed:
      return totalRecordSize | RECORD_TYPE_MASK | COMMITED_STATUS_OK;
    }

    /** @return total record length (including header) */
    private static int readRecordLength(ByteBuffer buffer,
                                        int offsetInBuffer) {
      int header = readHeader(buffer, offsetInBuffer);
      return extractRecordLength(header);
    }

    /** @return total record length (including header) */
    private static int extractRecordLength(int header) {
      return AlignmentUtils.roundUpToInt32(header & RECORD_LENGTH_MASK);
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
      return AlignmentUtils.roundUpToInt32(payloadSize + RECORD_HEADER_SIZE);
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
  private transient MMappedFileStorage.Page headerPage;


  private final long startOfRecoveredRegion;
  private final long endOfRecoveredRegion;

  /**
   * Was the storage closed properly during the previous session?
   * Value is set in constructor and fixed for a lifecycle of the instance
   */
  private final boolean wasClosedProperly;


  public AppendOnlyLogOverMMappedFile(@NotNull MMappedFileStorage storage) throws IOException {
    this.storage = storage;
    boolean fileIsEmpty = (storage.actualFileSize() == 0);

    int pageSize = storage.pageSize();
    if (!AlignmentUtils.is32bAligned(pageSize)) {
      throw new IllegalArgumentException("storage.pageSize(=" + pageSize + ") must be 32b-aligned");
    }

    headerPage = storage.pageByOffset(0L);

    ByteBuffer headerPageBuffer = headerPageBuffer();
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

      //records count could be incorrect if wasn't properly closed => re-count records:
      //FIXME RC: there is still a gap -- committed cursor update and recordsCount increment are not atomic,
      //          so could be an app crash in the moment committed cursor has been already updated, but recordsCount
      //          hasn't been incremented yet => we will not do recovery, because (committed==allocated), but records
      //          count is actually lagging behind.
      IntRef recordsCount = new IntRef(0);
      forEachRecord((recordId, buffer) -> {
        recordsCount.inc();
        return true;
      }, successfullyRecoveredUntil);
      setIntHeaderField(HeaderLayout.RECORDS_COUNT_OFFSET, recordsCount.get());
    }
    else {
      startOfRecoveredRegion = -1;
      endOfRecoveredRegion = -1;
    }

    wasClosedProperly = (getIntHeaderField(HeaderLayout.STORAGE_STATUS) == HeaderLayout.STORAGE_STATUS_CLOSED);
    setIntHeaderField(HeaderLayout.STORAGE_STATUS, HeaderLayout.STORAGE_STATUS_OPENED);
    storage.fsync();//make sure 'opened' status persists

    setLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_ALLOCATED_OFFSET, nextRecordToBeAllocatedOffset);
    setLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET, nextRecordToBeCommittedOffset);
  }


  /**
   * @return version of the log implementation (i.e., this class) used to create the file.
   * Current version is {@link #CURRENT_IMPLEMENTATION_VERSION}
   */
  public int getImplementationVersion() throws IOException {
    return HeaderLayout.readImplementationVersion(headerPageBuffer());
  }

  /** @return version of _data_ stored in records -- up to the client to define/recognize it */
  public int getDataVersion() throws IOException {
    return getIntHeaderField(HeaderLayout.EXTERNAL_VERSION_OFFSET);
  }

  public void setDataVersion(int version) throws IOException {
    setIntHeaderField(HeaderLayout.EXTERNAL_VERSION_OFFSET, version);
  }

  @Override
  public int recordsCount() throws IOException {
    return getIntHeaderField(HeaderLayout.RECORDS_COUNT_OFFSET);
  }

  /** @return arbitrary (user-defined) value from the Log's header, previously set by {@link #setUserDefinedHeaderField(int, int)} */
  public int getUserDefinedHeaderField(int fieldNo) throws IOException {
    int headerOffset = HeaderLayout.FIRST_UNUSED_OFFSET + fieldNo * Integer.BYTES;
    return getIntHeaderField(headerOffset);
  }

  /**
   * Sets arbitrary (user-defined) value in a Log's header.
   * There are 5 slots fieldNo=[0..5] available so far
   */
  public void setUserDefinedHeaderField(int fieldNo,
                                        int headerFieldValue) throws IOException {
    int headerOffset = HeaderLayout.FIRST_UNUSED_OFFSET + fieldNo * Integer.BYTES;
    setIntHeaderField(headerOffset, headerFieldValue);
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
      throw new ContentTooBigException("payloadSize(=" + payloadSize + ") is too big: " +
                                       "record with header must fit pageSize(=" + pageSize + ")");
    }

    int totalRecordLength = RecordLayout.calculateRecordLength(payloadSize);
    long recordOffsetInFile = allocateSpaceForRecord(totalRecordLength);
    AlignmentUtils.assert32bAligned(recordOffsetInFile, "recordOffsetInFile");

    MMappedFileStorage.Page page = storage.pageByOffset(recordOffsetInFile);
    int offsetInPage = storage.toOffsetInPage(recordOffsetInFile);

    //FIXME RC: There is a window between .allocateSpaceForRecord() and writing record header (with length) inside .putDataRecord().
    //          If app crashes during that window, current record is lost -- and not only current, but all records after it,
    //          even if those records were completely written and commited. This is because without knowing the length of
    //          current record -- subsequent records become unreachable for recovery.
    //          This is undesirable, because the subsequent record's ids could already be stored -- i.e. log doesn't provide
    //          the guarantee 'if append() return the id -- the record is guaranteed to be written and accessible after crash'.
    //          The window mentioned is narrow, and the guarantee above can't be provided anyway if OS crashes and in-memory
    //          content of mmapped-file is lost. But still, it is desirable to keep the guarantee at least for scenarios without
    //          OS crash.
    //          This could be done by using lock instead of CAS, and doing both 'allocated' cursor update _and_ stamping record
    //          length into the record header under the lock. Both operations are fast, so lock will be very narrow, and unlikely
    //          ever contended
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


    MMappedFileStorage.Page page = storage.pageByOffset(recordOffsetInFile);
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
                            (APPEND_LOG_DUMP_ON_ERROR
                             ? "\n" +
                               dumpContentAroundId(recordId, DEBUG_DUMP_REGION_WIDTH, MAX_RECORD_SIZE_TO_DUMP)
                             : "")
      );
    }
    int payloadLength = RecordLayout.extractPayloadLength(recordHeader);
    if (!RecordLayout.isFitIntoPage(pageBuffer, recordOffsetInPage, payloadLength)) {
      throw new IOException("record[" + recordId + "][@" + recordOffsetInFile + "].payloadLength(=" + payloadLength + ") " +
                            "is incorrect: page[0.." + pageBuffer.limit() + "], " +
                            "committedUpTo: " + firstUnCommittedOffset() + ", allocatedUpTo: " + firstUnAllocatedOffset() + ". " +
                            moreDiagnosticInfo(recordOffsetInFile) +
                            (APPEND_LOG_DUMP_ON_ERROR
                             ? "\n" +
                               dumpContentAroundId(recordId, DEBUG_DUMP_REGION_WIDTH, MAX_RECORD_SIZE_TO_DUMP)
                             : "")
      );
    }
    ByteBuffer recordDataSlice = pageBuffer.slice(recordOffsetInPage + RecordLayout.DATA_OFFSET, payloadLength)
      //.asReadOnlyBuffer()
      .order(pageBuffer.order());
    return reader.read(recordDataSlice);
  }

  @Override
  public boolean isValidId(long recordId) throws IOException {
    if (recordId <= 0) {
      return false;
    }
    long recordOffset = recordIdToOffsetUnchecked(recordId);
    if (!AlignmentUtils.is32bAligned(recordOffset)) {
      return false;
    }
    return recordOffset < firstUnAllocatedOffset();
  }

  @Override
  public boolean forEachRecord(@NotNull RecordReader reader) throws IOException {
    long firstUnallocatedOffset = firstUnAllocatedOffset();
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
  public boolean isEmpty() throws IOException {
    return firstUnAllocatedOffset() == HeaderLayout.HEADER_SIZE
           && firstUnCommittedOffset() == HeaderLayout.HEADER_SIZE;
  }

  /**
   * Was the storage closed properly during the previous session?
   * Value is determined during storage opening, and kept fixed for a lifecycle of the instance
   */
  public boolean wasClosedProperly() {
    return wasClosedProperly;
  }

  @Override
  public synchronized void close() throws IOException {
    if (storage.isOpen()) {
      setIntHeaderField(HeaderLayout.STORAGE_STATUS, HeaderLayout.STORAGE_STATUS_CLOSED);

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
    return "AppendOnlyLogOverMMappedFile[" + storage.storagePath() + "]{wasClosedProperly: " + wasClosedProperly + "}";
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
          MMappedFileStorage.Page page = storage.pageByOffset(recordOffsetInFile);
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
    //FIXME: This method involves unnecessary contention -- in current implementation _each_ thread finalizing
    //       it's record also make an attempt to commit all records before the current one, and _all_ such
    //       threads compete on updating 'commited' cursor.
    //
    //       This contention could be removed entirely by stating that only the 'most lagging' thread -- i.e. the
    //       thread for which currentRecordOffsetInFile==firstUncommitedOffset() -- should update the 'commited'
    //       cursor. This is very natural way to do it, not only because of less contention, but also because in
    //       the most cases threads finalize their records in the same order they allocate them => each thread
    //       commits it's own record _only_. And in such case the whole update is simplified down to the single
    //       uncontended CAS, because everything needed for the update (=totalRecordLength) is already passed in
    //       as param -- i.e. we don't even need re-read record header => the most frequent case also becomes the
    //       fastest then.
    //
    //       Unfortunately, this logic currently breaks down because of end-of-page-padding record (see allocateSpaceForRecord):
    //       end-of-page-padding is inserted before 'current' record, so currentRecordOffset points to the actual
    //       record on the next page, while .nextRecordToBeCommitted keeps pointing to padding record, left on
    //       previous page. Thus currentRecordOffsetInFile!=firstUncommitedOffset() even though current thread
    //       _is_ the most lagging -- and after single such fault commited cursor is never updated anymore.
    ///
    //       This is not a fundamental flaw, just a feature of current implementation, it could be fixed. But
    //       right now I postpone the fix, and work it around by made _every_ thread responsible for committing
    //       finalized records.
    //       Review it later, find way to avoid spending CPU on useless contention

    tryCommitFinalizedRecords();
  }

  private void tryCommitFinalizedRecords() throws IOException {
    CAS_LOOP:
    while (true) {
      long firstUnCommittedRecordOffset = firstUnCommittedOffset();

      long nextUncommittedRecordOffset = firstUnCommittedRecordOffset;
      long allocatedUpTo = firstUnAllocatedOffset();
      int dataRecordsToCommit = 0;//padding records not counted
      while (nextUncommittedRecordOffset < allocatedUpTo) {//scanning through all finalized-not-yet-commited records
        MMappedFileStorage.Page page = storage.pageByOffset(nextUncommittedRecordOffset);
        int offsetInPage = storage.toOffsetInPage(nextUncommittedRecordOffset);
        int recordHeader = RecordLayout.readHeader(page.rawPageBuffer(), offsetInPage);
        int totalRecordLength = RecordLayout.extractRecordLength(recordHeader);
        if (totalRecordLength == 0) {
          break; //record is not finalized (yet)
        }
        if (RecordLayout.isDataHeader(recordHeader)) {
          dataRecordsToCommit++;
        }

        nextUncommittedRecordOffset = nextRecordOffset(nextUncommittedRecordOffset, totalRecordLength);
      }

      if (nextUncommittedRecordOffset == firstUnCommittedRecordOffset) {
        return;
      }

      if (!casFirstUnCommittedOffset(firstUnCommittedRecordOffset, nextUncommittedRecordOffset)) {
        continue CAS_LOOP;
      }

      addToDataRecordsCount(dataRecordsToCommit);
      return;
    }
  }

  /**
   * @return offset of the next record, given current record starting (=header) offset, and the record length.
   * Takes into account record alignment to word/pages boundaries, etc.
   */
  private long nextRecordOffset(long recordOffsetInFile,
                                int totalRecordLength) {
    AlignmentUtils.assert32bAligned(recordOffsetInFile, "recordOffsetInFile");
    long nextRecordOffset = AlignmentUtils.roundUpToInt32(recordOffsetInFile + totalRecordLength);

    int pageSize = storage.pageSize();
    int offsetInPage = storage.toOffsetInPage(nextRecordOffset);
    int remainingOnPage = pageSize - offsetInPage;
    if (remainingOnPage < RecordLayout.RECORD_HEADER_SIZE) {
      throw new IllegalStateException(
        "remainingOnPage(=" + remainingOnPage + ") <= recordHeader(=" + RecordLayout.RECORD_HEADER_SIZE + ")");
    }
    return nextRecordOffset;
  }


  private long firstUnAllocatedOffset() throws IOException {
    return getLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_ALLOCATED_OFFSET);
  }

  private boolean casFirstUnAllocatedOffset(long currentValue,
                                            long newValue) throws IOException {
    return INT64_OVER_BYTE_BUFFER.compareAndSet(
      headerPageBuffer(),
      HeaderLayout.NEXT_RECORD_TO_BE_ALLOCATED_OFFSET,
      currentValue, newValue
    );
  }

  private long firstUnCommittedOffset() throws IOException {
    return getLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET);
  }

  private boolean casFirstUnCommittedOffset(long currentValue, long newValue) throws IOException {
    return INT64_OVER_BYTE_BUFFER.compareAndSet(
      headerPageBuffer(),
      HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET,
      currentValue, newValue
    );
  }

  private int addToDataRecordsCount(int recordsCommitted) throws IOException {
    return (int)INT32_OVER_BYTE_BUFFER.getAndAdd(
      headerPageBuffer(),
      HeaderLayout.RECORDS_COUNT_OFFSET,
      recordsCommitted
    );
  }

  /**
   * reads all the data records untilOffset (exclusive)
   *
   * @return true if stopped by itself because untilOffset reached, or untraverseable record met, false
   * if iteration stopped by reader returning false
   */
  private boolean forEachRecord(@NotNull RecordReader reader,
                                long untilOffset) throws IOException {
    int pageSize = storage.pageSize();
    for (long recordOffsetInFile = HeaderLayout.HEADER_SIZE; recordOffsetInFile < untilOffset; ) {

      MMappedFileStorage.Page page = storage.pageByOffset(recordOffsetInFile);
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
        //TODO RC: enable the check below after VFS version bump (currently it causes all the aologs to become
        //         corrupted, since all them previously missed 'committed' bit in padding records
        //if (!RecordLayout.isRecordCommitted(recordHeader)) {
        //  throw new IOException("padding.header("+recordHeader+") is not committed -- bug?")
        //}

        //just skip it
      }
      else {
        //if header != 0 => it must be either padding, or (uncommitted?) data record:
        throw new IOException("header(=" + recordHeader + "](@offset=" + recordOffsetInFile + "): not a padding, nor a data record" +
                              moreDiagnosticInfo(recordOffsetInFile));
      }


      recordOffsetInFile = nextRecordOffset(recordOffsetInFile, recordLength);
    }

    return true;
  }

  private long recoverRegion(long nextRecordToBeCommittedOffset,
                             long nextRecordToBeAllocatedOffset) throws IOException {
    int pageSize = storage.pageSize();
    for (long offsetInFile = nextRecordToBeCommittedOffset;
         offsetInFile < nextRecordToBeAllocatedOffset; ) {
      MMappedFileStorage.Page page = storage.pageByOffset(offsetInFile);
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
        //Can't recover farther: actual length of record is unknown
        return offsetInFile;
      }
      if (RecordLayout.isDataHeader(recordHeader)) {
        if (!RecordLayout.isRecordCommitted(recordHeader)) {
          //Unfinished record: convert it to padding record
          RecordLayout.putPaddingRecord(pageBuffer, recordOffsetInPage, recordLength);
        }//else: record OK -> move to the next one
      }
      else if (RecordLayout.isPaddingHeader(recordHeader)) {
        //TODO RC: enable the check below after VFS version bump (currently it causes all the aologs to become
        //         corrupted, since all them previously missed 'committed' bit in padding records
        //if (!RecordLayout.isRecordCommitted(recordHeader)) {
        //  throw new IOException("padding.header("+recordHeader+") is not committed -- bug?")
        //}

        //padding must be always committed -> move to the next one
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
      return "(There was no recovery, it can't be related to it" +
             (wasClosedProperly ? "" : " -- but storage wasn't closed properly") +
             ")";
    }
    if (recordOffsetInFile >= startOfRecoveredRegion && recordOffsetInFile < endOfRecoveredRegion) {
      return "(Record is in the recovered region [" + startOfRecoveredRegion + ".." + endOfRecoveredRegion + ") " +
             (wasClosedProperly ? "" : " and storage wasn't closed properly, ") +
             "so it may be due to some un-recovered records" +
             ")";
    }

    return "(There was a recovery " +
           (wasClosedProperly ? "" : "and storage wasn't closed properly, ") +
           "so it may be due to some un-recovered records, " +
           "but the record is outside the region [" + startOfRecoveredRegion + ".." + endOfRecoveredRegion + ") recovered)";
  }

  /**
   * @return log records in a region [aroundRecordId-regionWidth..aroundRecordId+regionWidth],
   * one record per line.
   * Record content formatted as hex-string.
   * If fecord is larger than maxRecordSizeToDump -- first maxRecordSizeToDump bytes dumped, with '...' at the end
   */
  private String dumpContentAroundId(long aroundRecordId,
                                     int regionWidth,
                                     int maxRecordSizeToDump) throws IOException {
    StringBuilder sb = new StringBuilder("Log content around id: " + aroundRecordId + " +/- " + regionWidth +
                                         " (first uncommitted offset: " + firstUnCommittedOffset() +
                                         ", first unallocated: " + firstUnAllocatedOffset() + ")\n");
    forEachRecord((recordId, buffer) -> {
      long recordOffset = recordIdToOffset(recordId);
      int recordSize = buffer.remaining();

      long nextRecordId = recordOffsetToId(nextRecordOffset(recordOffset, recordSize));

      //MAYBE RC: only use insideQuestionableRecord? Seems like records around are of little use
      boolean insideQuestionableRecord = (recordId <= aroundRecordId && aroundRecordId <= nextRecordId);
      boolean insideNeighbourRegion = (aroundRecordId - regionWidth <= recordId
                                       && recordId <= aroundRecordId + regionWidth);

      if (insideQuestionableRecord || insideNeighbourRegion) {
        String bufferAsHex = recordSize > maxRecordSizeToDump ?
                             IOUtil.toHexString(buffer.limit(buffer.position() + maxRecordSizeToDump)) + " ... " :
                             IOUtil.toHexString(buffer);
        sb.append(insideQuestionableRecord ? "*" : "")
          .append("[id: ").append(recordId).append(']')
          .append("[offset: ").append(recordOffset).append(']')
          .append("[len: ").append(recordSize).append(']')
          .append("[hex: ").append(bufferAsHex).append("]\n");
      }
      return recordId <= aroundRecordId + regionWidth;
    });
    return sb.toString();
  }


  //MAYBE RC: since record offsets are now 32b-aligned, we could drop 2 lowest bits from an offset while
  //          converting it to the id -> this way we could address wider offsets range with int id

  @VisibleForTesting
  static long recordOffsetToId(long recordOffset) {
    AlignmentUtils.assert32bAligned(recordOffset, "recordOffsetInFile");
    //recordOffset is int32-aligned, 2 lowest bits are 0, we could drop them, and make recordId smaller
    //0 is considered invalid id (NULL_ID) everywhere in our code, so '+1' for first id to be 1
    return ((recordOffset - HeaderLayout.HEADER_SIZE) >> 2) + 1;
  }

  @VisibleForTesting
  static long recordIdToOffset(long recordId) {
    if (recordId <= 0) {
      throw new IllegalArgumentException("recordId(=" + recordId + ") is negative or NULL_ID -- can't be read");
    }
    long offset = recordIdToOffsetUnchecked(recordId);
    if (!AlignmentUtils.is32bAligned(offset)) {
      throw new IllegalArgumentException("recordId(=" + recordId + ") is invalid: recordOffsetInFile(=" + offset + ") is not 32b-aligned");
    }
    return offset;
  }

  private static long recordIdToOffsetUnchecked(long recordId) {
    return ((recordId - 1) << 2) + HeaderLayout.HEADER_SIZE;
  }


  private ByteBuffer headerPageBuffer() throws IOException {
    MMappedFileStorage.Page _headerPage = headerPage;
    if(_headerPage == null) {
      throw new ClosedStorageException("["+storagePath()+"] is already closed");
    }
    return _headerPage.rawPageBuffer();
  }

  private int getIntHeaderField(int headerRelativeOffsetBytes) throws IOException {
    Objects.checkIndex(headerRelativeOffsetBytes, HeaderLayout.HEADER_SIZE - Integer.BYTES + 1);
    return (int)INT32_OVER_BYTE_BUFFER.getVolatile(headerPageBuffer(), headerRelativeOffsetBytes);
  }

  private long getLongHeaderField(int headerRelativeOffsetBytes) throws IOException {
    Objects.checkIndex(headerRelativeOffsetBytes, HeaderLayout.HEADER_SIZE - Long.BYTES + 1);
    return (long)INT64_OVER_BYTE_BUFFER.getVolatile(headerPageBuffer(), headerRelativeOffsetBytes);
  }

  private void setIntHeaderField(int headerRelativeOffsetBytes,
                                 int headerFieldValue) throws IOException {
    Objects.checkIndex(headerRelativeOffsetBytes, HeaderLayout.HEADER_SIZE - Integer.BYTES + 1);
    INT32_OVER_BYTE_BUFFER.setVolatile(headerPageBuffer(), headerRelativeOffsetBytes, headerFieldValue);
  }

  private void setLongHeaderField(int headerRelativeOffsetBytes,
                                  long headerFieldValue) throws IOException {
    Objects.checkIndex(headerRelativeOffsetBytes, HeaderLayout.HEADER_SIZE - Long.BYTES + 1);
    INT64_OVER_BYTE_BUFFER.setVolatile(headerPageBuffer(), headerRelativeOffsetBytes, headerFieldValue);
  }

  //================== alignment: ========================================================================
  // Record headers must be 32b-aligned so they could be accessed with volatile semantics -- because not
  // all CPU arch support unaligned access with memory sync semantics
}
