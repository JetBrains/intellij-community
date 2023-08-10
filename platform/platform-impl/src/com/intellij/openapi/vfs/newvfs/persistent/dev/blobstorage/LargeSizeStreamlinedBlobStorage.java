// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.LargeBlobStorageRecordLayout.ActualRecords.LargeRecord;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.DirectBufferWrapper;
import com.intellij.util.io.PagedFileStorage;
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

import static com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.LargeBlobStorageRecordLayout.ActualRecords.recordLayoutForType;
import static com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.LargeBlobStorageRecordLayout.ActualRecords.recordSizeTypeByCapacity;
import static com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.LargeBlobStorageRecordLayout.OFFSET_BUCKET;
import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.Storage;


/**
 * Backport of {@link StreamlinedBlobStorageOverLockFreePagesStorage} to on the top of {@link PagedFileStorage}
 * <p/>
 * Implements {@link StreamlinedBlobStorage} blobs over {@link PagedFileStorage} storage.
 * Implementation is thread-safe (protected by {@link com.intellij.util.io.StorageLockContext} locks)
 * <p/>
 * Storage is optimized to store small records (~tens bytes) -- it tries to compress record headers
 * so smaller records have just 2 bytes of overhead because of header. At the same time storage allows
 * record size up to 1Mb large.
 * <p>
 */
public class LargeSizeStreamlinedBlobStorage implements StreamlinedBlobStorage {
  private static final Logger LOG = Logger.getInstance(LargeSizeStreamlinedBlobStorage.class);

  /* ======== Persistent format: =================================================================== */

  // Persistent format: (header) (records)*
  //  header: storageVersion[int32], safeCloseMagic[int32] ...monitoring fields... dataFormatVersion[int32]
  //  record:
  //          recordHeader: recordType[int8], capacity, length?, redirectTo?, recordData[length]?
  //                        First byte of header contains the record type, which defines other header
  //                        fields & their length. A lot of bits wiggling are used to compress header
  //                        into as few bytes as possible -- see LargeBlobStorageRecordLayout for details.
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

  public static final int STORAGE_VERSION_CURRENT = 2;


  private static final int FILE_STATUS_OPENED = 0;
  private static final int FILE_STATUS_SAFELY_CLOSED = 1;
  //TODO RC: how to implement this?
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


  /**
   * Different record types support different capacities, even larger than this one. But most records
   * start as 'ACTUAL' record type, hence actual LargeRecord capacity is used as 'common denominator'
   * here.
   */
  public static final int MAX_CAPACITY = LargeRecord.MAX_CAPACITY;

  /**
   * Max length of .redirectTo chain.
   * If a chain is longer, it is considered a bug (cyclic reference, or alike) and IOException is thrown.
   */
  public static final int MAX_REDIRECTS = 1024;


  /* ============== instance fields: ====================================================================== */


  @NotNull
  private final PagedFileStorage pagedStorage;

  /** To avoid write file header to already closed storage */
  private final AtomicBoolean closed = new AtomicBoolean(false);

  @NotNull
  private final SpaceAllocationStrategy allocationStrategy;

  /**
   * Since records are page-aligned, record (with header) can't be larger than pageSize.
   * This is max record payload capacity (i.e. not including headers) for a current pageSize.
   * ({@link #MAX_CAPACITY} is a max capacity implementation supports -- regardless of page size)
   */
  private final int maxCapacityForPageSize;


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


