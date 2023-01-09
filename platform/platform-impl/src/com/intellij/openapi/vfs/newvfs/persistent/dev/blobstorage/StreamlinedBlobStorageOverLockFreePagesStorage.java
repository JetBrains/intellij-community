// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage;

import com.intellij.diagnostic.telemetry.TraceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IntRef;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.PagedFileStorageLockFree;
import com.intellij.util.io.pagecache.Page;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements {@link StreamlinedBlobStorage} blobs over {@link PagedFileStorageLockFree} storage.
 * Implementation is thread-safe, and mostly relies on page-level locks to protect data access.
 * <br/>
 * @deprecated  StreamlinedBlobStorageLargeSizeOverLockFreePagesStorage is a replacement with larger records
 */
@Deprecated
public class StreamlinedBlobStorageOverLockFreePagesStorage implements StreamlinedBlobStorage {
  private static final Logger LOG = Logger.getInstance(StreamlinedBlobStorageOverLockFreePagesStorage.class);

  /* ======== Persistent format: =================================================================== */

  // Persistent format: (header) (records)*
  //  header: storageVersion[int32], safeCloseMagic[int32] ...monitoring fields... dataFormatVersion[int32]
  //  record:
  //          recordHeader: capacity[uint16], actualLength[uint16], redirectToOffset[int32]
  //          recordData:   byte[actualLength]?
  //
  //  1. capacity is the allocated size of the record payload _excluding_ header, so
  //     nextRecordOffset = currentRecordOffset + recordHeader(=8) + recordCapacity
  //     There are 2 bytes for capacity now, so 0xFFFF (65535) is the max capacity supported today.
  //
  //  2. actualLength (<=capacity) is actual size of record payload written into the record, so
  //     recordData[0..actualLength) contains actual data, and recordData[actualLength..capacity)
  //     contains trash.
  //     There are few reserved values of actualLength (PADDING_RECORD_MARK, MOVED_RECORD_MARK, DELETED_RECORD_MARK)
  //     that marked removed/moved records -- to be reclaimed/compacted.
  //
  //  3. redirectToOffset is a 'forwarding pointer' for records that was moved (e.g. re-allocated).
  //
  //  4. records are always allocated on a single page: i.e. record never breaks a page boundary.
  //     If a record doesn't fit the current page, it is moved to another page (remaining space on
  //     page is filled by placeholder record, if needed).

  //TODO RC: implement space reclamation: re-use space of deleted records for the newly allocated ones.
  //         Need to keep a free-list.
  //IDEA RC: store fixed-size free-list (tuples recordId, capacity) right after header on a first page, so on load we
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

  public static final int STORAGE_VERSION_CURRENT = 2;


  private static final int FILE_STATUS_OPENED = 0;
  private static final int FILE_STATUS_SAFELY_CLOSED = 1;
  private static final int FILE_STATUS_CORRUPTED = 2;

  //=== HEADER format:

  /**
   * Version of this storage persistent format -- i.e. if !=STORAGE_VERSION_CURRENT, this class probably
   * can't read the file. Managed by the storage itself.
   */
  private static final int HEADER_OFFSET_STORAGE_VERSION = 0;
  private static final int HEADER_OFFSET_FILE_STATUS = HEADER_OFFSET_STORAGE_VERSION + Integer.BYTES;

  private static final int HEADER_OFFSET_RECORDS_ALLOCATED = HEADER_OFFSET_FILE_STATUS + Integer.BYTES;
  private static final int HEADER_OFFSET_RECORDS_RELOCATED = HEADER_OFFSET_RECORDS_ALLOCATED + Integer.BYTES;
  private static final int HEADER_OFFSET_RECORDS_DELETED = HEADER_OFFSET_RECORDS_RELOCATED + Integer.BYTES;
  private static final int HEADER_OFFSET_RECORDS_LIVE_TOTAL_PAYLOAD_SIZE = HEADER_OFFSET_RECORDS_DELETED + Integer.BYTES;
  private static final int HEADER_OFFSET_RECORDS_LIVE_TOTAL_CAPACITY_SIZE = HEADER_OFFSET_RECORDS_LIVE_TOTAL_PAYLOAD_SIZE + Long.BYTES;
  /**
   * Version of data, stored in a blobs, managed by client code.
   * MAYBE this should not be a part of standard header, but implemented on the top of 'additional headers'
   * (see below)?
   */
  private static final int HEADER_OFFSET_DATA_FORMAT_VERSION = HEADER_OFFSET_RECORDS_LIVE_TOTAL_CAPACITY_SIZE + Long.BYTES;
  private static final int HEADER_SIZE = HEADER_OFFSET_DATA_FORMAT_VERSION + Integer.BYTES;

  //MAYBE allow to reserve additional space in header for something implemented on the top of the storage?


  //=== RECORD format:

  private static final int RECORD_OFFSET_CAPACITY = 0;
  private static final int RECORD_OFFSET_ACTUAL_LENGTH = RECORD_OFFSET_CAPACITY + Short.BYTES;
  private static final int RECORD_OFFSET_REDIRECT_TO = RECORD_OFFSET_ACTUAL_LENGTH + Short.BYTES;
  private static final int RECORD_HEADER_SIZE = RECORD_OFFSET_REDIRECT_TO + Integer.BYTES;


  /**
   * Max capacity (inclusive) of a record (2bytes)
   */
  public static final int MAX_CAPACITY = 0xFFFF;
  /**
   * Max length of a record (inclusive): stored in 2 bytes now, but 3 values are reserved (see below)
   */
  public static final int MAX_LENGTH = 0xFFFF - 3;


