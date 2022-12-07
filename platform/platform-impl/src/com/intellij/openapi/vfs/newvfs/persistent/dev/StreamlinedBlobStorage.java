// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.diagnostic.telemetry.TraceManager;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IntRef;
import com.intellij.util.io.DirectBufferWrapper;
import com.intellij.util.io.PagedFileStorage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * Store blobs, like {@link com.intellij.util.io.storage.AbstractStorage}, but tries to be faster:
 * remove intermediate mapping (id -> offset,length), and directly use offset as recordId. Also provides
 * read/write methods direct access to underlying ByteBuffers, to reduce memcopy-ing overhead.
 * <br/>
 * <br/>
 * Storage is designed for performance, hence API is quite low-level, and needs care to be used correctly.
 * I've tried to hide implementation details AMAP, but some of them are visible through API anyway, because hiding them (seems to)
 * will cost performance.
 * <br/>
 * <br/>
 * Not thread safe: requires external synchronization if used from multiple threads
 */
public class StreamlinedBlobStorage implements Closeable, AutoCloseable, Forceable {
  private static final Logger LOG = Logger.getInstance(StreamlinedBlobStorage.class);

  public static final int NULL_ID = 0;

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
  //  4. records are always allocated on a single page: single record never breaks a page boundary: if
  //     a record doesn't fit current page, it is moved to another page (remaining space on page is filled
  //     by placeholder record, if needed).

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
  //       on the fact that all references to the old location is already re-linked -- but storage can't guarantee that,
  //       there should be some external agent responsible for that.
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
  private final PagedFileStorage pagedStorage;
  /**
   * To avoid write file status to already closed storage
   */
  private boolean closed = false;

  @NotNull
  private final SpaceAllocationStrategy allocationStrategy;

  private int nextRecordId;

  private final ThreadLocal<ByteBuffer> threadLocalBuffer;

  //==== monitoring fields:
  // They are frequently accessed, read/write them each time into a file header is too expensive (and verbose),
  // hence use caching fields instead:

  private volatile int recordsAllocated;
  private volatile int recordsRelocated;
  private volatile int recordsDeleted;
  private volatile long totalLiveRecordsPayloadBytes;
  private volatile long totalLiveRecordsCapacityBytes;
  private final BatchCallback openTelemetryCallback;


  public StreamlinedBlobStorage(final @NotNull PagedFileStorage pagedStorage,
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

    pagedStorage.lockWrite();
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
          throw new IOException("Can't read file[" + pagedStorage + "]: too big, " + length + " > Integer.MAX_VALUE * " + OFFSET_BUCKET);
        }
        nextRecordId = offsetToId(length);

