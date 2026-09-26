// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.blobstorage;

import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.platform.diagnostic.telemetry.PlatformScopesKt;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.util.io.storages.blobstorage.RecordLayout.ActualRecords;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage.Page;
import com.intellij.util.BitUtil;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.blobstorage.BlobStorageStatistics;
import com.intellij.util.io.blobstorage.ByteBufferReader;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy;
import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.platform.util.io.storages.blobstorage.RecordLayout.ActualRecords.recordLayoutForType;
import static com.intellij.platform.util.io.storages.blobstorage.RecordLayout.ActualRecords.recordSizeTypeByCapacity;
import static com.intellij.platform.util.io.storages.blobstorage.RecordLayout.OFFSET_BUCKET;
import static com.intellij.util.io.IOUtil.magicWordToASCII;
import static java.lang.invoke.MethodHandles.byteBufferViewVarHandle;
import static java.nio.ByteOrder.nativeOrder;


/**
 * Implements {@link StreamlinedBlobStorage} blobs over {@link MMappedFileStorage} storage.
 * Storage is optimized to store small records (~tens bytes) -- it tries to compress record headers
 * so smaller records have just 2 bytes of overhead because of the header.
 * At the same time, storage allows record size up to ~1Mb large (={@linkplain #MAX_CAPACITY})
 * <p>
 * Storage is NOT thread-safe: it is up to calling code to protect the accesses
 */
public final class StreamlinedBlobStorageOverMMappedFile implements StreamlinedBlobStorage, BlobStorageStatistics {
  public static final int STORAGE_VERSION_CURRENT = 1;

  /** First header int32, used to recognize this storage's file type */
  private static final int MAGIC_WORD = IOUtil.asciiToMagicWord("SBlS");

  /* ======== Persistent format: =================================================================== */

  // Persistent format: (header) (records)*
  //  header: see HeaderLayout for details
  //  record:
  //          recordHeader: recordType[int8], capacity, length?, redirectTo?, recordData[length]?
  //                        First byte of header contains the record type, which defines other header
  //                        fields & their length. A lot of bits wiggling are used to compress header
  //                        into as few bytes as possible -- see RecordLayout for details.
  //  Glossary:
  //  1. capacity: is the _allocated_ size of the record _excluding_ header, so
  //     nextRecordOffset = currentRecordOffset + recordHeader + recordCapacity
  //     (and recordHeader size depends on a record type, which is encoded in a first header byte)
  //
  //  2. actualLength (<=capacity) is the actual size of record payload written into the record, so
  //     recordData[0..actualLength) contains actual data, and recordData[actualLength..capacity)
  //     contains garbage.
  //
  //  3. redirectTo: a 'forwarding pointer' for records that were moved (e.g. re-allocated).
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

  /** _Storage_ header layout. For record header layouts see {@linkplain RecordLayout} */
  @VisibleForTesting
  public static final class HeaderLayout {
    //@formatter:off

    /** Encodes storage (file) type */
    public static final int MAGIC_WORD_OFFSET                           = 0;   //int32

    /** Version of this storage's persistent format */
    public static final int STORAGE_VERSION_OFFSET                      = 4;   //int32
    /** pageSize is a part of a binary layout: records are page-aligned */
    public static final int PAGE_SIZE_OFFSET                            = 8;   //int32
    /** File status: bitmask from FILE_STATUS_XXX bits */
    public static final int FILE_STATUS_OFFSET                          = 12;  //int32

    public static final int NEXT_RECORD_ID_OFFSET                       = 16;  //int32

    public static final int RECORDS_ALLOCATED_OFFSET                    = 20;  //int32
    public static final int RECORDS_RELOCATED_OFFSET                    = 24;  //int32
    public static final int RECORDS_DELETED_OFFSET                      = 28;  //int32

    public static final int RECORDS_LIVE_TOTAL_PAYLOAD_SIZE_OFFSET      = 32;  //int64
    public static final int RECORDS_LIVE_TOTAL_CAPACITY_SIZE_OFFSET     = 40;  //int64

    /** Version of data, stored in blobs, managed by client code */
    public static final int DATA_FORMAT_VERSION_OFFSET                  = 48;  //int32

    @SuppressWarnings("unused")
    public static final int FIRST_UNUSED_FIELD_OFFSET                   = 52;

    //Bytes [52..64] is reserved for the generations to come:
    public static final int HEADER_SIZE                                 = 64;

    //FILE_STATUS bitmasks:
    public static final int FILE_STATUS_CLOSED_PROPERLY_MASK        = 0b01;
    public static final int FILE_STATUS_ALWAYS_CLOSED_PROPERLY_MASK = 0b10;//sticky: once it is set, it is never reset to 0

    //@formatter:on
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
  private static final int MAX_REDIRECTS = 256;

  private static final long MAX_FILE_LENGTH = Integer.MAX_VALUE * (long)OFFSET_BUCKET;

  private static final VarHandle INT_HANDLE = byteBufferViewVarHandle(int[].class, nativeOrder())
    .withInvokeExactBehavior();


  /* ============== instance fields: ====================================================================== */

  private final @NotNull MMappedFileStorage storage;

  private final @NotNull ByteOrder byteOrder;
  private final @NotNull SpaceAllocationStrategy allocationStrategy;

  /** Cached storage.pageByOffset(0) value for faster access */
  private transient volatile Page headerPage;

  private final int pageSize;

  /**
   * Since records are page-aligned, record (with header) can't be larger than pageSize.
   * This is max record payload capacity (i.e. NOT including headers) for a current pageSize.
   * ({@link #MAX_CAPACITY} is a max capacity implementation supports -- regardless of page size)
   */
  private final int maxCapacityForPageSize;

  /** Thread-local temporary buffer */
  private final ThreadLocal<ByteBuffer> threadLocalBuffer;

  /** To avoid writing a file header to already closed storage */
  private final AtomicBoolean closed = new AtomicBoolean(false);

  /** This property should be assigned in ctor, if the ctor detects that the storage was improperly closed */
  private final boolean wasClosedProperly;
  /** This property should be assigned in ctor, if the storage was improperly closed somewhere in the past */
  private final boolean wasAlwaysClosedProperly;


  //==== monitoring fields: =======================================================================================