  /**
   * Value of recordActualLength used to mark 'padding' record (fake record to fill space till the
   * end of page, if there is not enough space remains on current page for actual record to fit)
   */
  private static final int PADDING_RECORD_MARK = 0xFFFF;
  /**
   * Value of recordActualLength used as 'moved' (reallocated) mark
   */
  private static final int MOVED_RECORD_MARK = 0xFFFF - 1;
  /**
   * Value of recordActualLength used as 'deleted' mark
   */
  private static final int DELETED_RECORD_MARK = 0xFFFF - 2;

  /**
   * Use offsets stepping with OFFSET_BUCKET -- this allows to address OFFSET_BUCKET times more bytes with
   * int offset (at the cost of more sparse disk/memory representation)
   */
  private static final int OFFSET_BUCKET = 8;


  /* ===================================================================================================== */


  @NotNull
  private final PagedFileStorageLockFree pagedStorage;

  /** To avoid write file header to already closed storage */
  private final AtomicBoolean closed = new AtomicBoolean(false);

  @NotNull
  private final SpaceAllocationStrategy allocationStrategy;


  /** Field could be read as volatile, but writes are protected with this intrinsic lock */
  //@GuardedBy(this)
  private volatile int nextRecordId;

  private final ThreadLocal<ByteBuffer> threadLocalBuffer;

  //==== monitoring fields: =======================================================================================
  // They are frequently accessed, read/write them each time into a file header is too expensive (and verbose),
  // hence use caching fields instead:

  private final AtomicInteger recordsAllocated = new AtomicInteger();
  private final AtomicInteger recordsRelocated = new AtomicInteger();
  private final AtomicInteger recordsDeleted = new AtomicInteger();
  private final AtomicLong totalLiveRecordsPayloadBytes = new AtomicLong();
  private final AtomicLong totalLiveRecordsCapacityBytes = new AtomicLong();
  private final BatchCallback openTelemetryCallback;


  public StreamlinedBlobStorageOverLockFreePagesStorage(final @NotNull PagedFileStorageLockFree pagedStorage,
                                                        final @NotNull SpaceAllocationStrategy allocationStrategy) throws IOException {
    this.pagedStorage = pagedStorage;
    this.allocationStrategy = allocationStrategy;

    final int defaultCapacity = allocationStrategy.defaultCapacity();
    final boolean useNativeByteOrder = pagedStorage.isNativeBytesOrder();
    threadLocalBuffer = ThreadLocal.withInitial(() -> {
      final ByteBuffer buffer = ByteBuffer.allocate(defaultCapacity);
      if (useNativeByteOrder) {
        buffer.order(ByteOrder.nativeOrder());
      }
      return buffer;
    });

    final int pageSize = pagedStorage.getPageSize();
    if (pageSize < headerSize()) {
      throw new IllegalStateException("header(" + headerSize() + " b) must fit on 0th page(" + pageSize + " b)");
    }

    synchronized (this) {//protect nextRecordId modifications:
      try (final Page headerPage = pagedStorage.pageByIndex(0, /*forWrite: */ true)) {
        headerPage.lockPageForWrite();
        try {
          final long length = pagedStorage.length();
          if (length >= headerSize()) {
            final int version = readHeaderInt(HEADER_OFFSET_STORAGE_VERSION);
            if (version != STORAGE_VERSION_CURRENT) {
              throw new IOException(
                "Can't read file[" + pagedStorage + "]: version(" + version + ") != storage version (" + STORAGE_VERSION_CURRENT + ")");
            }
            final int fileStatus = readHeaderInt(HEADER_OFFSET_FILE_STATUS);
            if (fileStatus != FILE_STATUS_SAFELY_CLOSED) {
              throw new IOException(
                "Can't read file[" + pagedStorage + "]: status(" + fileStatus + ") != SAFELY_CLOSED (" + FILE_STATUS_SAFELY_CLOSED + ")");
            }
            if (length > Integer.MAX_VALUE * (long)OFFSET_BUCKET) {
              throw new IOException(
                "Can't read file[" + pagedStorage + "]: too big, " + length + " > Integer.MAX_VALUE * " + OFFSET_BUCKET);
            }
            nextRecordId = offsetToId(length);

            recordsAllocated.set(readHeaderInt(HEADER_OFFSET_RECORDS_ALLOCATED));
            recordsRelocated.set(readHeaderInt(HEADER_OFFSET_RECORDS_RELOCATED));
            recordsDeleted.set(readHeaderInt(HEADER_OFFSET_RECORDS_DELETED));
            totalLiveRecordsPayloadBytes.set(readHeaderLong(HEADER_OFFSET_RECORDS_LIVE_TOTAL_PAYLOAD_SIZE));
            totalLiveRecordsCapacityBytes.set(readHeaderLong(HEADER_OFFSET_RECORDS_LIVE_TOTAL_CAPACITY_SIZE));
          }
          else {
            nextRecordId = offsetToId(recordsStartOffset());
          }
          putHeaderInt(HEADER_OFFSET_STORAGE_VERSION, STORAGE_VERSION_CURRENT);
          putHeaderInt(HEADER_OFFSET_FILE_STATUS, FILE_STATUS_OPENED);
        }
        finally {
          headerPage.unlockPageForWrite();
        }
      }
    }

    openTelemetryCallback = setupReportingToOpenTelemetry(pagedStorage.getFile().getFileName());
  }

  @Override
  public int getStorageVersion() throws IOException {
    return readHeaderInt(HEADER_OFFSET_STORAGE_VERSION);
  }

  @Override
  public int getDataFormatVersion() throws IOException {
    return readHeaderInt(HEADER_OFFSET_DATA_FORMAT_VERSION);
  }

