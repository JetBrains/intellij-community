// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage;

import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.RecordLayout.ActualRecords;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.blobstorage.ByteBufferReader;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy;
import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.RecordLayout.OFFSET_BUCKET;
import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.Storage;

/**
 * This is not so much a supertype, but just a place for common logic (this why it is 'Helper').
 * All implementations share (almost) the same binary layout, but access on-disk data via different
 * page-caching mechanisms -- this Helper tries to extract as much common code as possible. Common
 * code is mostly related to a binary format.
 * ...actually, I plan that only one implementation remains finally -- i.e. different implementations
 * are just a way to see how they behave. This is why I dont' want to spent time on proper separation
 * of responsibilities -- i.e. extracting all storage-specific functionality under common interface,
 * and implement adapters for different storages.
 */
public abstract class StreamlinedBlobStorageHelper implements StreamlinedBlobStorage {

  /** First header int32, used to recognize this storage's file type */
  protected static final int MAGIC_WORD = IOUtil.asciiToMagicWord("SBlS");

  //=== FILE_STATUS header field values:
  protected static final int FILE_STATUS_OPENED = 0;
  protected static final int FILE_STATUS_PROPERLY_CLOSED = 1;

  /* ======== Persistent format: =================================================================== */

  // Persistent format: (header) (records)*
  //  header: storageVersion[int32], safeCloseMagic[int32] ...monitoring fields... dataFormatVersion[int32]
  //  record:
  //          recordHeader: recordType[int8], capacity, length?, redirectTo?, recordData[length]?
  //                        First byte of header contains the record type, which defines other header
  //                        fields & their length. A lot of bits wiggling are used to compress header
  //                        into as few bytes as possible -- see RecordLayout for details.
  //
  //  1. capacity is the allocated size of the record payload _excluding_ header, so
  //     nextRecordOffset = currentRecordOffset + recordHeader + recordCapacity
  //     (and recordHeader size depends on a record type, which is encoded in a first header byte)
  //
  //  2. actualLength (<=capacity) is the actual size of record payload written into the record, so
  //     recordData[0..actualLength) contains actual data, and recordData[actualLength..capacity)
  //     contains trash.
  //
  //  3. redirectTo is a 'forwarding pointer' for records that were moved (e.g. re-allocated).
  //
  //  4. records are always allocated on a single page: i.e. record never breaks a page boundary.
  //     If a record doesn't fit the current page, it is moved to another page (remaining space on
  //     page is filled by placeholder record, if needed).

  //TODO RC: implement space reclamation: re-use space of deleted records for the newly allocated ones.
  //         Need to keep a free-list.
  //MAYBE RC: store fixed-size free-list (tuples recordId, capacity) right after header on a first page, so on load we
  //         immediately have some records to choose from.
  //RC: there is a maintenance work (i.e deleted records reuse, compaction) not implemented yet for the storage. I think
  //    this maintenance is better to be implemented on the top of the storage, than encapsulated inside it.
  //    This is because:
  //    a) encapsulated maintenance makes storage too hard to test, so better to test maintenance in isolation, especially
  //       because maintenance is likely better to be done async
  //    b) maintenance highly depend on use of storage: i.e. ability to reclaim space of moved records depends
  //       on the fact that all references to the old location are already re-linked -- but storage can't guarantee
  //       that, there should be some external agent responsible for that.
  //    So my plans are:
  //    a) inside storage implement some _support_ for maintenance (e.g. ability to store some additional info in storage
  //       header, to be used for maintenance)
  //    b) implement something like BlobStorageHousekeeper, which runs in dedicated thread, with some precautions to not
  //       interrupt frontend work.

  protected static final class HeaderLayout {
    //@formatter:off

    /** Encodes storage (file) type */
    static final int MAGIC_WORD_OFFSET                           = 0;   //int32

    /** Version of this storage persistent format */
    static final int STORAGE_VERSION_OFFSET                      = 4;   //int32
    /** pageSize is a part of binary layout: records are page-aligned */
    static final int PAGE_SIZE_OFFSET                            = 8;   //int32
    static final int FILE_STATUS_OFFSET                          = 12;  //int32

    static final int NEXT_RECORD_ID_OFFSET                       = 16;  //int32