  private final AtomicInteger recordsAllocated = new AtomicInteger();
  private final AtomicInteger recordsRelocated = new AtomicInteger();
  private final AtomicInteger recordsDeleted = new AtomicInteger();
  private final AtomicLong totalLiveRecordsPayloadBytes = new AtomicLong();
  private final AtomicLong totalLiveRecordsCapacityBytes = new AtomicLong();

  private final BatchCallback openTelemetryCallback;
  //==== monitoring fields: =======================================================================================


  public StreamlinedBlobStorageOverMMappedFile(@NotNull MMappedFileStorage storage,
                                               @NotNull SpaceAllocationStrategy allocationStrategy) throws IOException {
    int pageSize = storage.pageSize();
    if (pageSize < headerSize()) {
      throw new IllegalStateException("header(" + headerSize() + " b) must fit on 0th page(" + pageSize + " b)");
    }

    this.storage = storage;

    ByteOrder byteOrder = storage.byteOrder();
    this.byteOrder = byteOrder;
    this.pageSize = pageSize;
    this.allocationStrategy = allocationStrategy;

    int defaultCapacity = allocationStrategy.defaultCapacity();
    threadLocalBuffer = ThreadLocal.withInitial(() -> {
      ByteBuffer buffer = ByteBuffer.allocate(defaultCapacity);
      buffer.order(byteOrder);
      return buffer;
    });

    maxCapacityForPageSize = pageSize - ActualRecords.LargeRecord.INSTANCE.headerSize();
    if (maxCapacityForPageSize <= 0) {
      throw new IllegalArgumentException(
        "pageSize(=" + pageSize + ") is too small even for a record header(=" + ActualRecords.LargeRecord.INSTANCE.headerSize() + "b)"
      );
    }

    //Important to ask file size _before_ requesting headerPage -- because the file will be expanded on that request
    long length = storage.actualFileSize();
    if (length > MAX_FILE_LENGTH) {
      throw new IOException(
        "Can't read file[" + storage + "]: too big, " + length + " > Integer.MAX_VALUE * " + OFFSET_BUCKET);
    }

    headerPage = storage.pageByOffset(0L);

    if (length == 0) {//new empty file
      putHeaderInt(HeaderLayout.MAGIC_WORD_OFFSET, MAGIC_WORD);
      putHeaderInt(HeaderLayout.STORAGE_VERSION_OFFSET, STORAGE_VERSION_CURRENT);
      putHeaderInt(HeaderLayout.PAGE_SIZE_OFFSET, pageSize);

      updateNextRecordId(offsetToId(recordsStartOffset()));

      wasClosedProperly = true;
      wasAlwaysClosedProperly = true;
    }
    else {
      int magicWord = readHeaderInt(HeaderLayout.MAGIC_WORD_OFFSET);
      if (magicWord != MAGIC_WORD) {
        throw new IOException("[" + storage.storagePath() + "] is of incorrect type: " +
                              ".magicWord(=" + magicWord + ", '" + magicWordToASCII(magicWord) + "') != " + MAGIC_WORD + " expected");
      }

      int version = readHeaderInt(HeaderLayout.STORAGE_VERSION_OFFSET);
      if (version != STORAGE_VERSION_CURRENT) {
        throw new IOException(
          "[" + storage.storagePath() + "]: file version(" + version + ") != current impl version (" + STORAGE_VERSION_CURRENT + ")");
      }

      int filePageSize = readHeaderInt(HeaderLayout.PAGE_SIZE_OFFSET);
      if (pageSize != filePageSize) {
        throw new IOException("[" + storage.storagePath() + "]: file created with pageSize=" + filePageSize +
                              " but current storage.pageSize=" + pageSize);
      }

      int fileStatusBitmask = readHeaderInt(HeaderLayout.FILE_STATUS_OFFSET);
      boolean wasClosedProperlyLastTime = BitUtil.isSet(fileStatusBitmask, HeaderLayout.FILE_STATUS_CLOSED_PROPERLY_MASK);
      boolean wasAlwaysClosedProperly = BitUtil.isSet(fileStatusBitmask, HeaderLayout.FILE_STATUS_ALWAYS_CLOSED_PROPERLY_MASK);

      this.wasClosedProperly = wasClosedProperlyLastTime;
      this.wasAlwaysClosedProperly = wasAlwaysClosedProperly && wasClosedProperlyLastTime;
    }

    //Store ALWAYS_CLOSED_PROPERLY bit, but CLOSED_PROPERLY effectively set=false:
    // CLOSED_PROPERLY will be set back to true in .close(), but will remain false if _not_ closed properly:
    int fileStatusBitmask = wasAlwaysClosedProperly ? HeaderLayout.FILE_STATUS_ALWAYS_CLOSED_PROPERLY_MASK : 0;
    putHeaderInt(HeaderLayout.FILE_STATUS_OFFSET, fileStatusBitmask);
    storage.fsync();//ensure status is persisted



    recordsAllocated.set(readHeaderInt(HeaderLayout.RECORDS_ALLOCATED_OFFSET));
    recordsRelocated.set(readHeaderInt(HeaderLayout.RECORDS_RELOCATED_OFFSET));
    recordsDeleted.set(readHeaderInt(HeaderLayout.RECORDS_DELETED_OFFSET));
    totalLiveRecordsPayloadBytes.set(readHeaderLong(HeaderLayout.RECORDS_LIVE_TOTAL_PAYLOAD_SIZE_OFFSET));
    totalLiveRecordsCapacityBytes.set(readHeaderLong(HeaderLayout.RECORDS_LIVE_TOTAL_CAPACITY_SIZE_OFFSET));

    //setup monitoring:
    openTelemetryCallback = setupReportingToOpenTelemetry(storage.storagePath().getFileName(), this);
  }

  @Override
  public boolean wasClosedProperly() {
    return wasClosedProperly;
  }

  @Override
  public boolean wasAlwaysClosedProperly() {
    return wasAlwaysClosedProperly;
  }

  @Override
  public boolean hasRecord(int recordId) throws IOException {
    return hasRecord(recordId, null);
  }

  @Override
  public boolean isRecordActual(int recordActualLength) {
    return recordActualLength >= 0;
  }

  @Override
  public int maxPayloadSupported() {
    return Math.min(maxCapacityForPageSize, MAX_CAPACITY);
  }