  @Override
  public void setDataFormatVersion(final int expectedVersion) throws IOException {
    putHeaderInt(HEADER_OFFSET_DATA_FORMAT_VERSION, expectedVersion);
  }


  @Override
  public <Out> Out readRecord(final int recordId,
                              final @NotNull Reader<Out> reader) throws IOException {
    return readRecord(recordId, reader, null);
  }

  @Override
  public boolean hasRecord(final int recordId) throws IOException {
    return hasRecord(recordId, null);
  }

  @Override
  public boolean hasRecord(final int recordId,
                           final @Nullable IntRef redirectToIdRef) throws IOException {
    if (recordId == NULL_ID) {
      return false;
    }
    checkRecordIdValid(recordId);
    if (!isRecordIdAllocated(recordId)) {
      return false;
    }
    final long recordOffset = idToOffset(recordId);
    try (final Page page = pagedStorage.pageByOffset(recordOffset, /*forWrite: */ false)) {
      final int offsetOnPage = pagedStorage.toOffsetInPage(recordOffset);
      page.lockPageForRead();
      try {
        final ByteBuffer buffer = page.rawPageBuffer();
        final int recordActualLength = readRecordLength(buffer, offsetOnPage);
        final int recordRedirectedToId = readRecordRedirectToId(buffer, offsetOnPage);

        if (redirectToIdRef != null) {
          redirectToIdRef.set(recordId);
        }
        if (!isRecordActual(recordActualLength)) {
          if (!isValidRecordId(recordRedirectedToId)) {
            return false;
          }
          //MAYBE RC: try to avoid recursion here, since we lock >1 pages while really only need to lock 1
          return hasRecord(recordRedirectedToId, redirectToIdRef);
        }

        return true;
      }
      finally {
        page.unlockPageForRead();
      }
    }
  }

  //TODO RC: consider change way of dealing with ByteBuffers: what-if all methods will have same semantics,
  //         i.e. buffer contains payload[0..limit]? I.e. all methods passing buffers in such a state, and
  //         all methods returned buffers in such a state?

  /**
   * reader will be called with read-only ByteBuffer set up for reading the record content (payload):
   * i.e. position=0, limit=payload.length. Reader is free to do whatever it likes with the buffer.
   *
   * @param redirectToIdRef if not-null, will contain actual recordId of the record,
   *                        which could be different from recordId passed in if record was moved (e.g.
   *                        re-allocated in a new place) and recordId used to call the method is now
   *                        outdated. Clients could still use old recordId, but better to replace
   *                        this outdated id with actual one, since it improves performance (at least)
   */
  @Override
  public <Out> Out readRecord(final int recordId,
                              final @NotNull Reader<Out> reader,
                              final @Nullable IntRef redirectToIdRef) throws IOException {
    checkRecordIdExists(recordId);
    final long recordOffset = idToOffset(recordId);
    try (final Page page = pagedStorage.pageByOffset(recordOffset, /*forWrite: */ false)) {
      final int offsetOnPage = pagedStorage.toOffsetInPage(recordOffset);
      page.lockPageForRead();
      try {
        final ByteBuffer buffer = page.rawPageBuffer();
        final int recordCapacity = readRecordCapacity(buffer, offsetOnPage);
        final int recordActualLength = readRecordLength(buffer, offsetOnPage);
        final int recordRedirectedToId = readRecordRedirectToId(buffer, offsetOnPage);

        if (redirectToIdRef != null) {
          redirectToIdRef.set(recordId);//will be overwritten as we follow .recordRedirectedToId chain
        }
        if (!isRecordActual(recordActualLength)) {
          if (!isValidRecordId(recordRedirectedToId)) {
            throw new IOException("Record[" + recordId + "] is deleted");
          }
          //MAYBE RC: try to avoid recursion here, since we lock >1 pages while really only need to lock 1
          //FIXME RC: recursion is much worse with per-page locking, since it could lead to deadlocks!
          return readRecord(recordRedirectedToId, reader, redirectToIdRef);
        }

        final ByteBuffer slice = buffer.slice(offsetOnPage + RECORD_HEADER_SIZE, recordActualLength)
          .asReadOnlyBuffer()
          .order(buffer.order());
        return reader.read(slice);
      }
      finally {
        page.unlockPageForRead();
      }
    }
  }

  @Override
  public int writeToRecord(final int recordId,
                           final @NotNull Writer writer) throws IOException {
    return writeToRecord(recordId, writer, /*expectedRecordSizeHint: */ -1);
  }

  @Override
  public int writeToRecord(final int recordId,
                           final @NotNull Writer writer,
                           final int expectedRecordSizeHint) throws IOException {
    return writeToRecord(recordId, writer, expectedRecordSizeHint, /* leaveRedirectOnRecordRelocation: */ false);
  }