    static final int RECORDS_ALLOCATED_OFFSET                    = 20;  //int32
    static final int RECORDS_RELOCATED_OFFSET                    = 24;  //int32
    static final int RECORDS_DELETED_OFFSET                      = 28;  //int32

    static final int RECORDS_LIVE_TOTAL_PAYLOAD_SIZE_OFFSET      = 32;  //int64
    static final int RECORDS_LIVE_TOTAL_CAPACITY_SIZE_OFFSET     = 40;  //int64

    /** Version of data, stored in a blobs, managed by client code */
    static final int DATA_FORMAT_VERSION_OFFSET                  = 48;  //int32


    static final int FIRST_UNUSED_FIELD_OFFSET                   = 52;

    //Bytes [52..64] is reserved for the generations to come:
    static final int HEADER_SIZE                                 = 64;

    //@formatter:off

  }

  /**
   * Different record types support different capacities, even larger than this one. But most records
   * start as 'ACTUAL' record type, hence actual LargeRecord capacity is used as 'common denominator'
   * here.
   */
  public static final int MAX_CAPACITY = ActualRecords.LargeRecord.MAX_CAPACITY;

  /**
   * Max length of .redirectTo chain.
   * If a chain is longer, it is considered a bug (cyclic reference, or alike) and IOException is thrown.
   */
  protected static final int MAX_REDIRECTS = 256;

  protected static final long MAX_FILE_LENGTH = Integer.MAX_VALUE * (long)OFFSET_BUCKET;

  /** To avoid write file header to already closed storage */
  protected final AtomicBoolean closed = new AtomicBoolean(false);

  protected final AtomicBoolean wasClosedProperly = new AtomicBoolean(true);

  protected final @NotNull SpaceAllocationStrategy allocationStrategy;

  protected final int pageSize;
  protected final ByteOrder byteOrder;
  /**
   * Since records are page-aligned, record (with header) can't be larger than pageSize.
   * This is max record payload capacity (i.e. NOT including headers) for a current pageSize.
   * ({@link #MAX_CAPACITY} is a max capacity implementation supports -- regardless of page size)
   */
  protected final int maxCapacityForPageSize;

  private final ThreadLocal<ByteBuffer> threadLocalBuffer;


  //FIXME RC: always store nextRecordId in a header! this way all implementations headers will be the same
  /** Field could be read as volatile, but writes are protected with this intrinsic lock */
  //@GuardedBy(this)
  protected volatile int nextRecordId;


  //==== monitoring fields: =======================================================================================
  // They are frequently accessed, read/write them each time into a file header is too expensive (and verbose),
  // hence use caching fields instead:

  protected final AtomicInteger recordsAllocated = new AtomicInteger();
  protected final AtomicInteger recordsRelocated = new AtomicInteger();
  protected final AtomicInteger recordsDeleted = new AtomicInteger();
  protected final AtomicLong totalLiveRecordsPayloadBytes = new AtomicLong();
  protected final AtomicLong totalLiveRecordsCapacityBytes = new AtomicLong();


  protected StreamlinedBlobStorageHelper(@NotNull SpaceAllocationStrategy allocationStrategy,
                                         int pageSize,
                                         @NotNull ByteOrder byteOrder) {
    if (pageSize < headerSize()) {
      throw new IllegalStateException("header(" + headerSize() + " b) must fit on 0th page(" + pageSize + " b)");
    }

    this.byteOrder = byteOrder;
    this.pageSize = pageSize;
    this.allocationStrategy = allocationStrategy;

    final int defaultCapacity = allocationStrategy.defaultCapacity();
    threadLocalBuffer = ThreadLocal.withInitial(() -> {
      final ByteBuffer buffer = ByteBuffer.allocate(defaultCapacity);
      buffer.order(byteOrder);
      return buffer;
    });


    maxCapacityForPageSize = pageSize - ActualRecords.LargeRecord.INSTANCE.headerSize();
    if (maxCapacityForPageSize <= 0) {
      throw new IllegalArgumentException(
        "pageSize(=" + pageSize + ") is too small even for a record header(=" + ActualRecords.LargeRecord.INSTANCE.headerSize() + "b)"
      );
    }
  }