  public LargeSizeStreamlinedBlobStorage(final @NotNull PagedFileStorage pagedStorage,
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

    maxCapacityForPageSize = pageSize - LargeRecord.INSTANCE.headerSize();
    if (maxCapacityForPageSize <= 0) {
      throw new IllegalArgumentException(
        "pageSize(=" + pageSize + ") is too small even for a record header(=" + LargeRecord.INSTANCE.headerSize() + "b)"
      );
    }

    synchronized (this) {//protect nextRecordId modifications:
      pagedStorage.lockWrite();
      try {
        final DirectBufferWrapper headerPage = pagedStorage.getByteBuffer(0, /*forWrite: */ true);
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

            putHeaderInt(HEADER_OFFSET_STORAGE_VERSION, STORAGE_VERSION_CURRENT);
            putHeaderInt(HEADER_OFFSET_FILE_STATUS, FILE_STATUS_OPENED);
            headerPage.fileSizeMayChanged(HEADER_SIZE);
            headerPage.markDirty();
          }
        }
        finally {
          headerPage.unlock();
        }
      }
      finally {
        pagedStorage.unlockWrite();
      }
    }

    openTelemetryCallback = setupReportingToOpenTelemetry(pagedStorage.getFile().getFileName());
  }

  @Override
  public int getStorageVersion() throws IOException {
    pagedStorage.lockRead();
    try {
      return readHeaderInt(HEADER_OFFSET_STORAGE_VERSION);
    }
    finally {
      pagedStorage.unlockRead();
    }
  }

  @Override
  public int getDataFormatVersion() throws IOException {
    pagedStorage.lockRead();
    try {
      return readHeaderInt(HEADER_OFFSET_DATA_FORMAT_VERSION);
    }
    finally {
      pagedStorage.unlockRead();
    }
  }

  @Override
  public void setDataFormatVersion(final int expectedVersion) throws IOException {
    pagedStorage.lockWrite();
    try {
      putHeaderInt(HEADER_OFFSET_DATA_FORMAT_VERSION, expectedVersion);
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }


  @Override
  public <Out> Out readRecord(final int recordId,
                              final @NotNull ByteBufferReader<Out> reader) throws IOException {
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
    int currentRecordId = recordId;
    for (int i = 0; i < MAX_REDIRECTS; i++) {
      final long recordOffset = idToOffset(currentRecordId);
      pagedStorage.lockRead();
      try {
        final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, /*forWrite: */ false);
        try {
          final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
          final ByteBuffer buffer = page.getBuffer();
          final LargeBlobStorageRecordLayout recordLayout = LargeBlobStorageRecordLayout.recordLayout(buffer, offsetOnPage);
          final byte recordType = recordLayout.recordType();

          if (redirectToIdRef != null) {
            redirectToIdRef.set(currentRecordId);
          }

          if (recordType == LargeBlobStorageRecordLayout.RECORD_TYPE_ACTUAL) {
            return true;
          }

          if (recordType == LargeBlobStorageRecordLayout.RECORD_TYPE_MOVED) {
            final int redirectToId = recordLayout.redirectToId(buffer, offsetOnPage);
            if (redirectToId == NULL_ID) {
              return false;
            }
            currentRecordId = redirectToId;
          }
          else {
            throw new AssertionError("RecordType(" + recordType + ") should not appear in the chain: " +
                                     "it is either not implemented yet, or all wrong");
          }
        }
        finally {
          page.unlock();
        }
      }
      finally {
        pagedStorage.unlockRead();
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
  public <Out> Out readRecord(final int recordId,
                              final @NotNull ByteBufferReader<Out> reader,
                              final @Nullable IntRef redirectToIdRef) throws IOException {
    checkRecordIdExists(recordId);
    int currentRecordId = recordId;
    for (int i = 0; i < MAX_REDIRECTS; i++) {
      final long recordOffset = idToOffset(currentRecordId);
      pagedStorage.lockRead();
      try {
        final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
        final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, /*forWrite: */ false);
        try {
          final ByteBuffer buffer = page.getBuffer();
          final LargeBlobStorageRecordLayout recordLayout = LargeBlobStorageRecordLayout.recordLayout(buffer, offsetOnPage);
          final byte recordType = recordLayout.recordType();

          if (redirectToIdRef != null) {
            redirectToIdRef.set(currentRecordId); //will be overwritten if we follow .redirectedToId chain
          }

          if (recordType == LargeBlobStorageRecordLayout.RECORD_TYPE_ACTUAL) {
            final int recordPayloadLength = recordLayout.length(buffer, offsetOnPage);
            final ByteBuffer slice = buffer.slice(offsetOnPage + recordLayout.headerSize(), recordPayloadLength)
              .asReadOnlyBuffer()
              .order(buffer.order());
            return reader.read(slice);
          }

          if (recordType == LargeBlobStorageRecordLayout.RECORD_TYPE_MOVED) {
            final int redirectToId = recordLayout.redirectToId(buffer, offsetOnPage);
            if (redirectToId == NULL_ID) { //!actual && redirectTo = NULL
              throw new RecordAlreadyDeletedException("Can't read record[" + currentRecordId + "]: it was deleted");
            }
            currentRecordId = redirectToId;
          }
          else {
            throw new AssertionError("RecordType(" + recordType + ") should not appear in the chain: " +
                                     "it is either not implemented yet, or all wrong");
          }
        }
        finally {
          page.unlock();
        }
      }
      finally {
        pagedStorage.unlockRead();
      }
    }
    throw new IOException("record[" + recordId + "].redirectTo chain is too long (>=" + MAX_REDIRECTS + "): circular reference?");
  }

  @Override
  public int writeToRecord(final int recordId,
                           final @NotNull ByteBufferWriter writer) throws IOException {
    return writeToRecord(recordId, writer, /*expectedRecordSizeHint: */ -1);
  }

  @Override
  public int writeToRecord(final int recordId,
                           final @NotNull ByteBufferWriter writer,
                           final int expectedRecordSizeHint) throws IOException {
    return writeToRecord(recordId, writer, expectedRecordSizeHint, /* leaveRedirectOnRecordRelocation: */ false);
  }

  /**
   * Writer is called with writeable ByteBuffer represented current record content (payload).
   * Buffer is prepared for read: position=0, limit=payload.length, capacity=[current record capacity].
   * <br> <br>
   * Writer is free to read and/or modify the buffer, and return it in an 'after puts' state, i.e.
   * position=[#last byte of payload], new payload content = buffer[0..position].
   * <br> <br>
   * NOTE: this implies that even if the writer writes nothing, only reads -- it is still required to
   * set buffer.position=limit, because otherwise storage will treat the buffer state as if record
   * should be set length=0. This is a bit unnatural, so there is a shortcut: if the writer changes
   * nothing, it could just return null.
   * <br> <br>
   * Capacity: if new payload fits into buffer passed in -> it could be written right into it. If new
   * payload requires more space, writer should allocate its own buffer with enough capacity, write
   * new payload into it, and return that buffer (in an 'after puts' state), instead of buffer passed
   * in. Storage will re-allocate space for the record with capacity >= returned buffer capacity.
   *
   * @param expectedRecordSizeHint          hint to a storage about how big data writer intend to write. May be used for allocating buffer
   *                                        of that size. <=0 means 'no hints, use default buffer allocation strategy'
   * @param leaveRedirectOnRecordRelocation if current record is relocated during writing, old record could be either removed right now,
   *                                        or remain as 'redirect-to' record, so new content could still be accesses by old recordId.
   */
  @Override
  public int writeToRecord(final int recordId,
                           final @NotNull ByteBufferWriter writer,
                           final int expectedRecordSizeHint,
                           final boolean leaveRedirectOnRecordRelocation) throws IOException {
    //insert new record?
    if (!isValidRecordId(recordId)) {
      final ByteBuffer temp = acquireTemporaryBuffer(expectedRecordSizeHint);
      try {
        final ByteBuffer bufferWithData = writer.write(temp);
        bufferWithData.flip();

        final int recordLength = bufferWithData.limit();
        checkLengthHardLimit(recordLength);
        if (recordLength > maxCapacityForPageSize) {
          throw new IllegalStateException(
            "recordLength(=" + recordLength + ") > maxCapacityForPageSize(=" + maxCapacityForPageSize + ") -- can't fit");
        }

        final int capacity = bufferWithData.capacity();
        //Don't check capacity right here -- let allocation strategy first decide how to deal with capacity > MAX
        final int requestedRecordCapacity = allocationStrategy.capacity(
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
      final long recordOffset = idToOffset(currentRecordId);
      final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
      pagedStorage.lockWrite();
      try {
        final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, /*forWrite: */ true);
        try {
          final ByteBuffer buffer = page.getBuffer();
          final LargeBlobStorageRecordLayout recordLayout = LargeBlobStorageRecordLayout.recordLayout(buffer, offsetOnPage);
          final byte recordType = recordLayout.recordType();
          if (recordType == LargeBlobStorageRecordLayout.RECORD_TYPE_MOVED) {
            final int redirectToId = recordLayout.redirectToId(buffer, offsetOnPage);
            if (!isValidRecordId(redirectToId)) {
              throw new RecordAlreadyDeletedException("Can't write to record[" + currentRecordId + "]: it was deleted");
            }
            currentRecordId = redirectToId;
            continue;//hope redirect chains are not too long...
          }
          if (recordType != LargeBlobStorageRecordLayout.RECORD_TYPE_ACTUAL) {
            throw new AssertionError("RecordType(" + recordType + ") should not appear in the chain: " +
                                     "it is either not implemented yet, or all wrong");
          }
          final int recordCapacity = recordLayout.capacity(buffer, offsetOnPage);
          final int recordActualLength = recordLayout.length(buffer, offsetOnPage);
          //TODO RC: consider 'expectedRecordSizeHint' here? I.e. if expectedRecordSizeHint>record.capacity -> allocate heap buffer
          //         of the size asked, copy actual record content into it?
          final int recordPayloadOffset = offsetOnPage + recordLayout.headerSize();
          final ByteBuffer recordContent = buffer.slice(recordPayloadOffset, recordCapacity)
            .limit(recordActualLength)
            .order(buffer.order());

          final ByteBuffer newRecordContent = writer.write(recordContent);
          if (newRecordContent == null) {
            //returned null means writer decides to skip write -> just return current recordId
            return currentRecordId;
          }

          if (newRecordContent != recordContent) {//writer decides to allocate new buffer for content:
            newRecordContent.flip();
            final int newRecordLength = newRecordContent.remaining();
            if (newRecordLength <= recordCapacity) {
              //RC: really, in this case writer should just write data right in the 'recordContent'
              //    buffer, not allocate the new buffer -- but ok, we could deal with it:
              recordLayout.putRecord(buffer, offsetOnPage,
                                     recordCapacity, newRecordLength, NULL_ID, newRecordContent);
              page.fileSizeMayChanged(offsetOnPage + recordLayout.headerSize() + newRecordLength);
              page.markDirty();

              totalLiveRecordsPayloadBytes.addAndGet(newRecordLength - recordActualLength);
            }
            else {//current record is too small for new content -> relocate to a new place
              final int newRecordId = writeToNewlyAllocatedRecord(newRecordContent, newRecordContent.capacity());

              final LargeBlobStorageRecordLayout.MovedRecord movedRecordLayout = LargeBlobStorageRecordLayout.MovedRecord.INSTANCE;
              //mark current record as either 'moved' or 'deleted'
              final int redirectToId = leaveRedirectOnRecordRelocation ? newRecordId : NULL_ID;
              //Total space occupied by record must remain constant, but record capacity should be
              // changed since MovedRecord has another headerSize than Small|LargeRecord
              final int movedRecordCapacity = recordLayout.fullRecordSize(recordCapacity) - movedRecordLayout.headerSize();
              movedRecordLayout.putRecord(buffer, offsetOnPage, movedRecordCapacity, 0, redirectToId, null);

              page.fileSizeMayChanged(offsetOnPage + movedRecordLayout.headerSize());
              page.markDirty();

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
            recordLayout.putLength(buffer, offsetOnPage, newRecordLength);

            page.fileSizeMayChanged(offsetOnPage + recordLayout.headerSize() + newRecordLength);
            page.markDirty();

            totalLiveRecordsPayloadBytes.addAndGet(newRecordLength - recordActualLength);
          }
          return currentRecordId;
        }
        finally {
          page.unlock();
        }
      }
      finally {
        pagedStorage.unlockWrite();
      }
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
  public void deleteRecord(final int recordId) throws IOException {
    checkRecordIdExists(recordId);

    final long recordOffset = idToOffset(recordId);
    pagedStorage.lockWrite();
    try {
      final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, /*forWrite: */ true);
      final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
      try {
        final ByteBuffer buffer = page.getBuffer();
        final LargeBlobStorageRecordLayout recordLayout = LargeBlobStorageRecordLayout.recordLayout(buffer, offsetOnPage);
        final int recordCapacity = recordLayout.capacity(buffer, offsetOnPage);
        final int recordActualLength = recordLayout.length(buffer, offsetOnPage);
        final byte recordType = recordLayout.recordType();
        switch (recordType) {
          case LargeBlobStorageRecordLayout.RECORD_TYPE_MOVED -> {
            final int redirectToId = recordLayout.redirectToId(buffer, offsetOnPage);
            if (!isValidRecordId(redirectToId)) {
              throw new RecordAlreadyDeletedException("Can't delete record[" + recordId + "]: it was already deleted");
            }

            // (redirectToId=NULL) <=> 'record deleted' ('moved nowhere')
            ((LargeBlobStorageRecordLayout.MovedRecord)recordLayout).putRedirectTo(buffer, offsetOnPage, NULL_ID);
            page.fileSizeMayChanged(offsetOnPage + recordLayout.headerSize());
            page.markDirty();
          }
          case LargeBlobStorageRecordLayout.RECORD_TYPE_ACTUAL -> {
            final LargeBlobStorageRecordLayout.MovedRecord movedRecordLayout = LargeBlobStorageRecordLayout.MovedRecord.INSTANCE;
            //Total space occupied by record must remain constant, but record capacity should be
            // changed since MovedRecord has another headerSize than Small|LargeRecord
            final int deletedRecordCapacity = recordLayout.fullRecordSize(recordCapacity) - movedRecordLayout.headerSize();
            // set (redirectToId=NULL) to mark record as deleted ('moved nowhere')
            movedRecordLayout.putRecord(buffer, offsetOnPage, deletedRecordCapacity, 0, NULL_ID, null);
            page.fileSizeMayChanged(offsetOnPage + movedRecordLayout.headerSize());
            page.markDirty();
          }
          default -> throw new AssertionError("RecordType(" + recordType + ") should not appear in the chain: " +
                                              "it is either not implemented yet, or all wrong");
        }

        recordsDeleted.incrementAndGet();
        totalLiveRecordsPayloadBytes.addAndGet(-recordActualLength);
        totalLiveRecordsCapacityBytes.addAndGet(-recordCapacity);
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
      pagedStorage.lockRead();
      try {
        final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, /*forWrite: */ false);
        final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
        try {
          final ByteBuffer buffer = page.getBuffer();
          final LargeBlobStorageRecordLayout recordLayout = LargeBlobStorageRecordLayout.recordLayout(buffer, offsetOnPage);
          final byte recordType = recordLayout.recordType();
          final int recordCapacity = recordLayout.capacity(buffer, offsetOnPage);
          switch (recordType) {
            case LargeBlobStorageRecordLayout.RECORD_TYPE_ACTUAL, LargeBlobStorageRecordLayout.RECORD_TYPE_MOVED -> {
              final int headerSize = recordLayout.headerSize();
              final boolean isActual = recordType == LargeBlobStorageRecordLayout.RECORD_TYPE_ACTUAL;
              final int recordActualLength = isActual ? recordLayout.length(buffer, offsetOnPage) : -1;
              final ByteBuffer slice = isActual ?
                                       buffer.slice(offsetOnPage + headerSize, recordActualLength)
                                         .asReadOnlyBuffer()
                                         .order(buffer.order()) :
                                       buffer.slice(offsetOnPage + headerSize, 0)
                                         .asReadOnlyBuffer()
                                         .order(buffer.order());
              final boolean ok = processor.processRecord(currentId, recordCapacity, recordActualLength, slice);
              if (!ok) {
                return recordNo;
              }
            }
            default -> {
              //just skip for now
            }
          }

          final long nextRecordOffset = nextRecordOffset(recordOffset, recordLayout, recordCapacity);
          if (nextRecordOffset >= storageLength) {
            return recordNo;
          }

          currentId = offsetToId(nextRecordOffset);
        }
        finally {
          page.unlock();
        }
      }
      finally {
        pagedStorage.unlockRead();
      }
    }
  }

  @Override
  public boolean isRecordActual(final int recordActualLength) {
    return 0 <= recordActualLength;
  }

  @Override
  public int maxPayloadSupported() {
    return Math.min(MAX_CAPACITY, pagedStorage.getPageSize());
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
    pagedStorage.lockWrite();
    try {
      final DirectBufferWrapper headerPage = pagedStorage.getByteBuffer(0, /*forWrite: */ true);
      try {
        putHeaderInt(HEADER_OFFSET_FILE_STATUS, FILE_STATUS_SAFELY_CLOSED);

        putHeaderInt(HEADER_OFFSET_RECORDS_ALLOCATED, recordsAllocated.get());
        putHeaderInt(HEADER_OFFSET_RECORDS_RELOCATED, recordsRelocated.get());
        putHeaderInt(HEADER_OFFSET_RECORDS_DELETED, recordsDeleted.get());
        putHeaderLong(HEADER_OFFSET_RECORDS_LIVE_TOTAL_PAYLOAD_SIZE, totalLiveRecordsPayloadBytes.get());
        putHeaderLong(HEADER_OFFSET_RECORDS_LIVE_TOTAL_CAPACITY_SIZE, totalLiveRecordsCapacityBytes.get());
        headerPage.fileSizeMayChanged(HEADER_SIZE);
        headerPage.markDirty();
      }
      finally {
        headerPage.unlock();
      }
      pagedStorage.force();
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }

  @Override
  public void close() throws IOException {
    pagedStorage.lockWrite();
    try {
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
      pagedStorage.close();
    }
    finally {
      pagedStorage.unlockWrite();
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

  /** Storage header size */
  //RC: Method instead of constant because I expect headers to become variable-size with 'auxiliary headers' introduction
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
                                          final int requestedRecordCapacity) throws IOException {
    final int pageSize = pagedStorage.getPageSize();

    final int recordLength = content.limit();
    if (recordLength > maxCapacityForPageSize) {
      //Actually, at this point it must be guaranteed recordLength<=maxCapacityForPageSize, but lets check again:
      throw new IllegalStateException(
        "recordLength(=" + recordLength + ") > maxCapacityForPageSize(=" + maxCapacityForPageSize + ") -- can't fit");
    }
    final int implementableCapacity = Math.min(requestedRecordCapacity, maxCapacityForPageSize);
    checkCapacityHardLimit(implementableCapacity);


    final byte recordSizeType = recordSizeTypeByCapacity(implementableCapacity);
    final LargeBlobStorageRecordLayout recordLayout = recordLayoutForType(recordSizeType);
    final int fullRecordSize = recordLayout.fullRecordSize(implementableCapacity);
    if (fullRecordSize > pageSize) {
      throw new IllegalArgumentException("record size(header:" + recordLayout.headerSize() + " + capacity:" + implementableCapacity + ")" +
                                         " should be <= pageSize(=" + pageSize + ")");
    }

    final IntRef actualRecordSizeRef = new IntRef();//actual record size may be >= requested totalRecordSize 
    final int newRecordId = allocateSlotForRecord(pageSize, fullRecordSize, actualRecordSizeRef);
    final long newRecordOffset = idToOffset(newRecordId);
    final int actualRecordSize = actualRecordSizeRef.get();
    final int actualRecordCapacity = actualRecordSize - recordLayout.headerSize();
    final int newRecordLength = content.remaining();

    //check everything before write anything:
    checkCapacityHardLimit(actualRecordCapacity);
    checkLengthHardLimit(newRecordLength);

    final int offsetOnPage = pagedStorage.getOffsetInPage(newRecordOffset);
    pagedStorage.lockWrite();
    try {
      final DirectBufferWrapper page = pagedStorage.getByteBuffer(newRecordOffset, /*forWrite: */ true);
      try {
        recordLayout.putRecord(page.getBuffer(), offsetOnPage,
                               actualRecordCapacity, newRecordLength, NULL_ID,
                               content);
        page.fileSizeMayChanged(offsetOnPage + actualRecordSize);
        page.markDirty();
        return newRecordId;
      }
      finally {
        page.unlock();
      }
    }
    finally {
      pagedStorage.unlockWrite();

      recordsAllocated.incrementAndGet();
      totalLiveRecordsCapacityBytes.addAndGet(actualRecordCapacity);
      totalLiveRecordsPayloadBytes.addAndGet(newRecordLength);
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
        final int offsetOnPage = pagedStorage.getOffsetInPage(recordStartOffset);
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
    final LargeBlobStorageRecordLayout.PaddingRecord paddingRecord = LargeBlobStorageRecordLayout.PaddingRecord.INSTANCE;

    final int offsetInPage = pagedStorage.getOffsetInPage(recordOffset);
    final int remainingOnPage = pageSize - offsetInPage;

    pagedStorage.lockWrite();
    try {
      final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, /*forWrite: */ true);
      try {
        final int capacity = remainingOnPage - paddingRecord.headerSize();
        paddingRecord.putRecord(page.getBuffer(), offsetInPage, capacity, 0, NULL_ID, null);
        page.fileSizeMayChanged(offsetInPage + paddingRecord.headerSize());
        page.markDirty();
      }
      finally {
        page.unlock();
      }
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }

  private long nextRecordOffset(final long recordOffset,
                                final LargeBlobStorageRecordLayout recordLayout,
                                final int recordCapacity) {
    final int headerSize = recordLayout.headerSize();
    final long nextOffset = recordOffset + headerSize + recordCapacity;

    final int offsetOnPage = pagedStorage.getOffsetInPage(nextOffset);
    final int pageSize = pagedStorage.getPageSize();
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


  private static int roundSizeUpToBucket(final int offset,
                                         final int pageSize,
                                         final int rawRecordSize) {
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

  private static boolean isValidRecordId(final int recordId) {
    return recordId > NULL_ID;
  }


  private static void checkCapacityHardLimit(final int capacity) {
    if (!isCorrectCapacity(capacity)) {
      throw new IllegalArgumentException("capacity(=" + capacity + ") must be in [0, " + MAX_CAPACITY + "]");
    }
  }

  private static void checkLengthHardLimit(final int length) {
    if (!isCorrectLength(length)) {
      throw new IllegalArgumentException("length(=" + length + ") must be in [0, " + MAX_CAPACITY + "]");
    }
  }

  private static boolean isCorrectCapacity(final int capacity) {
    return 0 <= capacity && capacity <= MAX_CAPACITY;
  }

  private static boolean isCorrectLength(final int length) {
    return 0 <= length && length <= MAX_CAPACITY;
  }

  @NotNull
  private BatchCallback setupReportingToOpenTelemetry(final Path fileName) {
    final Meter meter = TelemetryManager.getInstance().getMeter(Storage);

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