  @Override
  public boolean isClosed() {
    return closed.get();
  }

  @Override
  public int liveRecordsCount() throws ClosedStorageException {
    checkNotClosed();
    return recordsAllocated.get() - recordsDeleted.get() - recordsRelocated.get();
  }

  @Override
  public int recordsAllocated() throws ClosedStorageException {
    checkNotClosed();
    return recordsAllocated.get();
  }

  @Override
  public int recordsRelocated() throws ClosedStorageException {
    checkNotClosed();
    return recordsRelocated.get();
  }

  @Override
  public int recordsDeleted() throws ClosedStorageException {
    checkNotClosed();
    return recordsDeleted.get();
  }

  @Override
  public long totalLiveRecordsPayloadBytes() throws ClosedStorageException {
    checkNotClosed();
    return totalLiveRecordsPayloadBytes.get();
  }

  @Override
  public long totalLiveRecordsCapacityBytes() throws ClosedStorageException {
    checkNotClosed();
    return totalLiveRecordsCapacityBytes.get();
  }

  @Override
  public String toString() {
    try {
      return getClass().getSimpleName() + "[" + storagePath() + "]{nextRecordId: " + nextRecordId() + '}';
    }
    catch (IOException e) {
      return getClass().getSimpleName() + "[" + storagePath() + "]{closed}";
    }
  }

  private void checkNotClosed() throws ClosedStorageException {
    if (closed.get()) {
      throw new ClosedStorageException("Storage " + this + " is already closed");
    }
  }

  /** Storage header size */
  private static int headerSize() {
    //RC: Use method instead of constant because I'm thinking about variable-size header, maybe...
    return HeaderLayout.HEADER_SIZE;
  }

  private static long recordsStartOffset() {
    long headerSize = headerSize();
    if (headerSize % OFFSET_BUCKET > 0) {
      return (headerSize / OFFSET_BUCKET + 1) * OFFSET_BUCKET;
    }
    else {
      return (headerSize / OFFSET_BUCKET) * OFFSET_BUCKET;
    }
  }

  private static long idToOffset(int recordId) {
    // recordId=0 is used as NULL_ID (i.e. invalid) id, hence '-1'
    return recordsStartOffset() + (recordId - 1) * (long)OFFSET_BUCKET;
  }

  private static int offsetToId(long offset) {
    // recordId=0 is used as NULL_ID (i.e. invalid) id, hence '+1' for the 1st record to have {id:1}
    long longId = (offset - recordsStartOffset()) / OFFSET_BUCKET + 1;
    int id = (int)longId;

    assert longId == id : "offset " + offset + " is out of Integer bounds";
    assert id > 0 : "id " + id + " is not a valid id";

    return id;
  }

  private int allocateSlotForRecord(int pageSize,
                                    int totalRecordSize,
                                    @NotNull IntRef actualRecordSize) throws IOException {
    if (totalRecordSize > pageSize) {
      throw new IllegalArgumentException("recordSize(" + totalRecordSize + " b) must be <= pageSize(" + pageSize + " b)");
    }
    //MAYBE RC: all this could be implemented as CAS-loop, without lock
    synchronized (this) {// protect nextRecordId modifications:
      while (true) {     // [totalRecordSize <= pageSize] =implies=> [loop must finish in <=2 iterations]
        int newRecordId = nextRecordId();
        long recordStartOffset = idToOffset(newRecordId);
        int offsetOnPage = storage.toOffsetInPage(recordStartOffset);
        int recordSizeRoundedUp = roundSizeUpToBucket(offsetOnPage, pageSize, totalRecordSize);
        long recordEndOffset = recordStartOffset + recordSizeRoundedUp - 1;
        long startPage = recordStartOffset / pageSize;
        //we don't want record to be broken by page boundary, so if the current record steps out of the current
        // page -> we move the entire record to the next page, and pad the space remaining on the current page
        // with filler (padding) record:
        long endPage = recordEndOffset / pageSize;
        if (startPage == endPage) {
          actualRecordSize.set(recordSizeRoundedUp);
          updateNextRecordId(offsetToId(recordEndOffset + 1));
          return newRecordId;
        }

        //insert a space-filler record to occupy space till the end of page:
        //MAYBE RC: even better would be to add that space to the previous record (i.e. last record
        // remains on the current page). We do this in .roundSizeUpToBucket() with small bytes at
        // the end of page, but unfortunately here we don't know there 'previous record' header
        // is located => can't adjust its capacity. This problem could be solved, but I don't
        // think it is important enough for now.
        putSpaceFillerRecord(recordStartOffset, pageSize);

        //...move pointer to the next page, and re-try allocating record:
        long nextPageStartOffset = (startPage + 1) * pageSize;
        updateNextRecordId(offsetToId(nextPageStartOffset));
        assert idToOffset(nextRecordId()) == nextPageStartOffset : "idToOffset(" + nextRecordId() + ")=" + idToOffset(nextRecordId()) +
                                                                   " != nextPageStartOffset(" + nextPageStartOffset + ")";
      }
    }
  }

  private void checkRecordIdExists(int recordId) throws IOException {
    if (!isExistingRecordId(recordId)) {
      throw new IllegalArgumentException(
        "recordId(" + recordId + ") is not valid: allocated ids are in (0, " + nextRecordId() + "), " +
        "(wasClosedProperly: " + wasClosedProperly() + ", wasAlwaysClosedProperly: " + wasAlwaysClosedProperly() + ")"
      );
    }
  }

  private void checkRedirectToId(int startingRecordId,
                                 int currentRecordId,
                                 int redirectToId) throws IOException {
    if (redirectToId == NULL_ID) { //!actual && redirectTo = NULL
      throw new RecordAlreadyDeletedException(
        "Can't access record[" + startingRecordId + "/" + currentRecordId + "]: it was deleted " +
        "(wasClosedProperly: " + wasClosedProperly() + ", wasAlwaysClosedProperly: " + wasAlwaysClosedProperly() + ")"
      );
    }
    if (!isExistingRecordId(redirectToId)) {
      throw new CorruptedException(
        "record(" + startingRecordId + "/" + currentRecordId + ").redirectToId(=" + redirectToId + ") is not exist: " +
        "allocated ids are in (0, " + nextRecordId() + "), " +
        "(wasClosedProperly: " + wasClosedProperly() + ", wasAlwaysClosedProperly: " + wasAlwaysClosedProperly() + ")"
      );
    }
  }