  @Override
  public boolean wasClosedProperly() {
    return wasClosedProperly.get();
  }

  @Override
  public boolean hasRecord(int recordId) throws IOException {
    return hasRecord(recordId, null);
  }

  @Override
  public <Out> Out readRecord(final int recordId,
                              final @NotNull ByteBufferReader<Out> reader) throws IOException {
    return readRecord(recordId, reader, null);
  }

  @Override
  public int writeToRecord(final int recordId,
                           final @NotNull ByteBufferWriter writer,
                           final int expectedRecordSizeHint) throws IOException {
    return writeToRecord(recordId, writer, expectedRecordSizeHint, /* leaveRedirectOnRecordRelocation: */ false);
  }

  @Override
  public int writeToRecord(final int recordId,
                           final @NotNull ByteBufferWriter writer) throws IOException {
    return writeToRecord(recordId, writer, /*expectedRecordSizeHint: */ -1);
  }


  @Override
  public boolean isRecordActual(int recordActualLength) {
    return recordActualLength >= 0;
  }

  @Override
  public int maxPayloadSupported() {
    return Math.min(maxCapacityForPageSize, MAX_CAPACITY);
  }

  //monitoring:

  @Override
  public int liveRecordsCount() {
    return recordsAllocated.get() - recordsDeleted.get() - recordsRelocated.get();
  }

  public int recordsAllocated() {
    return recordsAllocated.get();
  }

  public int recordsRelocated() {
    return recordsRelocated.get();
  }

  public int recordsDeleted() {
    return recordsDeleted.get();
  }

  public long totalLiveRecordsPayloadBytes() {
    return totalLiveRecordsPayloadBytes.get();
  }

  public long totalLiveRecordsCapacityBytes() {
    return totalLiveRecordsCapacityBytes.get();
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + storagePath() + "]{nextRecordId: " + nextRecordId + '}';
  }

  //==================== implementation: ==========================================================================

  protected abstract @NotNull Path storagePath();

  protected void checkNotClosed() throws ClosedStorageException {
    if (closed.get()) {
      throw new ClosedStorageException("Storage " + this + " is already closed");
    }
  }

  /** Storage header size */
  protected int headerSize() {
    //RC: Use method instead of constant because I'm thinking about variable-size header, maybe...
    return HeaderLayout.HEADER_SIZE;
  }

  protected long recordsStartOffset() {
    long headerSize = headerSize();
    if (headerSize % OFFSET_BUCKET > 0) {
      return (headerSize / OFFSET_BUCKET + 1) * OFFSET_BUCKET;
    }
    else {
      return (headerSize / OFFSET_BUCKET) * OFFSET_BUCKET;
    }
  }

  protected long idToOffset(int recordId) {
    // recordId=0 is used as NULL_ID (i.e. invalid) id, hence '-1'
    return recordsStartOffset() + (recordId - 1) * (long)OFFSET_BUCKET;
  }

  protected int offsetToId(long offset) {
    // recordId=0 is used as NULL_ID (i.e. invalid) id, hence '+1' for the 1st record to have {id:1}
    long longId = (offset - recordsStartOffset()) / OFFSET_BUCKET + 1;
    int id = (int)longId;

    assert longId == id : "offset " + offset + " is out of Integer bounds";
    assert id > 0 : "id " + id + " is not a valid id";

    return id;
  }

  protected int toOffsetOnPage(long offsetInFile) {
    return Math.toIntExact(offsetInFile % pageSize);
  }


  protected void updateNextRecordId(int nextRecordId) {
    if( nextRecordId <= NULL_ID ){
      throw new IllegalArgumentException("nextRecordId(="+nextRecordId+") must be >0");
    }
    synchronized (this) {
      this.nextRecordId = nextRecordId;
    }
  }

