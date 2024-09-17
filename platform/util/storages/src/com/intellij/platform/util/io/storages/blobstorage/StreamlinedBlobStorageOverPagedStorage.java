// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.blobstorage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IntRef;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.DirectBufferWrapper;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.blobstorage.ByteBufferReader;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import com.intellij.util.io.blobstorage.SpaceAllocationStrategy;
import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;
import io.opentelemetry.api.metrics.BatchCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

import static com.intellij.platform.util.io.storages.blobstorage.RecordLayout.ActualRecords.recordLayoutForType;
import static com.intellij.platform.util.io.storages.blobstorage.RecordLayout.ActualRecords.recordSizeTypeByCapacity;
import static com.intellij.platform.util.io.storages.blobstorage.RecordLayout.OFFSET_BUCKET;
import static com.intellij.util.io.IOUtil.magicWordToASCII;


/**
 * Backport of {@link StreamlinedBlobStorageOverLockFreePagedStorage} to on the top of {@link PagedFileStorage}
 * <p/>
 * Implements {@link StreamlinedBlobStorage} blobs over {@link PagedFileStorage} storage.
 * Implementation is thread-safe (protected by {@link com.intellij.util.io.StorageLockContext} locks)
 * <p/>
 * Storage is optimized to store small records (~tens bytes) -- it tries to compress record headers
 * so smaller records have just 2 bytes of overhead because of header. At the same time storage allows
 * record size up to 1Mb large.
 * <p>
 */
public final class StreamlinedBlobStorageOverPagedStorage extends StreamlinedBlobStorageHelper {
  private static final Logger LOG = Logger.getInstance(StreamlinedBlobStorageOverPagedStorage.class);

  //For persistent format description see comments in superclass

  public static final int STORAGE_VERSION_CURRENT = 2;


  /* ============== instance fields: ====================================================================== */


  private final @NotNull PagedFileStorage pagedStorage;

  //==== monitoring fields: =======================================================================================

  private final BatchCallback openTelemetryCallback;


  public StreamlinedBlobStorageOverPagedStorage(@NotNull PagedFileStorage pagedStorage,
                                                @NotNull SpaceAllocationStrategy allocationStrategy) throws IOException {
    super(allocationStrategy,
          pagedStorage.getPageSize(),
          pagedStorage.isNativeBytesOrder() ? ByteOrder.nativeOrder() : ByteOrder.BIG_ENDIAN
    );
    this.pagedStorage = pagedStorage;

    pagedStorage.lockWrite();
    try {
      DirectBufferWrapper headerPage = pagedStorage.getByteBuffer(0, /*forWrite: */ true);
      try {
        long length = pagedStorage.length();
        if (length > MAX_FILE_LENGTH) {
          throw new IOException(
            "Can't read file[" + pagedStorage + "]: too big, " + length + "b > max(Integer.MAX_VALUE * " + OFFSET_BUCKET + ")");
        }

        if (length == 0) {//new empty file
          putHeaderInt(HeaderLayout.MAGIC_WORD_OFFSET, MAGIC_WORD);
          putHeaderInt(HeaderLayout.STORAGE_VERSION_OFFSET, STORAGE_VERSION_CURRENT);
          putHeaderInt(HeaderLayout.PAGE_SIZE_OFFSET, pageSize);

          updateNextRecordId(offsetToId(recordsStartOffset()));

          this.wasClosedProperly.set(true);
        }
        else {
          int magicWord = readHeaderInt(HeaderLayout.MAGIC_WORD_OFFSET);
          if (magicWord != MAGIC_WORD) {
            throw new IOException("[" + pagedStorage.getFile() + "] is of incorrect type: " +
                                  ".magicWord(=" + magicWord + ", '" + magicWordToASCII(magicWord) + "') != " + MAGIC_WORD + " expected");
          }

          int version = readHeaderInt(HeaderLayout.STORAGE_VERSION_OFFSET);
          if (version != STORAGE_VERSION_CURRENT) {
            throw new IOException(
              "[" + pagedStorage.getFile() + "]: file version(" + version + ") != current impl version (" + STORAGE_VERSION_CURRENT + ")");
          }
          if (length > MAX_FILE_LENGTH) {
            throw new IOException(
              "[" + pagedStorage.getFile() + "]: too big, " + length + " > Integer.MAX_VALUE * " + OFFSET_BUCKET);
          }

          int filePageSize = readHeaderInt(HeaderLayout.PAGE_SIZE_OFFSET);
          if (pageSize != filePageSize) {
            throw new IOException("[" + pagedStorage.getFile() + "]: file created with pageSize=" + filePageSize +
                                  " but current storage.pageSize=" + pageSize);
          }

          int nextRecordId = readHeaderInt(HeaderLayout.NEXT_RECORD_ID_OFFSET);
          updateNextRecordId(nextRecordId);

          recordsAllocated.set(readHeaderInt(HeaderLayout.RECORDS_ALLOCATED_OFFSET));
          recordsRelocated.set(readHeaderInt(HeaderLayout.RECORDS_RELOCATED_OFFSET));
          recordsDeleted.set(readHeaderInt(HeaderLayout.RECORDS_DELETED_OFFSET));
          totalLiveRecordsPayloadBytes.set(readHeaderLong(HeaderLayout.RECORDS_LIVE_TOTAL_PAYLOAD_SIZE_OFFSET));
          totalLiveRecordsCapacityBytes.set(readHeaderLong(HeaderLayout.RECORDS_LIVE_TOTAL_CAPACITY_SIZE_OFFSET));

          boolean wasClosedProperly = readHeaderInt(HeaderLayout.FILE_STATUS_OFFSET) == FILE_STATUS_PROPERLY_CLOSED;
          this.wasClosedProperly.set(wasClosedProperly);
        }


        putHeaderInt(HeaderLayout.FILE_STATUS_OFFSET, FILE_STATUS_OPENED);

        headerPage.fileSizeMayChanged(HeaderLayout.HEADER_SIZE);
        headerPage.markDirty();
      }
      finally {
        headerPage.unlock();
      }
    }
    finally {
      pagedStorage.unlockWrite();
    }

    openTelemetryCallback = setupReportingToOpenTelemetry(pagedStorage.getFile().getFileName(), this);
  }