  /**
   * @return true if record with recordId is already allocated.
   * It doesn't mean the recordId is valid, though -- it could point to the middle of some record.
   */
  private boolean isRecordIdAllocated(int recordId) throws IOException {
    return recordId < nextRecordId();
  }

  /**
   * @return true if record with recordId is in the range of existing record ids.
   * It doesn't mean the recordId is valid, though -- it could point to the middle of some record.
   */
  private boolean isExistingRecordId(int recordId) throws IOException {
    return isValidRecordId(recordId) && isRecordIdAllocated(recordId);
  }

  private long nextRecordOffset(long recordOffset,
                                @NotNull RecordLayout recordLayout,
                                int recordCapacity) {
    int headerSize = recordLayout.headerSize();
    long nextOffset = recordOffset + headerSize + recordCapacity;

    int offsetOnPage = storage.toOffsetInPage(nextOffset);
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

  private static int roundSizeUpToBucket(int offset,
                                         int pageSize,
                                         int rawRecordSize) {
    int recordSizeRoundedUp = rawRecordSize;
    if (recordSizeRoundedUp % OFFSET_BUCKET != 0) {
      recordSizeRoundedUp = ((recordSizeRoundedUp / OFFSET_BUCKET + 1) * OFFSET_BUCKET);
    }
    int occupiedOnPage = offset + recordSizeRoundedUp;
    int remainedOnPage = pageSize - occupiedOnPage;
    if (0 < remainedOnPage && remainedOnPage < OFFSET_BUCKET) {
      //we can't squeeze even the smallest record into remaining space, so just merge it into current record
      recordSizeRoundedUp += remainedOnPage;
    }
    assert recordSizeRoundedUp >= rawRecordSize
      : "roundedUpRecordSize(=" + recordSizeRoundedUp + ") must be >= rawRecordSize(=" + rawRecordSize + ")";
    return recordSizeRoundedUp;
  }

  private static void checkRecordIdValid(int recordId) throws FileTooBigException {
    if (!isValidRecordId(recordId)) {
      throw new FileTooBigException("recordId(" + recordId + ") is invalid: must be > 0");
    }
  }

  private static boolean isValidRecordId(int recordId) {
    return recordId > NULL_ID;
  }

  private static void checkCapacityHardLimit(int capacity) throws FileTooBigException {
    if (!isCorrectCapacity(capacity)) {
      throw new FileTooBigException("capacity(=" + capacity + ") must be in [0, " + MAX_CAPACITY + "]");
    }
  }

  private static void checkLengthHardLimit(int length) throws FileTooBigException {
    if (!isCorrectLength(length)) {
      throw new FileTooBigException("length(=" + length + ") must be in [0, " + MAX_CAPACITY + "]");
    }
  }

  private static boolean isCorrectCapacity(int capacity) {
    return 0 <= capacity && capacity <= MAX_CAPACITY;
  }

  private static boolean isCorrectLength(int length) {
    return 0 <= length && length <= MAX_CAPACITY;
  }

  /**
   * @return buffer with [capacity >= minCapacity, position=0, limit=0, byteOrder=this.byteOrder]
   *         Buffer is NOT guaranteed to be zeroed.
   */
  private @NotNull ByteBuffer acquireTemporaryBuffer(int minCapacity) {
    ByteBuffer temp = threadLocalBuffer.get();
    if (temp != null && temp.capacity() >= minCapacity) {
      threadLocalBuffer.remove();
      return temp.position(0)
        .order(byteOrder)//to be sure: maybe someone has changed it during some uses before?
        .limit(0);
    }
    else {
      int defaultCapacity = allocationStrategy.defaultCapacity();
      int capacity = Math.max(defaultCapacity, minCapacity);
      return ByteBuffer.allocate(capacity)
        .order(byteOrder)
        .limit(0);
    }
  }

  private void releaseTemporaryBuffer(@NotNull ByteBuffer temp) {
    int defaultCapacity = allocationStrategy.defaultCapacity();
    //avoid keeping too big buffers from GC:
    if (temp.capacity() <= 2 * defaultCapacity) {
      threadLocalBuffer.set(temp);
    }
  }

  @Override
  public int getStorageVersion() throws IOException {
    return readHeaderInt(HeaderLayout.STORAGE_VERSION_OFFSET);
  }

  @Override
  public int getDataFormatVersion() throws IOException {
    return readHeaderInt(HeaderLayout.DATA_FORMAT_VERSION_OFFSET);
  }

  @Override
  public void setDataFormatVersion(int expectedVersion) throws IOException {
    putHeaderInt(HeaderLayout.DATA_FORMAT_VERSION_OFFSET, expectedVersion);
  }


  @Override
  public boolean hasRecord(int recordId,
                           @Nullable IntRef redirectToIdRef) throws IOException {
    if (recordId == NULL_ID) {
      return false;
    }
    checkRecordIdValid(recordId);
    if (!isRecordIdAllocated(recordId)) {
      return false;
    }
    int currentRecordId = recordId;
    for (int i = 0; i < MAX_REDIRECTS; i++) {
      long recordOffset = idToOffset(currentRecordId);
      Page page = storage.pageByOffset(recordOffset);
      int offsetOnPage = storage.toOffsetInPage(recordOffset);
      ByteBuffer buffer = page.rawPageBuffer();
      RecordLayout recordLayout = RecordLayout.recordLayout(buffer, offsetOnPage);
      byte recordType = recordLayout.recordType();

      if (redirectToIdRef != null) {
        redirectToIdRef.set(currentRecordId);
      }

      if (recordType == RecordLayout.RECORD_TYPE_ACTUAL) {
        return true;
      }

      if (recordType == RecordLayout.RECORD_TYPE_MOVED) {
        int redirectToId = recordLayout.redirectToId(buffer, offsetOnPage);
        if (redirectToId == NULL_ID) {
          return false;
        }
        checkRedirectToId(recordId, currentRecordId, redirectToId);
        currentRecordId = redirectToId;
      }
      else {
        throw new AssertionError("RecordType(" + recordType + ") should not appear in the chain: " +
                                 "it is either not implemented yet, or all wrong");
      }
    }
    throw new IOException("record[" + recordId + "].redirectTo chain is too long (>=" + MAX_REDIRECTS + "): circular reference?");
  }

  //MAYBE RC: consider change way of dealing with ByteBuffers: what-if all methods will have same semantics,
  //          i.e. buffer contains payload[0..limit]? I.e. all methods are passing buffers in such a state,
  //          and all methods are returning buffers in such a state?

  /**
   * reader will be called with read-only ByteBuffer set up for reading the record content (payload):
   * i.e. position=0, limit=payload.length. Reader is free to do whatever it likes with the buffer.
   *
   * @param redirectToIdRef if not-null, will contain actual recordId of the record,
   *                        which could be different from recordId passed in if the record was moved (e.g.
   *                        re-allocated in a new place) and recordId used to call the method is now
   *                        outdated. Clients could still use old recordId, but better to replace
   *                        this outdated id with actual one, since it improves performance (at least)
   */
  @Override
  public <Out> Out readRecord(int recordId,
                              @NotNull ByteBufferReader<Out> reader,
                              @Nullable IntRef redirectToIdRef) throws IOException {
    checkRecordIdExists(recordId);
    int currentRecordId = recordId;
    for (int i = 0; i < MAX_REDIRECTS; i++) {
      long recordOffsetInFile = idToOffset(currentRecordId);
      int offsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
      Page page = storage.pageByOffset(recordOffsetInFile);
      ByteBuffer buffer = page.rawPageBuffer();
      RecordLayout recordLayout = RecordLayout.recordLayout(buffer, offsetOnPage);
      byte recordType = recordLayout.recordType();

      if (redirectToIdRef != null) {
        redirectToIdRef.set(currentRecordId); //will be overwritten if we follow .redirectedToId chain
      }

      if (recordType == RecordLayout.RECORD_TYPE_ACTUAL) {
        int recordPayloadLength = recordLayout.length(buffer, offsetOnPage);
        ByteBuffer slice = buffer.slice(offsetOnPage + recordLayout.headerSize(), recordPayloadLength)
          .asReadOnlyBuffer()
          .order(buffer.order());
        return reader.read(slice);
      }

      if (recordType == RecordLayout.RECORD_TYPE_MOVED) {
        int redirectToId = recordLayout.redirectToId(buffer, offsetOnPage);
        checkRedirectToId(recordId, currentRecordId, redirectToId);
        currentRecordId = redirectToId;
      }
      else {
        throw new AssertionError("RecordType(" + recordType + ") should not appear in the chain: " +
                                 "it is either not implemented yet, or all wrong");
      }
    }
    throw new IOException("record[" + recordId + "].redirectTo chain is too long (>=" + MAX_REDIRECTS + "): circular reference?");
  }

  /**
   * Writer is called with a writeable {@link ByteBuffer} representing current record content (payload).
   * The buffer is prepared for reading: position=0, limit=payload.length, capacity=[current record capacity].
   * <br> <br>
   * Writer is free to read and/or modify the buffer, and return it in an 'after puts' state, i.e.
   * position=[#last byte of payload], new payload content = buffer[0..position].
   * <br> <br>
   * NOTE: this implies that even if the writer makes no changes, only reads the buffer -- it is still required
   * to set buffer.position=limit, because otherwise storage will treat the buffer state as if the new payload
   * length is 0.
   * This is a bit unnatural, so there is a shortcut: if the writer changes nothing, it could just return null.
   * <br> <br>
   * Capacity: if a new payload fits into the buffer passed in -> it could be written right into it.
   * If the new payload requires more space, a writer should allocate its own buffer with enough capacity,
   * write the new payload into it, and return that buffer (in the same 'after puts' state mentioned above),
   * instead of the buffer passed in.
   * Storage will then re-allocate space for the record with capacity >= returned buffer capacity.
   *
   * @param expectedRecordSizeHint          hint to a storage about how big data a writer intends to write. May be used for allocating
   *                                        buffer of that size. <=0 means 'no hints, use default buffer allocation strategy'
   * @param leaveRedirectOnRecordRelocation if the current record is relocated during writing, the old record could be either removed right
   *                                        now (false) or remain as a 'redirect-to' record, so new content could still be accessed by old
   *                                        recordId (true)
   */
  @Override
  public int writeToRecord(int recordId,
                           @NotNull ByteBufferWriter writer,
                           int expectedRecordSizeHint,
                           boolean leaveRedirectOnRecordRelocation) throws IOException {
    //insert new record?
    if (!isValidRecordId(recordId)) {
      ByteBuffer temp = acquireTemporaryBuffer(expectedRecordSizeHint);
      try {
        ByteBuffer bufferWithData = writer.write(temp);
        bufferWithData.flip();

        int recordLength = bufferWithData.limit();
        checkLengthHardLimit(recordLength);
        if (recordLength > maxCapacityForPageSize) {
          throw new IllegalStateException(
            "recordLength(=" + recordLength + ") > maxCapacityForPageSize(=" + maxCapacityForPageSize + ") -- can't fit");
        }

        int capacity = bufferWithData.capacity();
        //Don't check capacity right here -- let allocation strategy first decide how to deal with capacity > MAX
        int requestedRecordCapacity = allocationStrategy.capacity(
          recordLength,
          capacity
        );

        if (requestedRecordCapacity < recordLength) {
          throw new IllegalStateException(
            "Allocation strategy " + allocationStrategy + "(" + recordLength + ", " + capacity + ")" +
            " returns " + requestedRecordCapacity + " < length(=" + recordLength + ")");
        }

        return writeToNewlyAllocatedRecord(bufferWithData, requestedRecordCapacity);
      }
      finally {
        releaseTemporaryBuffer(temp);
      }
    }

    //already existent record
    int currentRecordId = recordId;
    for (int i = 0; i < MAX_REDIRECTS; i++) {
      long recordOffset = idToOffset(currentRecordId);
      int offsetOnPage = storage.toOffsetInPage(recordOffset);
      Page page = storage.pageByOffset(recordOffset);
      ByteBuffer buffer = page.rawPageBuffer();
      RecordLayout recordLayout = RecordLayout.recordLayout(buffer, offsetOnPage);
      byte recordType = recordLayout.recordType();
      if (recordType == RecordLayout.RECORD_TYPE_MOVED) {
        int redirectToId = recordLayout.redirectToId(buffer, offsetOnPage);
        checkRedirectToId(recordId, currentRecordId, redirectToId);
        currentRecordId = redirectToId;
        continue;//hope redirect chains are not too long...
      }
      if (recordType != RecordLayout.RECORD_TYPE_ACTUAL) {
        throw new AssertionError("RecordType(" + recordType + ") should not appear in the chain: " +
                                 "it is either not implemented yet, or all wrong");
      }
      int recordCapacity = recordLayout.capacity(buffer, offsetOnPage);
      int recordActualLength = recordLayout.length(buffer, offsetOnPage);
      //TODO RC: consider 'expectedRecordSizeHint' here? I.e. if expectedRecordSizeHint>record.capacity -> allocate heap buffer
      //         of the size asked, copy actual record content into it?
      int recordPayloadOffset = offsetOnPage + recordLayout.headerSize();
      ByteBuffer recordContent = buffer.slice(recordPayloadOffset, recordCapacity)
        .limit(recordActualLength)
        .order(buffer.order());

      ByteBuffer newRecordContent = writer.write(recordContent);
      if (newRecordContent == null) {
        //returned null means writer decides to skip write -> just return current recordId
        return currentRecordId;
      }

      if (newRecordContent != recordContent) {//writer decides to allocate new buffer for content:
        newRecordContent.flip();
        int newRecordLength = newRecordContent.remaining();
        if (newRecordLength <= recordCapacity) {
          //RC: really, in this case writer should just write data right in the 'recordContent'
          //    buffer, not allocate the new buffer -- but ok, we could deal with it:
          recordLayout.putRecord(buffer, offsetOnPage,
                                 recordCapacity, newRecordLength, NULL_ID, newRecordContent);

          totalLiveRecordsPayloadBytes.addAndGet(newRecordLength - recordActualLength);
        }
        else {//current record is too small for new content -> relocate to a new place
          int newRecordCapacity = allocationStrategy.capacity(newRecordLength, newRecordContent.capacity());
          int newRecordId = writeToNewlyAllocatedRecord(newRecordContent, newRecordCapacity);

          RecordLayout.MovedRecord movedRecordLayout = RecordLayout.MovedRecord.INSTANCE;
          //mark current record as either 'moved' or 'deleted'
          int redirectToId = leaveRedirectOnRecordRelocation ? newRecordId : NULL_ID;
          //Total space occupied by record must remain constant, but record capacity should be
          // changed since MovedRecord has another headerSize than Small|LargeRecord
          int movedRecordCapacity = recordLayout.fullRecordSize(recordCapacity) - movedRecordLayout.headerSize();
          movedRecordLayout.putRecord(buffer, offsetOnPage, movedRecordCapacity, 0, redirectToId, null);

          totalLiveRecordsPayloadBytes.addAndGet(-recordActualLength);
          totalLiveRecordsCapacityBytes.addAndGet(-recordCapacity);
          if (leaveRedirectOnRecordRelocation) {
            recordsRelocated.incrementAndGet();
          }
          else {
            recordsDeleted.incrementAndGet();
          }

          return newRecordId;
        }
      }
      else {//if newRecordContent is null or == recordContent -> changes are already written by writer into the recordContent,
        // we only need to adjust record header:
        recordContent.flip();
        int newRecordLength = recordContent.remaining();
        assert (newRecordLength <= recordCapacity) : newRecordLength + " > " + recordCapacity +
                                                     ": can't be, since recordContent.capacity()==recordCapacity!";
        recordLayout.putLength(buffer, offsetOnPage, newRecordLength);


        totalLiveRecordsPayloadBytes.addAndGet(newRecordLength - recordActualLength);
      }
      return currentRecordId;
    }
    throw new IOException("record[" + recordId + "].redirectTo chain is too long (>=" + MAX_REDIRECTS + "): circular reference?");
  }

  /**
   * Delete record by recordId.
   * <p>
   * Contrary to read/write methods, this method DOES NOT follow redirectTo chain: record to be deleted
   * is the record with id=recordId, redirectToId field is ignored. Why is that: because the main use
   * case of redirectTo chain is to support delayed record removal -- i.e. to give all clients a chance
   * to change their stored recordId to the new one, after the record was moved for some reason. But
   * after all clients have done that, the _stale_ record should be removed (so its space could be
   * reclaimed) -- not the now-actual record referred by redirectTo link. If remove method follows
   * .redirectTo links -- it becomes impossible to remove stale record without affecting its actual
   * counterpart.
   *
   * @throws IllegalStateException if record is already deleted
   */
  @Override
  public void deleteRecord(int recordId) throws IOException {
    checkRecordIdExists(recordId);

    long recordOffset = idToOffset(recordId);
    Page page = storage.pageByOffset(recordOffset);
    int offsetOnPage = storage.toOffsetInPage(recordOffset);
    ByteBuffer buffer = page.rawPageBuffer();
    RecordLayout recordLayout = RecordLayout.recordLayout(buffer, offsetOnPage);
    int recordCapacity = recordLayout.capacity(buffer, offsetOnPage);
    int recordActualLength = recordLayout.length(buffer, offsetOnPage);
    byte recordType = recordLayout.recordType();
    switch (recordType) {
      case RecordLayout.RECORD_TYPE_MOVED -> {
        int redirectToId = recordLayout.redirectToId(buffer, offsetOnPage);
        if (redirectToId == NULL_ID) {
          throw new RecordAlreadyDeletedException(
            "Can't delete record[" + recordId + "]: it was already deleted, " +
            "(wasClosedProperly: " + wasClosedProperly() + ", wasAlwaysClosedProperly: " + wasAlwaysClosedProperly() + ")"
          );
        }

        // (redirectToId=NULL) <=> 'record deleted' ('moved nowhere')
        ((RecordLayout.MovedRecord)recordLayout).putRedirectTo(buffer, offsetOnPage, NULL_ID);
      }
      case RecordLayout.RECORD_TYPE_ACTUAL -> {
        RecordLayout.MovedRecord movedRecordLayout = RecordLayout.MovedRecord.INSTANCE;
        //Total space occupied by record must remain constant, but record capacity should be
        // changed since MovedRecord has another headerSize than Small|LargeRecord
        int deletedRecordCapacity = recordLayout.fullRecordSize(recordCapacity) - movedRecordLayout.headerSize();
        // set (redirectToId=NULL) to mark record as deleted ('moved nowhere')
        movedRecordLayout.putRecord(buffer, offsetOnPage, deletedRecordCapacity, /* length: */ 0, NULL_ID, null);
      }
      default -> throw new AssertionError("RecordType(" + recordType + ") should not appear in the chain: " +
                                          "it is either not implemented yet, or all wrong");
    }

    recordsDeleted.incrementAndGet();
    totalLiveRecordsPayloadBytes.addAndGet(-recordActualLength);
    totalLiveRecordsCapacityBytes.addAndGet(-recordCapacity);
  }

  //TODO int deleteAllForwarders(int recordId) throws IOException;

  /**
   * Scan all records (even deleted one), and deliver their content to processor. ByteBuffer is read-only, and
   * prepared for reading (i.e. position=0, limit=payload.length). For deleted/moved records recordLength is negative
   * see {@link #isRecordActual(int)}.
   * Scanning stops prematurely if processor returns false.
   *
   * @return how many records were processed
   */
  @Override
  public <E extends Exception> int forEach(@NotNull Processor<E> processor) throws IOException, E {
    long storageLength = actualLength();
    int currentId = offsetToId(recordsStartOffset());
    for (int recordNo = 0; ; recordNo++) {
      long recordOffset = idToOffset(currentId);
      Page page = storage.pageByOffset(recordOffset);
      int offsetOnPage = storage.toOffsetInPage(recordOffset);
      ByteBuffer buffer = page.rawPageBuffer();
      RecordLayout recordLayout = RecordLayout.recordLayout(buffer, offsetOnPage);
      byte recordType = recordLayout.recordType();
      int recordCapacity = recordLayout.capacity(buffer, offsetOnPage);
      switch (recordType) {
        case RecordLayout.RECORD_TYPE_ACTUAL, RecordLayout.RECORD_TYPE_MOVED -> {
          int headerSize = recordLayout.headerSize();
          boolean isActual = recordType == RecordLayout.RECORD_TYPE_ACTUAL;
          int recordActualLength = isActual ? recordLayout.length(buffer, offsetOnPage) : -1;
          ByteBuffer slice = isActual ?
                             buffer.slice(offsetOnPage + headerSize, recordActualLength)
                             .asReadOnlyBuffer()
                             .order(buffer.order()) :
                             buffer.slice(offsetOnPage + headerSize, 0)
                             .asReadOnlyBuffer()
                             .order(buffer.order());
          boolean ok = processor.processRecord(currentId, recordCapacity, recordActualLength, slice);
          if (!ok) {
            return recordNo + 1;
          }
        }
        default -> {
          //just skip for now
        }
      }

      long nextRecordOffset = nextRecordOffset(recordOffset, recordLayout, recordCapacity);
      if (nextRecordOffset >= storageLength) {
        return recordNo;
      }

      currentId = offsetToId(nextRecordOffset);
    }
  }

  @Override
  public long sizeInBytes() throws IOException {
    return actualLength();
  }

  @Override
  public boolean isDirty() {
    //RC: as always, with mapped storage it is tricky to say when it is 'dirty' -- 'cos almost all the
    // writes go directly to the mapped buffer, and mapped buffer dirty/flush management is up to OS.
    // We can manage .dirty flag ourself, but it seems an overhead for (almost) nothing -- there are
    // very few real usages for .isDirty() in mmapped storages. I just define: storage is not dirty
    // by default:
    return false;
  }

  @Override
  public void force() throws IOException {
    checkNotClosed();

    putHeaderInt(HeaderLayout.RECORDS_ALLOCATED_OFFSET, recordsAllocated.get());
    putHeaderInt(HeaderLayout.RECORDS_RELOCATED_OFFSET, recordsRelocated.get());
    putHeaderInt(HeaderLayout.RECORDS_DELETED_OFFSET, recordsDeleted.get());
    putHeaderLong(HeaderLayout.RECORDS_LIVE_TOTAL_PAYLOAD_SIZE_OFFSET, totalLiveRecordsPayloadBytes.get());
    putHeaderLong(HeaderLayout.RECORDS_LIVE_TOTAL_CAPACITY_SIZE_OFFSET, totalLiveRecordsCapacityBytes.get());
  }

  @Override
  public void close() throws IOException {
    if (!closed.get()) {
      //Class in general doesn't provide thread-safety guarantees and need a =n external synchronization if used
      // in multithreading.
      // But since it uses mmapped files, concurrency errors in closing/reclaiming may lead to JVM crash, not
      // just program bugs -- hence, a bit of protection does no harm:
      synchronized (this) {//also ensures updateNextRecordId() is finished
        if (!closed.get()) {
          int fileStatusMask = BitUtil.set(
            HeaderLayout.FILE_STATUS_CLOSED_PROPERLY_MASK, HeaderLayout.FILE_STATUS_ALWAYS_CLOSED_PROPERLY_MASK, wasAlwaysClosedProperly
          );
          putHeaderInt(HeaderLayout.FILE_STATUS_OFFSET, fileStatusMask);
          force();

          closed.set(true);

          openTelemetryCallback.close();

          headerPage = null;

          storage.close();
        }
      }
    }
  }

  @Override
  public void closeAndClean() throws IOException {
    close();
    storage.closeAndClean();
  }

  // ============================= implementation: ========================================================================

  private @NotNull Path storagePath() {
    return storage.storagePath();
  }

  // ===================== storage header accessors: ===

  private int readHeaderInt(int offset) throws IOException {
    assert (0 <= offset && offset <= HeaderLayout.HEADER_SIZE - Integer.BYTES)
      : "header offset(=" + offset + ") must be in [0," + (HeaderLayout.HEADER_SIZE - Integer.BYTES) + "]";
    return headerPage().rawPageBuffer().getInt(offset);
  }

  private void putHeaderInt(int offset,
                            int value) throws IOException {
    assert (0 <= offset && offset <= HeaderLayout.HEADER_SIZE - Integer.BYTES)
      : "header offset(=" + offset + ") must be in [0," + (HeaderLayout.HEADER_SIZE - Integer.BYTES) + "]";
    headerPage().rawPageBuffer().putInt(offset, value);
  }

  private long readHeaderLong(int offset) throws IOException {
    assert (0 <= offset && offset <= HeaderLayout.HEADER_SIZE - Long.BYTES)
      : "header offset(=" + offset + ") must be in [0," + (HeaderLayout.HEADER_SIZE - Long.BYTES) + "]";
    return headerPage().rawPageBuffer().getLong(offset);
  }

  private void putHeaderLong(int offset,
                             long value) throws IOException {
    assert (0 <= offset && offset <= HeaderLayout.HEADER_SIZE - Long.BYTES)
      : "header offset(=" + offset + ") must be in [0," + (HeaderLayout.HEADER_SIZE - Long.BYTES) + "]";
    headerPage().rawPageBuffer().putLong(offset, value);
  }

  /**
   * Actual size of data -- i.e. all allocated records.
   * File size is almost always larger than that because {@link MMappedFileStorage} pre-allocates each page
   * in advance.
   */
  private long actualLength() throws IOException {
    return idToOffset(nextRecordId());
  }

  private int nextRecordId() throws IOException {
    Page headerPage = headerPage();
    ByteBuffer headerBuffer = headerPage.rawPageBuffer();
    return (int)INT_HANDLE.getVolatile(headerBuffer, HeaderLayout.NEXT_RECORD_ID_OFFSET);
  }

  //@GuardedBy(this)
  private void updateNextRecordId(int nextRecordId) throws IOException {
    if (nextRecordId <= NULL_ID) {
      throw new IllegalArgumentException("nextRecordId(=" + nextRecordId + ") must be >0");
    }
    Page headerPage = headerPage();
    ByteBuffer headerBuffer = headerPage.rawPageBuffer();
    INT_HANDLE.setVolatile(headerBuffer, HeaderLayout.NEXT_RECORD_ID_OFFSET, nextRecordId);
  }

  private @NotNull Page headerPage() throws ClosedStorageException {
    Page _headerPage = this.headerPage;
    if (_headerPage == null) {
      throw new ClosedStorageException("Storage is closed");
    }
    return _headerPage;
  }

  // ===================== storage records accessors: ================================================ //

  /**
   * content buffer is passed in 'ready for write' state: position=0, limit=[#last byte of payload]
   */
  private int writeToNewlyAllocatedRecord(ByteBuffer content,
                                          int requestedRecordCapacity) throws IOException {
    int pageSize = storage.pageSize();

    int recordLength = content.limit();
    if (recordLength > maxCapacityForPageSize) {
      //Actually, at this point it must be guaranteed recordLength<=maxCapacityForPageSize, but lets check again:
      throw new IllegalStateException(
        "recordLength(=" + recordLength + ") > maxCapacityForPageSize(=" + maxCapacityForPageSize + ") -- can't fit");
    }
    int implementableCapacity = Math.min(requestedRecordCapacity, maxCapacityForPageSize);
    checkCapacityHardLimit(implementableCapacity);


    byte recordSizeType = recordSizeTypeByCapacity(implementableCapacity);
    RecordLayout recordLayout = recordLayoutForType(recordSizeType);
    int fullRecordSize = recordLayout.fullRecordSize(implementableCapacity);
    if (fullRecordSize > pageSize) {
      throw new IllegalArgumentException("record size(header:" + recordLayout.headerSize() + " + capacity:" + implementableCapacity + ")" +
                                         " should be <= pageSize(=" + pageSize + ")");
    }

    IntRef actualRecordSizeRef = new IntRef();//actual record size may be >= requested totalRecordSize
    int newRecordId = allocateSlotForRecord(pageSize, fullRecordSize, actualRecordSizeRef);
    long newRecordOffset = idToOffset(newRecordId);
    int actualRecordSize = actualRecordSizeRef.get();
    int actualRecordCapacity = actualRecordSize - recordLayout.headerSize();
    int newRecordLength = content.remaining();

    //check everything before write anything:
    checkCapacityHardLimit(actualRecordCapacity);
    checkLengthHardLimit(newRecordLength);

    int offsetOnPage = storage.toOffsetInPage(newRecordOffset);
    try {
      Page page = storage.pageByOffset(newRecordOffset);
      recordLayout.putRecord(page.rawPageBuffer(), offsetOnPage,
                             actualRecordCapacity, newRecordLength, NULL_ID,
                             content);
      return newRecordId;
    }
    finally {
      recordsAllocated.incrementAndGet();
      totalLiveRecordsCapacityBytes.addAndGet(actualRecordCapacity);
      totalLiveRecordsPayloadBytes.addAndGet(newRecordLength);
    }
  }

  private void putSpaceFillerRecord(long recordOffset,
                                    int pageSize) throws IOException {
    RecordLayout.PaddingRecord paddingRecord = RecordLayout.PaddingRecord.INSTANCE;

    int offsetInPage = storage.toOffsetInPage(recordOffset);
    int remainingOnPage = pageSize - offsetInPage;

    Page page = storage.pageByOffset(recordOffset);
    int capacity = remainingOnPage - paddingRecord.headerSize();
    paddingRecord.putRecord(page.rawPageBuffer(), offsetInPage, capacity, 0, NULL_ID, null);
  }

  // ===================== monitoring: =============================================================== //

  private static @NotNull BatchCallback setupReportingToOpenTelemetry(@NotNull Path fileName,
                                                                      @NotNull StreamlinedBlobStorageOverMMappedFile storage) {
    Meter meter = TelemetryManager.getInstance().getMeter(PlatformScopesKt.Storage);

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
        try {
          recordsAllocated.record(storage.recordsAllocated(), attributes);
          recordsRelocated.record(storage.recordsRelocated(), attributes);
          recordsDeleted.record(storage.recordsDeleted(), attributes);
          totalLiveRecordsPayloadBytes.record(storage.totalLiveRecordsPayloadBytes(), attributes);
          totalLiveRecordsCapacityBytes.record(storage.totalLiveRecordsCapacityBytes(), attributes);
        }
        catch (ClosedStorageException e) {
          //just skip
        }
      },
      recordsAllocated, recordsRelocated, recordsDeleted,
      totalLiveRecordsPayloadBytes, totalLiveRecordsCapacityBytes
    );
  }
}