  protected int allocateSlotForRecord(int pageSize,
                                      int totalRecordSize,
                                      @NotNull IntRef actualRecordSize) throws IOException {
    if (totalRecordSize > pageSize) {
      throw new IllegalArgumentException("recordSize(" + totalRecordSize + " b) must be <= pageSize(" + pageSize + " b)");
    }
    //MAYBE RC: all this could be implemented as CAS-loop, without lock
    synchronized (this) {// protect nextRecordId modifications:
      while (true) {     // [totalRecordSize <= pageSize] =implies=> [loop must finish in <=2 iterations]
        int newRecordId = nextRecordId;
        long recordStartOffset = idToOffset(newRecordId);
        int offsetOnPage = toOffsetOnPage(recordStartOffset);
        int recordSizeRoundedUp = roundSizeUpToBucket(offsetOnPage, pageSize, totalRecordSize);
        long recordEndOffset = recordStartOffset + recordSizeRoundedUp - 1;
        long startPage = recordStartOffset / pageSize;
        //we don't want record to be broken by page boundary, so if the current record steps out of the current
        // page -> we move the entire record to the next page, and pad the space remaining on the current page
        // with filler (padding) record:
        long endPage = recordEndOffset / pageSize;
        if (startPage == endPage) {
          actualRecordSize.set(recordSizeRoundedUp);
          nextRecordId = offsetToId(recordEndOffset + 1);
          return newRecordId;
        }

        //insert a space-filler record to occupy space till the end of page:
        //MAYBE RC: even better would be to add that space to the previous record (i.e. last record
        // remains on the current page). We do this in .roundSizeUpToBucket() with small bytes at
        // the end of page, but unfortunately here we don't know there 'previous record' header
        // is located => can't adjust its capacity. This problem could be solved, but I don't
        // think it is important enough for now.
        putSpaceFillerRecord(recordStartOffset, pageSize);

        //...move pointer to the next page, and re-try allocate record:
        long nextPageStartOffset = (startPage + 1) * pageSize;
        nextRecordId = offsetToId(nextPageStartOffset);
        assert idToOffset(nextRecordId) == nextPageStartOffset : "idToOffset(" + nextRecordId + ")=" + idToOffset(nextRecordId) +
                                                                 " != nextPageStartOffset(" + nextPageStartOffset + ")";
      }
    }
  }

  protected abstract void putSpaceFillerRecord(long recordOffset,
                                               int pageSize) throws IOException;


  protected void checkRecordIdExists(final int recordId) {
    checkRecordIdValid(recordId);
    if (!isRecordIdAllocated(recordId)) {
      throw new IllegalArgumentException("recordId(" + recordId + ") is not yet allocated: allocated ids are all < " + nextRecordId);
    }
  }

  /**
   * Method returns true if record with id=recordId is already allocated.
   * It doesn't mean the record is fully written, though -- we could be in the middle of record write.
   */
  protected boolean isRecordIdAllocated(int recordId) {
    return recordId < nextRecordId;
  }

  protected long nextRecordOffset(long recordOffset,
                                  @NotNull RecordLayout recordLayout,
                                  int recordCapacity) {
    final int headerSize = recordLayout.headerSize();
    final long nextOffset = recordOffset + headerSize + recordCapacity;

    final int offsetOnPage = toOffsetOnPage(nextOffset);
    if (pageSize - offsetOnPage < headerSize) {
      //Previously, I _fix_ the mismatch here -- by moving offset to the next page:
      //  nextOffset = (nextOffset / pageSize + 1) * pageSize;
      //Now instead of fix it here I adjust new record allocation code (allocateRecord), so for records
      // on the end of page -- record capacity is increased slightly, to consume that small unusable
      // bytes on the edge of the page -- this way those bytes are utilized.
      // But that means this branch should be unreachable now:
      throw new AssertionError("Bug: offsetOnPage(" + offsetOnPage + ") is too close to page border (" + pageSize + ")");
    }
    return nextOffset;
  }


  protected static int roundSizeUpToBucket(int offset,
                                           int pageSize,
                                           int rawRecordSize) {
    int recordSizeRoundedUp = rawRecordSize;
    if (recordSizeRoundedUp % OFFSET_BUCKET != 0) {
      recordSizeRoundedUp = ((recordSizeRoundedUp / OFFSET_BUCKET + 1) * OFFSET_BUCKET);
    }
    final int occupiedOnPage = offset + recordSizeRoundedUp;
    final int remainedOnPage = pageSize - occupiedOnPage;
    if (0 < remainedOnPage && remainedOnPage < OFFSET_BUCKET) {
      //we can't squeeze even the smallest record into remaining space, so just merge it into current record
      recordSizeRoundedUp += remainedOnPage;
    }
    assert recordSizeRoundedUp >= rawRecordSize
      : "roundedUpRecordSize(=" + recordSizeRoundedUp + ") must be >= rawRecordSize(=" + rawRecordSize + ")";
    return recordSizeRoundedUp;
  }