  /**
   * Writer is called with writeable ByteBuffer represented current record content (payload).
   * Buffer is prepared for read: position=0, limit=payload.length, capacity=[current record capacity].
   * <br> <br>
   * Writer is free to read and/or modify the buffer, and return it in a 'after puts' state, i.e.
   * position=[#last byte of payload], new payload content = buffer[0..position].
   * <br> <br>
   * NOTE: this implies that even if writer writes nothing, only reads -- it must set
   * buffer.position=limit, because otherwise storage will treat it as if record should be set length=0
   * For simplicity, if writer change nothing, it could return null.
   * <br> <br>
   * Capacity: if new payload fits into buffer passed in -> it could be written right into it. If new
   * payload requires more space, writer should allocate its own buffer with enough capacity, write
   * new payload into it, and return that buffer (in a 'after puts' state), instead of buffer passed
   * in. Storage will re-allocate space for the record with capacity >= returned buffer capacity.
   *
   * @param expectedRecordSizeHint          hint to a storage about how big data writer intend to write. May be used for allocating buffer
   *                                        of that size. <=0 means 'no hints, use default buffer allocation strategy'
   * @param leaveRedirectOnRecordRelocation if current record is relocated during writing, old record could be either removed right now,
   *                                        or remain as 'redirect-to' record, so new content could still be accesses by old recordId.
   */
  @Override
  public int writeToRecord(final int recordId,
                           final @NotNull Writer writer,
                           final int expectedRecordSizeHint,
                           final boolean leaveRedirectOnRecordRelocation) throws IOException {
    //insert new record?
    if (!isValidRecordId(recordId)) {
      final ByteBuffer temp = acquireTemporaryBuffer(expectedRecordSizeHint);
      try {
        final ByteBuffer bufferWithData = writer.write(temp);
        bufferWithData.flip();
        final int recordLength = bufferWithData.limit();
        checkLength(recordLength);

        final int capacity = bufferWithData.capacity();
        //Don't check capacity right here -- let allocation strategy first decide how to deal with capacity > MAX
        final int recordCapacity = allocationStrategy.capacity(
          recordLength,
          capacity
        );
        checkCapacity(recordCapacity);

        if (recordCapacity < bufferWithData.limit()) {
          throw new IllegalStateException(
            "Allocation strategy " + allocationStrategy + "(" + recordLength + ", " + capacity + ")" +
            " returns " + recordCapacity + " < length(" + recordLength + ")");
        }
        return writeToNewlyAllocatedRecord(bufferWithData, recordCapacity);
      }
      finally {
        releaseTemporaryBuffer(temp);
      }
    }

    //already existent record
    final long recordOffset = idToOffset(recordId);
    try (final Page page = pagedStorage.pageByOffset(recordOffset, /*forWrite: */ true)) {
      final int offsetOnPage = pagedStorage.toOffsetInPage(recordOffset);
      page.lockPageForWrite();
      try {
        final ByteBuffer buffer = page.rawPageBuffer();
        final int recordCapacity = readRecordCapacity(buffer, offsetOnPage);
        final int recordActualLength = readRecordLength(buffer, offsetOnPage);
        final int recordRedirectedToId = readRecordRedirectToId(buffer, offsetOnPage);

        if (!isRecordActual(recordActualLength)) { //record deleted or moved: check was it moved to a new location?
          if (!isValidRecordId(recordRedirectedToId)) {
            throw new IOException("Can't write to record[" + recordId + "]: it was deleted");
          }
          //hope redirect chains are not too long...
          return writeToRecord(recordRedirectedToId, writer, expectedRecordSizeHint, leaveRedirectOnRecordRelocation);
        }

        //TODO RC: consider 'expectedRecordSizeHint' here? I.e. if expectedRecordSizeHint>record.capacity -> allocate heap buffer
        //         of the size asked, copy actual record content into it?
        final int recordDataStartIndex = offsetOnPage + RECORD_HEADER_SIZE;
        final ByteBuffer recordContent = buffer.slice(recordDataStartIndex, recordCapacity)
          .limit(recordActualLength)
          .order(buffer.order());

        final ByteBuffer newRecordContent = writer.write(recordContent);
        if (newRecordContent == null) {
          //returned null means writer decides to skip write -> just return current recordId
          return recordId;
        }

        if (newRecordContent != recordContent) {//writer decides to allocate new buffer for content:
          newRecordContent.flip();
          final int newRecordLength = newRecordContent.remaining();
          if (newRecordLength <= recordCapacity) {
            //RC: really, in this case writer should just write data right in the 'recordContent'
            //    buffer, not allocate the new buffer -- but ok, we could deal with it:
            putRecordLength(buffer, offsetOnPage, newRecordLength);
            putRecordPayload(buffer, offsetOnPage, newRecordContent, newRecordLength);
            page.regionModified(offsetOnPage, RECORD_HEADER_SIZE + newRecordLength);

            totalLiveRecordsPayloadBytes.addAndGet(newRecordLength - recordActualLength);
          }
          else {//current record is too small for new content -> relocate to a new place
            final int newRecordId = writeToNewlyAllocatedRecord(newRecordContent, newRecordContent.capacity());

            //mark current record as either 'moved' or 'deleted'
            final int recordMark = leaveRedirectOnRecordRelocation ? MOVED_RECORD_MARK : DELETED_RECORD_MARK;
            putRecordLengthMark(buffer, offsetOnPage, recordMark);
            putRecordRedirectTo(buffer, offsetOnPage, newRecordId);
            //could cut first 2 bytes (capacity) from the modified region, but doesn't worth it:
            page.regionModified(offsetOnPage, RECORD_HEADER_SIZE);

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
          final int newRecordLength = recordContent.remaining();
          assert (newRecordLength <= recordCapacity) : newRecordLength + " > " + recordCapacity +
                                                       ": can't be, since recordContent.capacity()==recordCapacity!";
          putRecordLength(buffer, offsetOnPage, newRecordLength);
          page.regionModified(offsetOnPage, RECORD_HEADER_SIZE + newRecordLength);

          totalLiveRecordsPayloadBytes.addAndGet(newRecordLength - recordActualLength);
        }
        return recordId;
      }
      finally {
        page.unlockPageForWrite();
      }
    }
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
  public void deleteRecord(final int recordId) throws IOException {
    checkRecordIdExists(recordId);

    final long recordOffset = idToOffset(recordId);
    try (final Page page = pagedStorage.pageByOffset(recordOffset, /*forWrite: */ true)) {
      final int offsetOnPage = pagedStorage.toOffsetInPage(recordOffset);
      page.lockPageForWrite();
      try {
        final ByteBuffer buffer = page.rawPageBuffer();
        final int recordCapacity = readRecordCapacity(buffer, offsetOnPage);
        final int recordActualLength = readRecordLength(buffer, offsetOnPage);
        final int recordRedirectedToId = readRecordRedirectToId(buffer, offsetOnPage);

        if (isRecordDeleted(recordActualLength)) {//record deleted or moved: check was it moved to a new location?
          throw new IllegalStateException("Can't delete record[" + recordId + "]: it was already deleted");
        }
        else {
          putRecordLengthMark(buffer, offsetOnPage, DELETED_RECORD_MARK);
          putRecordRedirectTo(buffer, offsetOnPage, NULL_ID);

          //we could exclude first 2 bytes (capacity) from modified, but doesn't worth it:
          page.regionModified(offsetOnPage, RECORD_HEADER_SIZE);

          recordsDeleted.incrementAndGet();
          totalLiveRecordsPayloadBytes.addAndGet(-recordActualLength);
          totalLiveRecordsCapacityBytes.addAndGet(-recordCapacity);
        }
      }
      finally {
        page.unlockPageForWrite();
      }
    }
  }

  //TODO int deleteAllForwarders(final int recordId) throws IOException;

  /**
   * Scan all records (even deleted one), and deliver their content to processor. ByteBuffer is read-only, and
   * prepared for reading (i.e. position=0, limit=payload.length). For deleted/moved records recordLength is negative
   * see {@link #isRecordActual(int)}.
   * Scanning stops prematurely if processor returns false.
   *
   * @return how many records were processed
   */
  @Override
  public <E extends Exception> int forEach(final @NotNull Processor<E> processor) throws IOException, E {
    final long storageLength = pagedStorage.length();
    int currentId = offsetToId(recordsStartOffset());
    for (int recordNo = 0; ; recordNo++) {
      final long recordOffset = idToOffset(currentId);
      try (final Page page = pagedStorage.pageByOffset(recordOffset, /*forWrite: */ false)) {
        final int offsetOnPage = pagedStorage.toOffsetInPage(recordOffset);
        page.lockPageForRead();
        try {
          final ByteBuffer buffer = page.rawPageBuffer();
          final int recordCapacity = readRecordCapacity(buffer, offsetOnPage);
          final int recordActualLength = readRecordLength(buffer, offsetOnPage);
          if (!isCorrectCapacity(recordCapacity)) {
            throw new IOException("record[" + recordOffset + "].capacity(=" + recordCapacity + ") " +
                                  "is out of bounds [1," + MAX_CAPACITY + "] -> file has incorrect format or broken");
          }
          if (!isCorrectLengthFieldValue(recordActualLength)) {
            throw new IOException("record[" + recordOffset + "].length(=" + recordActualLength + ") " +
                                  "is out of bounds [0," + MAX_CAPACITY + "] -> file has incorrect format or broken");
          }

          final ByteBuffer slice = isRecordActual(recordActualLength) ?
                                   buffer.slice(offsetOnPage + RECORD_HEADER_SIZE, recordActualLength)
                                     .asReadOnlyBuffer()
                                     .order(buffer.order()) :
                                   buffer.slice(offsetOnPage + RECORD_HEADER_SIZE, 0)
                                     .asReadOnlyBuffer()
                                     .order(buffer.order());
          final boolean ok = processor.processRecord(currentId, recordCapacity, recordActualLength, slice);
          if (!ok) {
            return recordNo;
          }

          final long nextRecordOffset = nextRecordOffset(recordOffset, recordCapacity);
          if (nextRecordOffset >= storageLength) {
            return recordNo;
          }

          currentId = offsetToId(nextRecordOffset);
        }
        finally {
          page.unlockPageForRead();
        }
      }
    }
  }

  @Override
  public boolean isRecordActual(final int recordActualLength) {
    return 0 <= recordActualLength && recordActualLength < DELETED_RECORD_MARK;
  }

  @Override
  public int maxPayloadSupported() {
    return MAX_LENGTH - RECORD_HEADER_SIZE;
  }

  // === monitoring information accessors: ===================

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
  public long sizeInBytes() {
    return pagedStorage.length();
  }


  @Override
  public boolean isDirty() {
    return pagedStorage.isDirty();
  }

  @Override
  public void force() throws IOException {
    checkNotClosed();
    try (final Page headerPage = pagedStorage.pageByIndex(0, /*forWrite: */ true)) {
      headerPage.lockPageForWrite();
      try {
        putHeaderInt(HEADER_OFFSET_FILE_STATUS, FILE_STATUS_SAFELY_CLOSED);

        putHeaderInt(HEADER_OFFSET_RECORDS_ALLOCATED, recordsAllocated.get());
        putHeaderInt(HEADER_OFFSET_RECORDS_RELOCATED, recordsRelocated.get());
        putHeaderInt(HEADER_OFFSET_RECORDS_DELETED, recordsDeleted.get());
        putHeaderLong(HEADER_OFFSET_RECORDS_LIVE_TOTAL_PAYLOAD_SIZE, totalLiveRecordsPayloadBytes.get());
        putHeaderLong(HEADER_OFFSET_RECORDS_LIVE_TOTAL_CAPACITY_SIZE, totalLiveRecordsCapacityBytes.get());
      }
      finally {
        headerPage.unlockPageForWrite();
      }
    }
    pagedStorage.force();
  }

  @Override
  public void close() throws IOException {
    //.close() methods are better to be idempotent, i.e. not throw exceptions on repeating calls,
    // but just silently ignore attempts to 'close already closed'. And pagedStorage conforms with
    // that. But in .force() we write file status and other header fields, and without .closed
    // flag we'll do that even on already closed pagedStorage, which leads to exception.
    if (!closed.get()) {
      force();
      openTelemetryCallback.close();
      closed.set(true);
    }

    //MAYBE RC: it shouldn't be this class's responsibility to close pagedStorage, since not this class creates it?
    //          Better whoever creates it -- is responsible for closing it?
    try {
      pagedStorage.close();
    }
    catch (InterruptedException e) {
      ExceptionUtil.rethrow(e);
    }
  }

  @Override
  public String toString() {
    return "StreamlinedBlobStorageOverLockFree{" + pagedStorage + "}{nextRecordId: " + nextRecordId + '}';
  }


  // ============================= implementation: ========================================================================

  private void checkNotClosed() throws ClosedStorageException {
    if (closed.get()) {
      throw new ClosedStorageException("Storage " + pagedStorage + " is already closed");
    }
  }

  // === storage header accessors: ===

  private int readHeaderInt(final int offset) throws IOException {
    assert (0 <= offset && offset <= HEADER_SIZE - Integer.BYTES)
      : "header offset(=" + offset + ") must be in [0," + (HEADER_SIZE - Integer.BYTES) + "]";
    return pagedStorage.getInt(offset);
  }

  private void putHeaderInt(final int offset,
                            final int value) throws IOException {
    assert (0 <= offset && offset <= HEADER_SIZE - Integer.BYTES)
      : "header offset(=" + offset + ") must be in [0," + (HEADER_SIZE - Integer.BYTES) + "]";
    pagedStorage.putInt(offset, value);
  }

  private long readHeaderLong(final int offset) throws IOException {
    assert (0 <= offset && offset <= HEADER_SIZE - Long.BYTES)
      : "header offset(=" + offset + ") must be in [0," + (HEADER_SIZE - Long.BYTES) + "]";
    return pagedStorage.getLong(offset);
  }

  private void putHeaderLong(final int offset,
                             final long value) throws IOException {
    assert (0 <= offset && offset <= HEADER_SIZE - Long.BYTES)
      : "header offset(=" + offset + ") must be in [0," + (HEADER_SIZE - Long.BYTES) + "]";
    pagedStorage.putLong(offset, value);
  }

  private long headerSize() {
    return HEADER_SIZE;
  }


  // === storage records accessors: ===

  private long recordsStartOffset() {
    final long headerSize = headerSize();
    if (headerSize % OFFSET_BUCKET > 0) {
      return (headerSize / OFFSET_BUCKET + 1) * OFFSET_BUCKET;
    }
    else {
      return (headerSize / OFFSET_BUCKET) * OFFSET_BUCKET;
    }
  }

  /**
   * content buffer is passed in 'ready for write' state: position=0, limit=[#last byte of payload]
   */
  private int writeToNewlyAllocatedRecord(final ByteBuffer content,
                                          final int newRecordCapacity) throws IOException {
    final int pageSize = pagedStorage.getPageSize();
    final int totalRecordSize = RECORD_HEADER_SIZE + newRecordCapacity;
    if (totalRecordSize > pageSize) {
      throw new IllegalArgumentException(
        "record size(header:" + RECORD_HEADER_SIZE + " +capacity:" + newRecordCapacity + ") should be <= pageSize(=" + pageSize + ")");
    }

    final IntRef actualRecordSizeRef = new IntRef();//actual record size may be >= requested totalRecordSize 
    final int newRecordId = allocateSlotForRecord(pageSize, totalRecordSize, actualRecordSizeRef);
    final long newRecordOffset = idToOffset(newRecordId);
    final int actualRecordSize = actualRecordSizeRef.get();
    final int actualRecordCapacity = actualRecordSize - RECORD_HEADER_SIZE;

    try (final Page page = pagedStorage.pageByOffset(newRecordOffset, /*forWrite: */ true)) {
      final int newRecordLength = content.remaining();
      final int offsetOnPage = pagedStorage.toOffsetInPage(newRecordOffset);

      //check everything before write anything:
      checkCapacity(actualRecordCapacity);
      checkLength(newRecordLength);

      page.write(offsetOnPage, actualRecordSize, buffer -> {
        putRecordCapacity(buffer, offsetOnPage, actualRecordCapacity);
        putRecordLength(buffer, offsetOnPage, newRecordLength);
        putRecordRedirectTo(buffer, offsetOnPage, NULL_ID);
        putRecordPayload(buffer, offsetOnPage, content, newRecordLength);
        return buffer;
      });

      recordsAllocated.incrementAndGet();
      totalLiveRecordsCapacityBytes.addAndGet(actualRecordCapacity);
      totalLiveRecordsPayloadBytes.addAndGet(newRecordLength);

      return newRecordId;
    }
  }

  private int allocateSlotForRecord(final int pageSize,
                                    final int totalRecordSize,
                                    final @NotNull IntRef actualRecordSize) throws IOException {
    if (totalRecordSize > pageSize) {
      throw new IllegalArgumentException("recordSize(" + totalRecordSize + " b) must be <= pageSize(" + pageSize + " b)");
    }
    //MAYBE RC: all this could be implemented as CAS-loop, without lock
    synchronized (this) {// protect nextRecordId modifications:
      while (true) {     // [totalRecordSize <= pageSize] =implies=> [loop must finish in <=2 iterations]
        final int newRecordId = nextRecordId;
        final long recordStartOffset = idToOffset(newRecordId);
        final int offsetOnPage = pagedStorage.toOffsetInPage(recordStartOffset);
        final int recordSizeRoundedUp = roundSizeUpToBucket(offsetOnPage, pageSize, totalRecordSize);
        final long recordEndOffset = recordStartOffset + recordSizeRoundedUp - 1;
        final long startPage = recordStartOffset / pageSize;
        //we don't want record to be broken by page boundary, so if the current record steps out of the current
        // page -> we move the entire record to the next page, and pad the space remaining on the current page
        // with filler (padding) record:
        final long endPage = recordEndOffset / pageSize;
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
        final long nextPageStartOffset = (startPage + 1) * pageSize;
        nextRecordId = offsetToId(nextPageStartOffset);
        assert idToOffset(nextRecordId) == nextPageStartOffset : "idToOffset(" + nextRecordId + ")=" + idToOffset(nextRecordId) +
                                                                 " != nextPageStartOffset(" + nextPageStartOffset + ")";
      }
    }
  }

  private void putSpaceFillerRecord(final long recordOffset,
                                    final int pageSize) throws IOException {
    try (final Page page = pagedStorage.pageByOffset(recordOffset, /*forWrite: */ true)) {
      final int offsetInPage = pagedStorage.toOffsetInPage(recordOffset);
      page.lockPageForWrite();
      try {
        final ByteBuffer buffer = page.rawPageBuffer();
        final int remainingOnPage = pageSize - offsetInPage;
        if (remainingOnPage >= RECORD_HEADER_SIZE) {
          putRecordCapacity(buffer, offsetInPage, (remainingOnPage - RECORD_HEADER_SIZE));
          putRecordLengthMark(buffer, offsetInPage, PADDING_RECORD_MARK);
          putRecordRedirectTo(buffer, offsetInPage, NULL_ID);

          page.regionModified(offsetInPage, RECORD_HEADER_SIZE);
        }
        //if remainingOnPage < RECORD_HEADER_SIZE we can't put placeholder record, hence leave it as-is,
        //   and process this case in .nextRecordOffset()
      }
      finally {
        page.unlockPageForWrite();
      }
    }
  }

  private long nextRecordOffset(final long recordOffset,
                                final int recordCapacity) {
    final long nextOffset = recordOffset + RECORD_HEADER_SIZE + recordCapacity;

    final int offsetOnPage = pagedStorage.toOffsetInPage(nextOffset);
    final int pageSize = pagedStorage.getPageSize();
    if (pageSize - offsetOnPage < RECORD_HEADER_SIZE) {
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


  @NotNull
  private ByteBuffer acquireTemporaryBuffer(final int expectedRecordSizeHint) {
    final ByteBuffer temp = threadLocalBuffer.get();
    if (temp != null && temp.capacity() >= expectedRecordSizeHint) {
      threadLocalBuffer.set(null);
      return temp.position(0)
        .limit(0);
    }
    else {
      final int defaultCapacity = allocationStrategy.defaultCapacity();
      final int capacity = Math.max(defaultCapacity, expectedRecordSizeHint);
      final ByteBuffer buffer = ByteBuffer.allocate(capacity);
      if (pagedStorage.isNativeBytesOrder()) {
        buffer.order(ByteOrder.nativeOrder());
      }
      return buffer;
    }
  }

  private void releaseTemporaryBuffer(final @NotNull ByteBuffer temp) {
    final int defaultCapacity = allocationStrategy.defaultCapacity();
    //avoid keeping too big buffers from GC:
    if (temp.capacity() <= 2 * defaultCapacity) {
      threadLocalBuffer.set(temp);
    }
  }


  private static int readRecordCapacity(final @NotNull ByteBuffer pageBuffer,
                                        final int offsetOnPage) {
    return Short.toUnsignedInt(pageBuffer.getShort(offsetOnPage + RECORD_OFFSET_CAPACITY));
  }

  private static int readRecordRedirectToId(final @NotNull ByteBuffer pageBuffer,
                                            final int offsetOnPage) {
    return pageBuffer.getInt(offsetOnPage + RECORD_OFFSET_REDIRECT_TO);
  }

  private static int readRecordLength(final @NotNull ByteBuffer pageBuffer,
                                      final int offsetOnPage) {
    return Short.toUnsignedInt(pageBuffer.getShort(offsetOnPage + RECORD_OFFSET_ACTUAL_LENGTH));
  }

  private static void putRecordPayload(final @NotNull ByteBuffer pageBuffer,
                                       final int offsetOnPage,
                                       final @NotNull ByteBuffer recordData,
                                       final int newRecordLength) {
    pageBuffer.put(offsetOnPage + RECORD_HEADER_SIZE, recordData, 0, newRecordLength);
  }


  private static ByteBuffer putRecordCapacity(final @NotNull ByteBuffer pageBuffer,
                                              final int offsetOnPage,
                                              final int recordCapacity) {
    checkCapacity(recordCapacity);
    return pageBuffer.putShort(offsetOnPage + RECORD_OFFSET_CAPACITY, (short)recordCapacity);
  }

  private static ByteBuffer putRecordLength(final @NotNull ByteBuffer pageBuffer,
                                            final int offsetOnPage,
                                            final int recordLength) {
    checkLength(recordLength);
    return pageBuffer.putShort(offsetOnPage + RECORD_OFFSET_ACTUAL_LENGTH, (short)recordLength);
  }

  private static ByteBuffer putRecordLengthMark(final @NotNull ByteBuffer pageBuffer,
                                                final int offsetOnPage,
                                                final int mark) {
    if (mark < DELETED_RECORD_MARK) {
      throw new AssertionError(
        "Code bug: mark(=" +
        mark +
        ") is incorrect, must be one of [" +
        DELETED_RECORD_MARK +
        ", " +
        MOVED_RECORD_MARK +
        ", " +
        PADDING_RECORD_MARK +
        "]");
    }
    return pageBuffer.putShort(offsetOnPage + RECORD_OFFSET_ACTUAL_LENGTH, (short)mark);
  }

  private static ByteBuffer putRecordRedirectTo(final @NotNull ByteBuffer pageBuffer,
                                                final int offsetOnPage,
                                                final int redirectToId) {
    return pageBuffer.putInt(offsetOnPage + RECORD_OFFSET_REDIRECT_TO, redirectToId);
  }

  private static int roundSizeUpToBucket(final int offset,
                                         final int pageSize,
                                         final int rawRecordSize) {
    int recordSizeRoundedUp = rawRecordSize;
    if (recordSizeRoundedUp % OFFSET_BUCKET != 0) {
      recordSizeRoundedUp = ((recordSizeRoundedUp / OFFSET_BUCKET + 1) * OFFSET_BUCKET);
    }
    final int occupiedOnPage = offset + recordSizeRoundedUp;
    final int remainedOnPage = pageSize - occupiedOnPage;
    if (0 < remainedOnPage && remainedOnPage < RECORD_HEADER_SIZE) {
      //we can't squeeze even the smallest record into remaining space, so just merge it into current record
      recordSizeRoundedUp += remainedOnPage;
    }
    assert recordSizeRoundedUp >= rawRecordSize
      : "roundedUpRecordSize(=" + recordSizeRoundedUp + ") must be >= rawRecordSize(=" + rawRecordSize + ")";
    return recordSizeRoundedUp;
  }


  private long idToOffset(final int recordId) {
    // recordId=0 is used as NULL_ID (i.e. invalid) id, hence '-1'
    return recordsStartOffset() + (recordId - 1) * (long)OFFSET_BUCKET;
  }

  private int offsetToId(final long offset) {
    // recordId=0 is used as NULL_ID (i.e. invalid) id, hence '+1' for the 1st record to have {id:1}
    final long longId = (offset - recordsStartOffset()) / OFFSET_BUCKET + 1;
    final int id = (int)longId;

    assert longId == id : "offset " + offset + " is out of Integer bounds";
    assert id > 0 : "id " + id + " is not a valid id";

    return id;
  }

  private void checkRecordIdExists(final int recordId) {
    checkRecordIdValid(recordId);
    if (!isRecordIdAllocated(recordId)) {
      throw new IllegalArgumentException("recordId(" + recordId + ") is not yet allocated: allocated ids are all < " + nextRecordId);
    }
  }

  /**
   * Method returns true if record with id=recordId is already allocated.
   * It doesn't mean the record is fully written, though -- we could be in the middle of record write.
   */
  private boolean isRecordIdAllocated(final int recordId) {
    return recordId < nextRecordId;
  }

  private static void checkRecordIdValid(final int recordId) {
    if (!isValidRecordId(recordId)) {
      throw new IllegalArgumentException("recordId(" + recordId + ") is invalid: must be > 0");
    }
  }

  private static boolean isRecordDeleted(final int recordActualLength) {
    return recordActualLength == DELETED_RECORD_MARK;
  }

  private static boolean isValidRecordId(final int recordId) {
    return recordId > NULL_ID;
  }


  private static void checkCapacity(final int capacity) {
    if (!isCorrectCapacity(capacity)) {
      throw new IllegalArgumentException("capacity(=" + capacity + ") must be in [0, " + MAX_CAPACITY + "]");
    }
  }

  private static void checkLength(final int length) {
    if (!isCorrectLength(length)) {
      throw new IllegalArgumentException("length(=" + length + ") must be in [0, " + MAX_LENGTH + "]");
    }
  }

  private static void checkLength(final int length,
                                  final String messageToFormatWithLength) {
    if (!isCorrectLength(length)) {
      throw new IllegalArgumentException(messageToFormatWithLength.formatted(length));
    }
  }

  private static boolean isCorrectCapacity(final int capacity) {
    return 0 <= capacity && capacity <= MAX_CAPACITY;
  }

  private static boolean isCorrectLength(final int length) {
    return 0 <= length && length <= MAX_LENGTH;
  }

  private static boolean isCorrectLengthFieldValue(final int lengthFieldValue) {
    return 0 <= lengthFieldValue && lengthFieldValue <= MAX_CAPACITY;
  }


  @NotNull
  private BatchCallback setupReportingToOpenTelemetry(final Path fileName) {
    final Meter meter = TraceManager.INSTANCE.getMeter("storage");

    final var recordsAllocated = meter.counterBuilder("StreamlinedBlobStorage.recordsAllocated").buildObserver();
    final var recordsRelocated = meter.counterBuilder("StreamlinedBlobStorage.recordsRelocated").buildObserver();
    final var recordsDeleted = meter.counterBuilder("StreamlinedBlobStorage.recordsDeleted").buildObserver();
    final var totalLiveRecordsPayloadBytes =
      meter.upDownCounterBuilder("StreamlinedBlobStorage.totalLiveRecordsPayloadBytes").buildObserver();
    final var totalLiveRecordsCapacityBytes =
      meter.upDownCounterBuilder("StreamlinedBlobStorage.totalLiveRecordsCapacityBytes").buildObserver();
    final Attributes attributes = Attributes.builder()
      .put("file", fileName.toString())
      .build();
    return meter.batchCallback(
      () -> {
        recordsAllocated.record(this.recordsAllocated.get(), attributes);
        recordsRelocated.record(this.recordsRelocated.get(), attributes);
        recordsDeleted.record(this.recordsDeleted.get(), attributes);
        totalLiveRecordsPayloadBytes.record(this.totalLiveRecordsPayloadBytes.get(), attributes);
        totalLiveRecordsCapacityBytes.record(this.totalLiveRecordsCapacityBytes.get(), attributes);
      },
      recordsAllocated, recordsRelocated, recordsDeleted,
      totalLiveRecordsPayloadBytes, totalLiveRecordsCapacityBytes
    );
  }
}