        recordsAllocated = readHeaderInt(HEADER_OFFSET_RECORDS_ALLOCATED);
        recordsRelocated = readHeaderInt(HEADER_OFFSET_RECORDS_RELOCATED);
        recordsDeleted = readHeaderInt(HEADER_OFFSET_RECORDS_DELETED);
        totalLiveRecordsPayloadBytes = readHeaderLong(HEADER_OFFSET_RECORDS_LIVE_TOTAL_PAYLOAD_SIZE);
        totalLiveRecordsCapacityBytes = readHeaderLong(HEADER_OFFSET_RECORDS_LIVE_TOTAL_CAPACITY_SIZE);
      }
      else {
        nextRecordId = offsetToId(recordsStartOffset());
      }
      putHeaderInt(HEADER_OFFSET_STORAGE_VERSION, STORAGE_VERSION_CURRENT);
      putHeaderInt(HEADER_OFFSET_FILE_STATUS, FILE_STATUS_OPENED);
    }
    finally {
      pagedStorage.unlockWrite();
    }

    openTelemetryCallback = setupReportingToOpenTelemetry(pagedStorage.getFile().getFileName());
  }

  public int getStorageVersion() throws IOException {
    pagedStorage.lockRead();
    try {
      return readHeaderInt(HEADER_OFFSET_STORAGE_VERSION);
    }
    finally {
      pagedStorage.unlockRead();
    }
  }

  public int getDataFormatVersion() throws IOException {
    pagedStorage.lockRead();
    try {
      return readHeaderInt(HEADER_OFFSET_DATA_FORMAT_VERSION);
    }
    finally {
      pagedStorage.unlockRead();
    }
  }

  public void setDataFormatVersion(final int expectedVersion) throws IOException {
    pagedStorage.lockWrite();
    try {
      putHeaderInt(HEADER_OFFSET_DATA_FORMAT_VERSION, expectedVersion);
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }


  public <Out> Out readRecord(final int recordId,
                              final @NotNull Reader<Out> reader) throws IOException {
    return readRecord(recordId, reader, null);
  }

  public boolean hasRecord(final int recordId) throws IOException {
    return hasRecord(recordId, null);
  }

  public boolean hasRecord(final int recordId,
                           final @Nullable IntRef redirectToIdRef) throws IOException {
    if (recordId == NULL_ID) {
      return false;
    }
    pagedStorage.lockRead();
    try {
      checkRecordIdValid(recordId);
      if (!isRecordIdAllocated(recordId)) {
        return false;
      }

      final long recordOffset = idToOffset(recordId);
      final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
      final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, false);
      try {
        final ByteBuffer buffer = page.getBuffer();
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
        page.unlock();
      }
    }
    finally {
      pagedStorage.unlockRead();
    }
  }

  //TODO RC: consider change way of dealing with ByteBuffers: what-if all methods will have same semantics,
  //         i.e. buffer contains payload[0..limit]? I.e. all methods passing buffers in such a state, and
  //         all methods returned buffers in such a state?

  /**
   * reader will be called with read-only ByteBuffer set up for reading the record content (payload):
   * i.e. position=0, limit=payload.length. Reader is free to do whatever it likes with the buffer.
   *
   * @param redirectToIdRef if not-null length>=1 array, will contain actual recordId of the record,
   *                        which could be different from recordId passed in if record was moved (e.g.
   *                        re-allocated in a new place) and recordId used to call the method is now
   *                        outdated. Clients could still use old recordId, but better to replace
   *                        this outdated id with actual one, since it improves performance (at least)
   */
  public <Out> Out readRecord(final int recordId,
                              final @NotNull Reader<Out> reader,
                              final @Nullable IntRef redirectToIdRef) throws IOException {
    pagedStorage.lockRead();
    try {
      checkRecordIdExists(recordId);
      final long recordOffset = idToOffset(recordId);
      final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
      final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, false);
      try {
        final ByteBuffer buffer = page.getBuffer();
        final int recordCapacity = readRecordCapacity(buffer, offsetOnPage);
        final int recordActualLength = readRecordLength(buffer, offsetOnPage);
        final int recordRedirectedToId = readRecordRedirectToId(buffer, offsetOnPage);

        if (redirectToIdRef != null) {
          redirectToIdRef.set(recordId);
        }
        if (!isRecordActual(recordActualLength)) {
          if (!isValidRecordId(recordRedirectedToId)) {
            throw new IOException("Record[" + recordId + "] is deleted");
          }
          //MAYBE RC: try to avoid recursion here, since we lock >1 pages while really only need to lock 1
          return readRecord(recordRedirectedToId, reader, redirectToIdRef);
        }

        final ByteBuffer slice = buffer.slice(offsetOnPage + RECORD_HEADER_SIZE, recordActualLength)
          .asReadOnlyBuffer()
          .order(buffer.order());
        return reader.read(slice);
      }
      finally {
        page.unlock();
      }
    }
    finally {
      pagedStorage.unlockRead();
    }
  }

  public int writeToRecord(final int recordId,
                           final @NotNull Writer writer) throws IOException {
    return writeToRecord(recordId, writer, /*expectedRecordSizeHint: */ -1);
  }

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
        return allocateRecord(bufferWithData, recordCapacity);
      }
      finally {
        releaseTemporaryBuffer(temp);
      }
    }

    //already existent record
    pagedStorage.lockWrite();
    try {
      final long recordOffset = idToOffset(recordId);
      final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
      final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, true);
      try {
        final ByteBuffer buffer = page.getBuffer();
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
          //writer decides to skip write -> just return current recordId
          return recordId;
        }

        if (newRecordContent != recordContent) {
          newRecordContent.flip();
          final int newRecordLength = newRecordContent.remaining();
          if (newRecordLength <= recordCapacity) {
            //RC: really, in this case writer should just write data right in the 'recordContent' buffer,
            //    not allocate the new buffer -- but ok, we could deal with it:
            putRecordPayload(buffer, offsetOnPage, newRecordContent, newRecordLength);
            page.fileSizeMayChanged(offsetOnPage + RECORD_HEADER_SIZE + newRecordLength);

            totalLiveRecordsPayloadBytes += (newRecordLength - recordActualLength);
          }
          else {//current record is too small for new content -> relocate to a new place
            final int newRecordId = allocateRecord(newRecordContent, newRecordContent.capacity());

            //mark current record as either 'moved' or 'deleted'
            final int recordMark = leaveRedirectOnRecordRelocation ? MOVED_RECORD_MARK : DELETED_RECORD_MARK;
            putRecordLengthMark(buffer, offsetOnPage, recordMark);
            putRecordRedirectTo(buffer, offsetOnPage, newRecordId);
            page.fileSizeMayChanged(offsetOnPage + RECORD_HEADER_SIZE);

            totalLiveRecordsPayloadBytes -= recordActualLength;
            totalLiveRecordsCapacityBytes -= recordCapacity;
            if (leaveRedirectOnRecordRelocation) {
              recordsRelocated++;
            }
            else {
              recordsDeleted++;
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
          page.fileSizeMayChanged(offsetOnPage + RECORD_HEADER_SIZE + newRecordLength);

          totalLiveRecordsPayloadBytes += (newRecordLength - recordActualLength);
        }
        return recordId;
      }
      finally {
        page.markDirty();
        page.unlock();
      }
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }

  /**
   * Delete record by recordId.
   * <p>
   * Contrary to read/write methods, this method DOES NOT follow redirectTo chain: record to be deleted
   * is the record with id=recordId, redirectToId field is ignored. Why is that: because the main use
   * case of redirectTo chain is to support delayed actual record removal -- to give all clients a chance
   * to change their stored recordId to the new one, after that record was moved by some reason. But
   * after all clients have done that, the stale record should be removed (so its space could be reclaimed)
   * -- but not actual record referred with redirectTo link. If remove method follows redirectTo links
   * than how to remove stale record without affecting its actual counterpart?
   *
   * @throws IllegalStateException if record is already deleted
   */
  public void deleteRecord(final int recordId) throws IOException {
    pagedStorage.lockWrite();
    try {
      checkRecordIdExists(recordId);

      final long recordOffset = idToOffset(recordId);
      final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
      final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, true);
      try {
        final ByteBuffer buffer = page.getBuffer();
        final int recordCapacity = readRecordCapacity(buffer, offsetOnPage);
        final int recordActualLength = readRecordLength(buffer, offsetOnPage);
        final int recordRedirectedToId = readRecordRedirectToId(buffer, offsetOnPage);

        if (isRecordDeleted(recordActualLength)) {//record deleted or moved: check was it moved to a new location?
          throw new IllegalStateException("Can't delete record[" + recordId + "]: it was already deleted");
        }
        else {
          putRecordLengthMark(buffer, offsetOnPage, DELETED_RECORD_MARK);
          putRecordRedirectTo(buffer, offsetOnPage, NULL_ID);

          page.fileSizeMayChanged(offsetOnPage + RECORD_HEADER_SIZE);
          page.markDirty();

          recordsDeleted++;
          totalLiveRecordsPayloadBytes -= recordActualLength;
          totalLiveRecordsCapacityBytes -= recordCapacity;
        }
      }
      finally {
        page.unlock();
      }
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }

  //TODO int deleteAllForwarders(final int recordId) throws IOException;

  /**
   * Scan all records (even deleted one), and deliver their content to processor. ByteBuffer is read-only, and
   * prepared for reading (i.e. position=0, limit=payload.length). For deleted/moved records recordLength is negative
   * see {@link #isRecordActual(int)}.
   * Scanning stops if processor return false.
   *
   * @return how many records were processed
   */
  public <E extends Exception> int forEach(final @NotNull Processor<E> processor) throws IOException, E {
    pagedStorage.lockRead();
    try {
      final long storageLength = pagedStorage.length();
      int currentId = offsetToId(recordsStartOffset());
      for (int recordNo = 0; ; recordNo++) {
        final long recordOffset = idToOffset(currentId);
        final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, false);
        final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
        try {
          final ByteBuffer buffer = page.getBuffer();
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
          page.unlock();
        }
      }
    }
    finally {
      pagedStorage.unlockRead();
    }
  }

  public boolean isRecordActual(final int recordActualLength) {
    return 0 <= recordActualLength && recordActualLength < DELETED_RECORD_MARK;
  }

  public int maxPayloadSupported() {
    return MAX_LENGTH - RECORD_HEADER_SIZE;
  }

  public int liveRecordsCount() {
    return recordsAllocated - recordsDeleted - recordsRelocated;
  }

  public int recordsAllocated() {
    return recordsAllocated;
  }

  public int recordsRelocated() {
    return recordsRelocated;
  }

  public int recordsDeleted() {
    return recordsDeleted;
  }

  public long totalLiveRecordsPayloadBytes() {
    return totalLiveRecordsPayloadBytes;
  }

  public long totalLiveRecordsCapacityBytes() {
    return totalLiveRecordsCapacityBytes;
  }

  public long sizeInBytes() {
    return pagedStorage.length();
  }

  @Override
  public boolean isDirty() {
    return pagedStorage.isDirty();
  }

  @Override
  public void force() throws IOException {
    pagedStorage.lockWrite();
    try {
      if (!closed) {
        putHeaderInt(HEADER_OFFSET_FILE_STATUS, FILE_STATUS_SAFELY_CLOSED);

        putHeaderInt(HEADER_OFFSET_RECORDS_ALLOCATED, recordsAllocated);
        putHeaderInt(HEADER_OFFSET_RECORDS_RELOCATED, recordsRelocated);
        putHeaderInt(HEADER_OFFSET_RECORDS_DELETED, recordsDeleted);
        putHeaderLong(HEADER_OFFSET_RECORDS_LIVE_TOTAL_PAYLOAD_SIZE, totalLiveRecordsPayloadBytes);
        putHeaderLong(HEADER_OFFSET_RECORDS_LIVE_TOTAL_CAPACITY_SIZE, totalLiveRecordsCapacityBytes);

        pagedStorage.force();
      }
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }

  @Override
  public void close() throws IOException {
    pagedStorage.lockWrite();
    try {
      force();

      //MAYBE RC: generally, it should not be this class's responsibility to close pagedStorage, since not this
      // class creates it -- whoever creates it, is responsible for closing it.
      pagedStorage.close();

      //.close() methods are better to be idempotent, i.e. not throw exceptions on repeating calls, but just
      // silently ignore attempts to close already closed. And pagedStorage (seems to) conforms with that. But
      // here we try to write file status and other header fields, and without .close flag we'll try to do that
      // even on already closed pagedStorage, which leads to exception.
      closed = true;
      openTelemetryCallback.close();
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }

  @Override
  public String toString() {
    return "StreamlinedBlobStorage{" + pagedStorage + "}{nextRecordId: " + nextRecordId + '}';
  }


  public interface Reader<T> {
    T read(@NotNull final ByteBuffer data) throws IOException;
  }

  public interface Writer {
    ByteBuffer write(@NotNull final ByteBuffer data) throws IOException;
  }

  public interface Processor<E extends Exception> {
    boolean processRecord(final int recordId,
                          final int recordCapacity,
                          final int recordLength,
                          final ByteBuffer payload) throws IOException, E;
  }

  public interface SpaceAllocationStrategy {
    /**
     * @return how long buffers create for a new record (i.e. in {@link #writeToRecord(int, Writer)}
     * there recordId=NULL_ID)
     */
    int defaultCapacity();

    /**
     * @return if a writer in a {@link StreamlinedBlobStorage#writeToRecord(int, Writer)} returns buffer
     * of (length, capacity) -- how big record to allocate for the data? Buffer actual size (limit-position)
     * and buffer.capacity is considered. returned value must be >= actualLength
     */
    int capacity(final int actualLength,
                 final int currentCapacity);

    class WriterDecidesStrategy implements SpaceAllocationStrategy {
      private final int defaultCapacity;

      public WriterDecidesStrategy(final int defaultCapacity) {
        if (defaultCapacity <= 0 || defaultCapacity >= MAX_CAPACITY) {
          throw new IllegalArgumentException("defaultCapacity(" + defaultCapacity + ") must be in [1," + MAX_CAPACITY + "]");
        }
        this.defaultCapacity = defaultCapacity;
      }

      @Override
      public int defaultCapacity() {
        return defaultCapacity;
      }

      @Override
      public int capacity(final int actualLength,
                          final int currentCapacity) {
        if (actualLength < 0) {
          throw new IllegalArgumentException("actualLength(=" + actualLength + " should be >=0");
        }
        if (currentCapacity < actualLength) {
          throw new IllegalArgumentException("currentCapacity(=" + currentCapacity + ") should be >= actualLength(=" + actualLength + ")");
        }
        return currentCapacity;
      }

      @Override
      public String toString() {
        return "WriterDecidesStrategy{default: " + defaultCapacity + '}';
      }
    }

    class DataLengthPlusFixedPercentStrategy implements SpaceAllocationStrategy {
      private final int defaultCapacity;
      private final int minCapacity;
      private final int percentOnTheTop;

      public DataLengthPlusFixedPercentStrategy(final int defaultCapacity,
                                                final int minCapacity,
                                                final int percentOnTheTop) {
        if (defaultCapacity <= 0 || defaultCapacity > MAX_CAPACITY) {
          throw new IllegalArgumentException("defaultCapacity(" + defaultCapacity + ") must be in [1," + MAX_CAPACITY + "]");
        }
        if (minCapacity <= 0 || minCapacity > defaultCapacity) {
          throw new IllegalArgumentException("minCapacity(" + minCapacity + ") must be > 0 && <= defaultCapacity(" + defaultCapacity + ")");
        }
        if (percentOnTheTop < 0) {
          throw new IllegalArgumentException("percentOnTheTop(" + percentOnTheTop + ") must be >= 0");
        }
        this.defaultCapacity = defaultCapacity;
        this.minCapacity = minCapacity;
        this.percentOnTheTop = percentOnTheTop;
      }

      @Override
      public int defaultCapacity() {
        return defaultCapacity;
      }

      @Override
      public int capacity(final int actualLength,
                          final int currentCapacity) {
        if (actualLength < 0) {
          throw new IllegalArgumentException("actualLength(=" + actualLength + " should be >=0");
        }
        if (currentCapacity < actualLength) {
          throw new IllegalArgumentException("currentCapacity(=" + currentCapacity + ") should be >= actualLength(=" + actualLength + ")");
        }
        final double capacity = actualLength * (1.0 + percentOnTheTop / 100.0);
        final int advisedCapacity = (int)Math.max(minCapacity, capacity + 1);
        if (advisedCapacity < 0 || advisedCapacity > MAX_CAPACITY) {
          return MAX_CAPACITY;
        }
        return advisedCapacity;
      }

      @Override
      public String toString() {
        return "DataLengthPlusFixedPercentStrategy{" +
               "length + " + percentOnTheTop + "%" +
               ", min: " + minCapacity +
               ", default: " + defaultCapacity + "}";
      }
    }
  }

  /* ============================= implementation ================================================================================= */

  /**
   * content buffer is passed in 'ready for write' state: position=0, limit=[#last byte of payload]
   */
  private int allocateRecord(final ByteBuffer content,
                             final int newRecordCapacity) throws IOException {
    final int pageSize = pagedStorage.getPageSize();
    final int totalRecordSize = RECORD_HEADER_SIZE + newRecordCapacity;
    if (totalRecordSize > pageSize) {
      throw new IllegalArgumentException(
        "record size(header:" + RECORD_HEADER_SIZE + " +capacity:" + newRecordCapacity + ") should be <= pageSize(=" + pageSize + ")");
    }

    pagedStorage.lockWrite();
    try {
      final int newRecordId = nextRecordId;
      final long recordOffset = idToOffset(newRecordId);

      //we don't want record to be broken by page boundary, so if current record steps out of current page
      // -> we move entire record to the next page, and pad the space remaining on the current page with
      // filler record:
      final long startPage = recordOffset / pageSize;
      final long endPage = (recordOffset + totalRecordSize - 1) / pageSize;
      if (startPage != endPage) {
        //insert a space-filler record to occupy space till the end of page:
        putSpaceFillerRecord(recordOffset, pageSize);
        //MAYBE RC: even better would be to merge that space into the last record on the page (as we do with
        //         small bytes in the next branch), but unfortunately here we don't have previous record pointer,
        //         so can't find record header to adjust capacity. This problem could be solved, but for now I skip

        //...move pointer to the next page, and re-try allocate record:
        final long nextPageStartOffset = (startPage + 1) * pageSize;
        nextRecordId = offsetToId(nextPageStartOffset);
        assert idToOffset(nextRecordId) == nextPageStartOffset : "idToOffset(" + nextRecordId + ")=" + idToOffset(nextRecordId) +
                                                                 " != nextPageStartOffset(" + nextPageStartOffset + ")";

        return allocateRecord(content, newRecordCapacity);
      }

      final int newRecordLength = content.remaining();
      final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
      final int recordCapacityRoundedUp = roundCapacityUpToBucket(offsetOnPage, pageSize, newRecordCapacity);

      final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, true);
      final ByteBuffer buffer = page.getBuffer();
      try {
        //check everything before write anything:
        checkCapacity(recordCapacityRoundedUp);
        checkLength(newRecordLength);

        putRecordCapacity(buffer, offsetOnPage, recordCapacityRoundedUp);
        putRecordLength(buffer, offsetOnPage, newRecordLength);
        putRecordRedirectTo(buffer, offsetOnPage, NULL_ID);
        putRecordPayload(buffer, offsetOnPage, content, newRecordLength);

        page.fileSizeMayChanged(offsetOnPage + RECORD_HEADER_SIZE + recordCapacityRoundedUp);
        page.markDirty();
      }
      finally {
        page.unlock();
      }

      recordsAllocated++;
      totalLiveRecordsCapacityBytes += recordCapacityRoundedUp;
      totalLiveRecordsPayloadBytes += newRecordLength;

      nextRecordId = offsetToId(nextRecordOffset(recordOffset, recordCapacityRoundedUp));
      return newRecordId;
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }

  private void putSpaceFillerRecord(final long recordOffset,
                                    final int pageSize) throws IOException {
    final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, true);
    final ByteBuffer buffer = page.getBuffer();
    try {
      final int offsetInPage = pagedStorage.getOffsetInPage(recordOffset);
      final int remainingOnPage = pageSize - offsetInPage;
      if (remainingOnPage >= RECORD_HEADER_SIZE) {
        putRecordCapacity(buffer, offsetInPage, (remainingOnPage - RECORD_HEADER_SIZE));
        putRecordLengthMark(buffer, offsetInPage, PADDING_RECORD_MARK);
        putRecordRedirectTo(buffer, offsetInPage, NULL_ID);

        page.fileSizeMayChanged(pageSize - 1);
        page.markDirty();
      }
      //if remainingOnPage < RECORD_HEADER_SIZE we can't put placeholder record, hence leave it as-is,
      //   and process this case in a nextRecordOffset()
    }
    finally {
      page.unlock();
    }
  }

  private long nextRecordOffset(final long recordOffset,
                                final int recordCapacity) {
    final long nextOffset = recordOffset + RECORD_HEADER_SIZE + recordCapacity;

    final int offsetOnPage = pagedStorage.getOffsetInPage(nextOffset);
    final int pageSize = pagedStorage.getPageSize();
    if (pageSize - offsetOnPage < RECORD_HEADER_SIZE) {
      //Previously this was fixed here, by moving offset to the next page:
      //  nextOffset = (nextOffset / pageSize + 1) * pageSize;
      //But now instead of fix it here I adjust new record allocation code (allocateRecord), so for records
      // on the end of page its capacity is slightly increased to consume that small unusable bytes on the
      // edge of the page -- this way those bytes are utilized. But that means this branch should be unreachable now
      throw new AssertionError("Code bug: offsetOnPage(" + offsetOnPage + ") is too close to page border (" + pageSize + ")");
    }
    return nextOffset;
  }


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


  private long recordsStartOffset() {
    final long headerSize = headerSize();
    if (headerSize % OFFSET_BUCKET > 0) {
      return (headerSize / OFFSET_BUCKET + 1) * OFFSET_BUCKET;
    }
    else {
      return (headerSize / OFFSET_BUCKET) * OFFSET_BUCKET;
    }
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


  private static int readRecordCapacity(final ByteBuffer pageBuffer,
                                        final int offsetOnPage) {
    return Short.toUnsignedInt(pageBuffer.getShort(offsetOnPage + RECORD_OFFSET_CAPACITY));
  }

  private static int readRecordRedirectToId(final ByteBuffer pageBuffer,
                                            final int offsetOnPage) {
    return pageBuffer.getInt(offsetOnPage + RECORD_OFFSET_REDIRECT_TO);
  }

  private static int readRecordLength(final ByteBuffer pageBuffer,
                                      final int offsetOnPage) {
    return Short.toUnsignedInt(pageBuffer.getShort(offsetOnPage + RECORD_OFFSET_ACTUAL_LENGTH));
  }

  private static void putRecordPayload(final ByteBuffer pageBuffer,
                                       final int offsetOnPage,
                                       final ByteBuffer recordData,
                                       final int newRecordLength) {
    pageBuffer.put(offsetOnPage + RECORD_HEADER_SIZE, recordData, 0, newRecordLength);
  }

  @NotNull
  private static ByteBuffer putRecordCapacity(final ByteBuffer pageBuffer,
                                              final int offsetOnPage,
                                              final int recordCapacity) {
    checkCapacity(recordCapacity);
    return pageBuffer.putShort(offsetOnPage + RECORD_OFFSET_CAPACITY, (short)recordCapacity);
  }

  @NotNull
  private static ByteBuffer putRecordLength(final ByteBuffer pageBuffer,
                                            final int offsetOnPage,
                                            final int recordLength) {
    checkLength(recordLength);
    return pageBuffer.putShort(offsetOnPage + RECORD_OFFSET_ACTUAL_LENGTH, (short)recordLength);
  }

  @NotNull
  private static ByteBuffer putRecordLengthMark(final ByteBuffer pageBuffer,
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

  @NotNull
  private static ByteBuffer putRecordRedirectTo(final ByteBuffer pageBuffer,
                                                final int offsetOnPage,
                                                final int redirectToId) {
    return pageBuffer.putInt(offsetOnPage + RECORD_OFFSET_REDIRECT_TO, redirectToId);
  }

  private static int roundCapacityUpToBucket(final int offset,
                                             final int pageSize,
                                             final int rawCapacity) {
    int capacityRoundedUp = rawCapacity;
    if (capacityRoundedUp % OFFSET_BUCKET != 0) {
      capacityRoundedUp = ((capacityRoundedUp / OFFSET_BUCKET + 1) * OFFSET_BUCKET);
    }
    final int occupiedOnPage = offset + RECORD_HEADER_SIZE + capacityRoundedUp;
    final int remainedOnPage = pageSize - occupiedOnPage;
    if (0 < remainedOnPage && remainedOnPage < RECORD_HEADER_SIZE) {
      //we can't squeeze even the smallest record into remaining space, so just merge it into current record
      capacityRoundedUp += remainedOnPage;
    }
    assert capacityRoundedUp >= rawCapacity : capacityRoundedUp + "<=" + rawCapacity;
    return capacityRoundedUp;
  }


  private long idToOffset(final int recordId) {
    // recordId=0 is used as NULL_ID (i.e. invalid) id, hence '-1'
    return recordsStartOffset() + (recordId - 1) * (long)OFFSET_BUCKET;
  }

  private int offsetToId(final long offset) {
    // recordId=0 is used as NULL_ID (i.e. invalid) id, hence '+1'
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
    final var totalLiveRecordsPayloadBytes = meter.upDownCounterBuilder("StreamlinedBlobStorage.totalLiveRecordsPayloadBytes").buildObserver();
    final var totalLiveRecordsCapacityBytes = meter.upDownCounterBuilder("StreamlinedBlobStorage.totalLiveRecordsCapacityBytes").buildObserver();
    final Attributes attributes = Attributes.builder()
      .put("file", fileName.toString())
      .build();
    return meter.batchCallback(
      () -> {
        recordsAllocated.record(this.recordsAllocated, attributes);
        recordsRelocated.record(this.recordsRelocated, attributes);
        recordsDeleted.record(this.recordsDeleted, attributes);
        totalLiveRecordsPayloadBytes.record(this.totalLiveRecordsPayloadBytes, attributes);
        totalLiveRecordsCapacityBytes.record(this.totalLiveRecordsCapacityBytes, attributes);
      },
      recordsAllocated, recordsRelocated, recordsDeleted,
      totalLiveRecordsPayloadBytes, totalLiveRecordsCapacityBytes
    );
  }
}