  protected static void checkRecordIdValid(int recordId) {
    if (!isValidRecordId(recordId)) {
      throw new IllegalArgumentException("recordId(" + recordId + ") is invalid: must be > 0");
    }
  }

  protected static boolean isValidRecordId(int recordId) {
    return recordId > NULL_ID;
  }

  protected static void checkCapacityHardLimit(int capacity) {
    if (!isCorrectCapacity(capacity)) {
      throw new IllegalArgumentException("capacity(=" + capacity + ") must be in [0, " + MAX_CAPACITY + "]");
    }
  }

  protected static void checkLengthHardLimit(int length) {
    if (!isCorrectLength(length)) {
      throw new IllegalArgumentException("length(=" + length + ") must be in [0, " + MAX_CAPACITY + "]");
    }
  }

  protected static boolean isCorrectCapacity(int capacity) {
    return 0 <= capacity && capacity <= MAX_CAPACITY;
  }

  protected static boolean isCorrectLength(int length) {
    return 0 <= length && length <= MAX_CAPACITY;
  }


  protected @NotNull ByteBuffer acquireTemporaryBuffer(int expectedRecordSizeHint) {
    ByteBuffer temp = threadLocalBuffer.get();
    if (temp != null && temp.capacity() >= expectedRecordSizeHint) {
      threadLocalBuffer.set(null);
      return temp.position(0)
        .limit(0);
    }
    else {
      int defaultCapacity = allocationStrategy.defaultCapacity();
      int capacity = Math.max(defaultCapacity, expectedRecordSizeHint);
      ByteBuffer buffer = ByteBuffer.allocate(capacity);
      buffer.order(byteOrder);
      return buffer;
    }
  }

  protected void releaseTemporaryBuffer(@NotNull ByteBuffer temp) {
    int defaultCapacity = allocationStrategy.defaultCapacity();
    //avoid keeping too big buffers from GC:
    if (temp.capacity() <= 2 * defaultCapacity) {
      threadLocalBuffer.set(temp);
    }
  }


  public static @NotNull BatchCallback setupReportingToOpenTelemetry(@NotNull Path fileName,
                                                                     @NotNull StreamlinedBlobStorageHelper storage) {
    Meter meter = TelemetryManager.getInstance().getMeter(Storage);

    var recordsAllocated = meter.counterBuilder("StreamlinedBlobStorage.recordsAllocated").buildObserver();
    var recordsRelocated = meter.counterBuilder("StreamlinedBlobStorage.recordsRelocated").buildObserver();
    var recordsDeleted = meter.counterBuilder("StreamlinedBlobStorage.recordsDeleted").buildObserver();
    var totalLiveRecordsPayloadBytes = meter.upDownCounterBuilder("StreamlinedBlobStorage.totalLiveRecordsPayloadBytes").buildObserver();
    var totalLiveRecordsCapacityBytes = meter.upDownCounterBuilder("StreamlinedBlobStorage.totalLiveRecordsCapacityBytes").buildObserver();
    Attributes attributes = Attributes.builder()
      .put("file", fileName.toString())
      .build();
    return meter.batchCallback(
      () -> {
        recordsAllocated.record(storage.recordsAllocated(), attributes);
        recordsRelocated.record(storage.recordsRelocated(), attributes);
        recordsDeleted.record(storage.recordsDeleted(), attributes);
        totalLiveRecordsPayloadBytes.record(storage.totalLiveRecordsPayloadBytes(), attributes);
        totalLiveRecordsCapacityBytes.record(storage.totalLiveRecordsCapacityBytes(), attributes);
      },
      recordsAllocated, recordsRelocated, recordsDeleted,
      totalLiveRecordsPayloadBytes, totalLiveRecordsCapacityBytes
    );
  }
}