  @Override
  public int getStorageVersion() throws IOException {
    pagedStorage.lockRead();
    try {
      return readHeaderInt(HeaderLayout.STORAGE_VERSION_OFFSET);
    }
    finally {
      pagedStorage.unlockRead();
    }
  }

  @Override
  public int getDataFormatVersion() throws IOException {
    pagedStorage.lockRead();
    try {
      return readHeaderInt(HeaderLayout.DATA_FORMAT_VERSION_OFFSET);
    }
    finally {
      pagedStorage.unlockRead();
    }
  }

  @Override
  public void setDataFormatVersion(int expectedVersion) throws IOException {
    pagedStorage.lockWrite();
    try {
      putHeaderInt(HeaderLayout.DATA_FORMAT_VERSION_OFFSET, expectedVersion);
    }
    finally {
      pagedStorage.unlockWrite();
    }
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
      pagedStorage.lockRead();
      try {
        DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, /*forWrite: */ false);
        try {
          int offsetOnPage = toOffsetOnPage(recordOffset);
          ByteBuffer buffer = page.getBuffer();
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
  public <Out> Out readRecord(int recordId,
                              @NotNull ByteBufferReader<Out> reader,
                              @Nullable IntRef redirectToIdRef) throws IOException {
    checkRecordIdExists(recordId);
    int currentRecordId = recordId;
    for (int i = 0; i < MAX_REDIRECTS; i++) {
      long recordOffset = idToOffset(currentRecordId);
      pagedStorage.lockRead();
      try {
        int offsetOnPage = toOffsetOnPage(recordOffset);
        DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, /*forWrite: */ false);
        try {
          ByteBuffer buffer = page.getBuffer();
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
      int offsetOnPage = toOffsetOnPage(recordOffset);
      pagedStorage.lockWrite();
      try {
        DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, /*forWrite: */ true);
        try {
          ByteBuffer buffer = page.getBuffer();
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
              page.fileSizeMayChanged(offsetOnPage + recordLayout.headerSize() + newRecordLength);
              page.markDirty();

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
            int newRecordLength = recordContent.remaining();
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
  public void deleteRecord(int recordId) throws IOException {
    checkRecordIdExists(recordId);

    long recordOffset = idToOffset(recordId);
    pagedStorage.lockWrite();
    try {
      DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, /*forWrite: */ true);
      int offsetOnPage = toOffsetOnPage(recordOffset);
      try {
        ByteBuffer buffer = page.getBuffer();
        RecordLayout recordLayout = RecordLayout.recordLayout(buffer, offsetOnPage);
        int recordCapacity = recordLayout.capacity(buffer, offsetOnPage);
        int recordActualLength = recordLayout.length(buffer, offsetOnPage);
        byte recordType = recordLayout.recordType();
        switch (recordType) {
          case RecordLayout.RECORD_TYPE_MOVED -> {
            int redirectToId = recordLayout.redirectToId(buffer, offsetOnPage);
            if (!isValidRecordId(redirectToId)) {
              throw new RecordAlreadyDeletedException("Can't delete record[" + recordId + "]: it was already deleted");
            }

            // (redirectToId=NULL) <=> 'record deleted' ('moved nowhere')
            ((RecordLayout.MovedRecord)recordLayout).putRedirectTo(buffer, offsetOnPage, NULL_ID);
            page.fileSizeMayChanged(offsetOnPage + recordLayout.headerSize());
            page.markDirty();
          }
          case RecordLayout.RECORD_TYPE_ACTUAL -> {
            RecordLayout.MovedRecord movedRecordLayout = RecordLayout.MovedRecord.INSTANCE;
            //Total space occupied by record must remain constant, but record capacity should be
            // changed since MovedRecord has another headerSize than Small|LargeRecord
            int deletedRecordCapacity = recordLayout.fullRecordSize(recordCapacity) - movedRecordLayout.headerSize();
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
    long storageLength = pagedStorage.length();
    int currentId = offsetToId(recordsStartOffset());
    for (int recordNo = 0; ; recordNo++) {
      long recordOffset = idToOffset(currentId);
      pagedStorage.lockRead();
      try {
        DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, /*forWrite: */ false);
        int offsetOnPage = toOffsetOnPage(recordOffset);
        try {
          ByteBuffer buffer = page.getBuffer();
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
  public long sizeInBytes() throws ClosedStorageException {
    checkNotClosed();
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
      DirectBufferWrapper headerPage = pagedStorage.getByteBuffer(0, /*forWrite: */ true);
      try {
        putHeaderInt(HeaderLayout.NEXT_RECORD_ID_OFFSET, nextRecordId());
        putHeaderInt(HeaderLayout.RECORDS_ALLOCATED_OFFSET, recordsAllocated.get());
        putHeaderInt(HeaderLayout.RECORDS_RELOCATED_OFFSET, recordsRelocated.get());
        putHeaderInt(HeaderLayout.RECORDS_DELETED_OFFSET, recordsDeleted.get());
        putHeaderLong(HeaderLayout.RECORDS_LIVE_TOTAL_PAYLOAD_SIZE_OFFSET, totalLiveRecordsPayloadBytes.get());
        putHeaderLong(HeaderLayout.RECORDS_LIVE_TOTAL_CAPACITY_SIZE_OFFSET, totalLiveRecordsCapacityBytes.get());

        headerPage.fileSizeMayChanged(HeaderLayout.HEADER_SIZE);
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
        putHeaderInt(HeaderLayout.FILE_STATUS_OFFSET, FILE_STATUS_PROPERLY_CLOSED);

        force();

        closed.set(true);

        openTelemetryCallback.close();

        //MAYBE RC: it shouldn't be this class's responsibility to close pagedStorage, since not this class creates it?
        //          Better whoever creates it -- is responsible for closing it?
        pagedStorage.close();
      }
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }

  // ============================= implementation: ========================================================================

  // === storage header accessors: ===

  @Override
  protected @NotNull Path storagePath() {
    return pagedStorage.getFile();
  }

  private int readHeaderInt(int offset) throws IOException {
    assert (0 <= offset && offset <= HeaderLayout.HEADER_SIZE - Integer.BYTES)
      : "header offset(=" + offset + ") must be in [0," + (HeaderLayout.HEADER_SIZE - Integer.BYTES) + "]";
    return pagedStorage.getInt(offset);
  }

  private void putHeaderInt(int offset,
                            int value) throws IOException {
    assert (0 <= offset && offset <= HeaderLayout.HEADER_SIZE - Integer.BYTES)
      : "header offset(=" + offset + ") must be in [0," + (HeaderLayout.HEADER_SIZE - Integer.BYTES) + "]";
    pagedStorage.putInt(offset, value);
  }

  private long readHeaderLong(int offset) throws IOException {
    assert (0 <= offset && offset <= HeaderLayout.HEADER_SIZE - Long.BYTES)
      : "header offset(=" + offset + ") must be in [0," + (HeaderLayout.HEADER_SIZE - Long.BYTES) + "]";
    return pagedStorage.getLong(offset);
  }

  private void putHeaderLong(int offset,
                             long value) throws IOException {
    assert (0 <= offset && offset <= HeaderLayout.HEADER_SIZE - Long.BYTES)
      : "header offset(=" + offset + ") must be in [0," + (HeaderLayout.HEADER_SIZE - Long.BYTES) + "]";
    pagedStorage.putLong(offset, value);
  }

  // === storage records accessors: ===

  /**
   * content buffer is passed in 'ready for write' state: position=0, limit=[#last byte of payload]
   */
  private int writeToNewlyAllocatedRecord(ByteBuffer content,
                                          int requestedRecordCapacity) throws IOException {
    int pageSize = pagedStorage.getPageSize();

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

    int offsetOnPage = toOffsetOnPage(newRecordOffset);
    pagedStorage.lockWrite();
    try {
      DirectBufferWrapper page = pagedStorage.getByteBuffer(newRecordOffset, /*forWrite: */ true);
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


  @Override
  protected void putSpaceFillerRecord(long recordOffset,
                                      int pageSize) throws IOException {
    RecordLayout.PaddingRecord paddingRecord = RecordLayout.PaddingRecord.INSTANCE;

    int offsetInPage = toOffsetOnPage(recordOffset);
    int remainingOnPage = pageSize - offsetInPage;

    pagedStorage.lockWrite();
    try {
      DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, /*forWrite: */ true);
      try {
        int capacity = remainingOnPage - paddingRecord.headerSize();
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
}
