// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.util.io.DirectBufferWrapper;
import com.intellij.util.io.PagedFileStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Store blobs, like {@link com.intellij.util.io.storage.AbstractStorage}, but tries to be faster:
 * remove intermediate mapping (id -> offset,length), and directly use offset as recordId. Also provides
 * read/write methods direct access to underlying ByteBuffers, to reduce memcopy-ing overhead.
 */
public class StreamlinedBlobStorage implements Cloneable, AutoCloseable, Forceable {
  private static final Logger LOG = Logger.getInstance(StreamlinedBlobStorage.class);

  public static final int NULL_ID = 0;

  /* ======== Persistent format: =================================================================== */

  // Persistent format: header, records*
  //  header: version[4b], safeCloseMagic[4b]
  //  record:
  //          recordHeader: capacity[2b], actualLength[2b], redirectToOffset[4b]
  //          recordData:   byte[actualLength]?
  //
  //  1. capacity is the allocated size of the record (excluding header, only payload, so
  //     nextRecordOffset = currentRecordOffset + recordHeader(8b) + recordCapacity)
  //  2. actualLength (<=capacity) is actual size of record payload written into the record, so
  //     recordData[0..actualLength) contains actual data, and recordData[actualLength..capacity)
  //     contains trash.
  //     Negative actualLength denote removed/moved records -- to be reclaimed/compacted.
  //  3. redirectToOffset is a 'forwarding pointer' for records that was moved (e.g. re-allocated).
  //  4. records are always allocated on a single page, never break on a page boundary: if record
  //     doesn't fit current page, it is moved to another page (and remaining space filled by placeholder
  //     record)

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

  public static final int VERSION_CURRENT = 1;

  private static final int FILE_STATUS_OPENED = 0;
  private static final int FILE_STATUS_SAFELY_CLOSED = 1;
  private static final int FILE_STATUS_CORRUPTED = 2;

  private static final int HEADER_OFFSET_VERSION = 0;
  private static final int HEADER_OFFSET_FILE_STATUS = HEADER_OFFSET_VERSION + Integer.BYTES;
  private static final int HEADER_SIZE = HEADER_OFFSET_FILE_STATUS + Integer.BYTES;
  //TODO allow to reserve additional space in header for something implemented on the top of the storage

  private static final int RECORD_OFFSET_CAPACITY = 0;
  private static final int RECORD_OFFSET_ACTUAL_LENGTH = RECORD_OFFSET_CAPACITY + Short.BYTES;
  private static final int RECORD_OFFSET_REDIRECT_TO = RECORD_OFFSET_ACTUAL_LENGTH + Short.BYTES;
  private static final int RECORD_HEADER_SIZE = RECORD_OFFSET_REDIRECT_TO + Integer.BYTES;

  /**
   * Value of recordActualLength used to mark 'padding' record (fake record to fill space till the
   * end of page, if there is not enough space remains on current page for actual record to fit)
   */
  private static final short PADDING_RECORD_MARK = (short)-1;
  /**
   * Value of recordActualLength used as 'moved' (reallocated) mark
   */
  private static final short MOVED_RECORD_MARK = (short)-2;
  /**
   * Value of recordActualLength used as 'deleted' mark
   */
  private static final short DELETED_RECORD_MARK = (short)-3;

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


  public StreamlinedBlobStorage(final @NotNull PagedFileStorage pagedStorage,
                                final @NotNull SpaceAllocationStrategy allocationStrategy) throws IOException {
    this.pagedStorage = pagedStorage;
    this.allocationStrategy = allocationStrategy;

    final short defaultCapacity = allocationStrategy.defaultCapacity();
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
      if (length >= HEADER_SIZE) {
        final int version = readHeaderVersion();
        final int fileStatus = readHeaderFileStatus();
        if (version != VERSION_CURRENT) {
          throw new IOException(
            "Can't read file[" + pagedStorage + "]: version(" + version + ") != storage version (" + VERSION_CURRENT + ")");
        }
        if (fileStatus != FILE_STATUS_SAFELY_CLOSED) {
          throw new IOException(
            "Can't read file[" + pagedStorage + "]: status(" + fileStatus + ") != SAFELY_CLOSED (" + FILE_STATUS_SAFELY_CLOSED + ")");
        }
        if (length > Integer.MAX_VALUE * (long)OFFSET_BUCKET) {
          throw new IOException("Can't read file[" + pagedStorage + "]: too big, " + length + " > Integer.MAX_VALUE * " + OFFSET_BUCKET);
        }
        nextRecordId = offsetToId(length);
      }
      else {
        nextRecordId = offsetToId(recordsStartOffset());
      }
      putHeaderVersion(VERSION_CURRENT);
      putHeaderFileStatus(FILE_STATUS_OPENED);
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }


  public int getVersion() throws IOException {
    pagedStorage.lockRead();
    try {
      return readHeaderVersion();
    }
    finally {
      pagedStorage.unlockRead();
    }
  }

  //TODO RC: there are 2 notation of version really -- record storage version, and version of anything implemented on the top of
  //         record storage. This version is which of the two?
  public void setVersion(final int expectedVersion) throws IOException {
    pagedStorage.lockWrite();
    try {
      putHeaderVersion(expectedVersion);
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
                           final @Nullable int[] redirectToIdRef) throws IOException {
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
        final short recordActualLength = readRecordLength(buffer, offsetOnPage);
        final int recordRedirectedToId = readRecordRedirectToId(buffer, offsetOnPage);

        if (redirectToIdRef != null) {
          redirectToIdRef[0] = recordId;
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
                              final int[] redirectToIdRef) throws IOException {
    pagedStorage.lockRead();
    try {
      checkRecordIdExists(recordId);
      final long recordOffset = idToOffset(recordId);
      final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
      final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, false);
      try {
        final ByteBuffer buffer = page.getBuffer();
        final short recordCapacity = readRecordCapacity(buffer, offsetOnPage);
        final short recordActualLength = readRecordLength(buffer, offsetOnPage);
        final int recordRedirectedToId = readRecordRedirectToId(buffer, offsetOnPage);

        if (redirectToIdRef != null) {
          redirectToIdRef[0] = recordId;
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
   */
  public int writeToRecord(final int recordId,
                           final @NotNull Writer writer) throws IOException {
    if (!isValidRecordId(recordId)) {
      //insert new record:
      final ByteBuffer temp = acquireTemporaryBuffer();
      try {
        final ByteBuffer bufferWithData = writer.write(temp);
        bufferWithData.flip();
        final short recordLength = (short)bufferWithData.limit();
        if (recordLength < 0) {
          throw new IllegalStateException("Buffer length (" + bufferWithData.limit() + ") not fit in short");
        }
        final short capacity = (short)bufferWithData.capacity();
        if (capacity < 0) {
          throw new IllegalStateException("Buffer capacity (" + bufferWithData.capacity() + ") not fit in short");
        }
        final short recordCapacity = allocationStrategy.capacity(
          recordLength,
          capacity
        );
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

    pagedStorage.lockWrite();
    try {
      final long recordOffset = idToOffset(recordId);
      final int offsetOnPage = pagedStorage.getOffsetInPage(recordOffset);
      final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, true);
      try {
        final ByteBuffer buffer = page.getBuffer();
        final short recordCapacity = readRecordCapacity(buffer, offsetOnPage);
        final short recordActualLength = readRecordLength(buffer, offsetOnPage);
        final int recordRedirectedToId = readRecordRedirectToId(buffer, offsetOnPage);

        if (!isRecordActual(recordActualLength)) {//record deleted or moved: check was it moved to a new location?
          if (!isValidRecordId(recordRedirectedToId)) {
            throw new IOException("Can't write to record[" + recordId + "]: it was deleted");
          }
          //hope redirect chains are not too long...
          return writeToRecord(recordRedirectedToId, writer);
        }

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
            //RC: really, in this case writer should just write data in the 'recordContent' buffer, but ok, we could deal with it:
            putRecordPayload(buffer, offsetOnPage, newRecordContent, newRecordLength);
            page.fileSizeMayChanged(offsetOnPage + RECORD_HEADER_SIZE + newRecordLength);
          }
          else {
            final int newRecordId = allocateRecord(newRecordContent, (short)newRecordContent.capacity());
            putRecordLength(buffer, offsetOnPage, MOVED_RECORD_MARK);
            putRecordRedirectTo(buffer, offsetOnPage, newRecordId);
            page.fileSizeMayChanged(offsetOnPage + RECORD_HEADER_SIZE);
            return newRecordId;
          }
        }
        else {//if newRecordContent is null or == recordContent -> changes are already written by writer into the recordContent,
          // we only need to adjust record header:
          recordContent.flip();
          final int newRecordLength = recordContent.remaining();
          assert (newRecordLength <= recordCapacity) : newRecordLength + " > " + recordCapacity +
                                                       ": can't be, since recordContent.capacity()==recordCapacity!";
          putRecordLength(buffer, offsetOnPage, (short)newRecordLength);
          page.fileSizeMayChanged(offsetOnPage + RECORD_HEADER_SIZE + newRecordLength);
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
        final short recordActualLength = readRecordLength(buffer, offsetOnPage);
        final int recordRedirectedToId = readRecordRedirectToId(buffer, offsetOnPage);

        if (isRecordDeleted(recordActualLength)) {//record deleted or moved: check was it moved to a new location?
          throw new IllegalStateException("Can't delete record[" + recordId + "]: it was already deleted");
        }
        else {
          putRecordLength(buffer, offsetOnPage, DELETED_RECORD_MARK);
          putRecordRedirectTo(buffer, offsetOnPage, NULL_ID);
          page.fileSizeMayChanged(offsetOnPage + RECORD_HEADER_SIZE);
          page.markDirty();
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
   * see {@link #isRecordActual(short)}.
   * Scanning stops if processor return false.
   *
   * @return how many records were processed
   */
  public int forEach(final @NotNull Processor processor) throws IOException {
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
          final short recordCapacity = readRecordCapacity(buffer, offsetOnPage);
          final short recordActualLength = readRecordLength(buffer, offsetOnPage);

          final ByteBuffer slice = isRecordActual(recordActualLength) ?
                                   buffer.slice(offsetOnPage + RECORD_HEADER_SIZE, recordActualLength)
                                     .asReadOnlyBuffer()
                                     .order(buffer.order()) :
                                   buffer.slice(offsetOnPage + RECORD_HEADER_SIZE, 0)
                                     .asReadOnlyBuffer()
                                     .order(buffer.order());
          final boolean ok = processor.process(currentId, recordCapacity, recordActualLength, slice);
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

  public boolean isRecordActual(final short recordActualLength) {
    return recordActualLength >= 0;
  }

  public interface Reader<T> {
    public T read(@NotNull final ByteBuffer data) throws IOException;
  }

  public interface Writer {
    public ByteBuffer write(@NotNull final ByteBuffer data) throws IOException;
  }


  public interface Processor {
    boolean process(final int recordId,
                    final short recordCapacity,
                    final short recordLength,
                    final ByteBuffer payload) throws IOException;
  }

  public interface SpaceAllocationStrategy {
    /**
     * @return how long buffers create for a new record (i.e. in {@link #writeToRecord(int, ThrowableNotNullFunction)}
     * there recordId=NULL_ID)
     */
    short defaultCapacity();

    /**
     * @return given buffer, returned by writer in a {@link #writeToRecord(int, ThrowableNotNullFunction)}, how big
     * record to allocate? buffer actual size (limit-position) and buffer capacity is considered.
     * returned value must be >= actualLength
     */
    short capacity(final short actualLength,
                   final short currentCapacity);

    public static class WriterDecidesStrategy implements SpaceAllocationStrategy {
      private final short defaultCapacity;

      public WriterDecidesStrategy(final short defaultCapacity) {
        if (defaultCapacity <= 0) {
          throw new IllegalArgumentException("defaultCapacity(" + defaultCapacity + ") must be > 0");
        }
        this.defaultCapacity = defaultCapacity;
      }

      @Override
      public short defaultCapacity() {
        return defaultCapacity;
      }

      @Override
      public short capacity(final short actualLength,
                            final short currentCapacity) {
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

    public static class DataLengthPlusFixedPercentStrategy implements SpaceAllocationStrategy {
      private final short defaultCapacity;
      private final short minCapacity;
      private final int percentOnTheTop;

      public DataLengthPlusFixedPercentStrategy(final short defaultCapacity,
                                                final short minCapacity,
                                                final int percentOnTheTop) {
        if (defaultCapacity <= 0) {
          throw new IllegalArgumentException("defaultCapacity(" + defaultCapacity + ") must be > 0");
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
      public short defaultCapacity() {
        return defaultCapacity;
      }

      @Override
      public short capacity(final short actualLength,
                            final short currentCapacity) {
        if (actualLength < 0) {
          throw new IllegalArgumentException("actualLength(=" + actualLength + " should be >=0");
        }
        if (currentCapacity < actualLength) {
          throw new IllegalArgumentException("currentCapacity(=" + currentCapacity + ") should be >= actualLength(=" + actualLength + ")");
        }
        final double capacity = actualLength * (1.0 + percentOnTheTop / 100.0);
        final short advisedCapacity = (short)Math.max(minCapacity, capacity + 1);
        if (advisedCapacity < 0) {
          return Short.MAX_VALUE;
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

  public int recordsCount() {
    //TODO
    throw new UnsupportedOperationException("Method not implemented yet");
  }

  public int liveRecordsCount() {
    //TODO
    throw new UnsupportedOperationException("Method not implemented yet");
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
    pagedStorage.force();
  }

  @Override
  public void close() throws IOException {
    pagedStorage.lockWrite();
    try {
      if (!closed) {
        //.close() methods are better to be idempotent, i.e. not throw exceptions on repeating calls, but just
        // silently ignore attempts to close already closed. And pagedStorage (seems to) conforms with that. But
        // here we try to write file status, and without .close flag we'll try to do that even on already closed
        // pagedStorage, which leads to exception.
        putHeaderFileStatus(FILE_STATUS_SAFELY_CLOSED);
        //MAYBE RC: generally, it should not be this class's responsibility to close pagedStorage, since not this
        // class creates it -- who creates it, is responsible for closing it.
        pagedStorage.close();
        closed = true;
      }
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }

  @Override
  public String toString() {
    return "StreamlinedBlobStorage{" + pagedStorage + "}{nextRecordId: " + nextRecordId + '}';
  }

  /* ============================= implementation ================================================================================= */

  /**
   * content buffer is passed in 'ready for write' state: position=0, limit=[#last byte of payload]
   */
  private int allocateRecord(final ByteBuffer content,
                             final short newRecordCapacity) throws IOException {
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
      final short recordCapacityRoundedUp = roundCapacityUpToBucket(offsetOnPage, pageSize, newRecordCapacity);

      final DirectBufferWrapper page = pagedStorage.getByteBuffer(recordOffset, true);
      final ByteBuffer buffer = page.getBuffer();
      try {
        putRecordHeader(buffer, offsetOnPage,
                        recordCapacityRoundedUp,
                        (short)newRecordLength,
                        NULL_ID);
        putRecordPayload(buffer, offsetOnPage, content, newRecordLength);

        page.fileSizeMayChanged(offsetOnPage + RECORD_HEADER_SIZE + recordCapacityRoundedUp);
        page.markDirty();
      }
      finally {
        page.unlock();
      }

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
        putRecordHeader(buffer, offsetInPage,
                        (short)(remainingOnPage - RECORD_HEADER_SIZE),
                        PADDING_RECORD_MARK,
                        NULL_ID
        );
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

  private void putHeaderFileStatus(final int status) throws IOException {
    pagedStorage.putInt(HEADER_OFFSET_FILE_STATUS, status);
  }

  private void putHeaderVersion(final int version) throws IOException {
    pagedStorage.putInt(HEADER_OFFSET_VERSION, version);
  }

  private int readHeaderFileStatus() throws IOException {
    return this.pagedStorage.getInt(HEADER_OFFSET_FILE_STATUS);
  }

  private int readHeaderVersion() throws IOException {
    return pagedStorage.getInt(HEADER_OFFSET_VERSION);
  }

  private long headerSize() {
    return HEADER_SIZE;
  }

  private long recordsStartOffset() {
    return (headerSize() / OFFSET_BUCKET + 1) * OFFSET_BUCKET;
  }

  @NotNull
  private ByteBuffer acquireTemporaryBuffer() {
    final ByteBuffer temp = threadLocalBuffer.get();
    threadLocalBuffer.set(null);
    if (temp != null) {
      return temp.position(0)
        .limit(0);
    }
    else {
      final ByteBuffer buffer = ByteBuffer.allocate(allocationStrategy.defaultCapacity());
      if (pagedStorage.isNativeBytesOrder()) {
        buffer.order(ByteOrder.nativeOrder());
      }
      return buffer;
    }
  }

  private void releaseTemporaryBuffer(final @NotNull ByteBuffer temp) {
    threadLocalBuffer.set(temp);
  }


  private static short readRecordCapacity(final ByteBuffer pageBuffer,
                                          final int offsetOnPage) {
    return pageBuffer.getShort(offsetOnPage + RECORD_OFFSET_CAPACITY);
  }

  private static int readRecordRedirectToId(final ByteBuffer pageBuffer,
                                            final int offsetOnPage) {
    return pageBuffer.getInt(offsetOnPage + RECORD_OFFSET_REDIRECT_TO);
  }

  private static short readRecordLength(final ByteBuffer pageBuffer,
                                        final int offsetOnPage) {
    return pageBuffer.getShort(offsetOnPage + RECORD_OFFSET_ACTUAL_LENGTH);
  }

  private static void putRecordPayload(final ByteBuffer pageBuffer,
                                       final int offsetOnPage,
                                       final ByteBuffer recordData,
                                       final int newRecordLength) {
    pageBuffer.put(offsetOnPage + RECORD_HEADER_SIZE, recordData, 0, newRecordLength);
  }

  private static void putRecordHeader(final ByteBuffer pageBuffer,
                                      final int offsetOnPage,
                                      final short recordCapacity,
                                      final short recordLength,
                                      final int redirectToId) {
    putRecordCapacity(pageBuffer, offsetOnPage, recordCapacity);
    putRecordLength(pageBuffer, offsetOnPage, recordLength);
    putRecordRedirectTo(pageBuffer, offsetOnPage, redirectToId);
  }

  @NotNull
  private static ByteBuffer putRecordCapacity(final ByteBuffer pageBuffer,
                                              final int offsetOnPage,
                                              final short recordCapacity) {
    return pageBuffer.putShort(offsetOnPage + RECORD_OFFSET_CAPACITY, recordCapacity);
  }

  @NotNull
  private static ByteBuffer putRecordLength(final ByteBuffer pageBuffer,
                                            final int offsetOnPage,
                                            final short recordLength) {
    return pageBuffer.putShort(offsetOnPage + RECORD_OFFSET_ACTUAL_LENGTH, recordLength);
  }

  @NotNull
  private static ByteBuffer putRecordRedirectTo(final ByteBuffer pageBuffer,
                                                final int offsetOnPage,
                                                final int redirectToId) {
    return pageBuffer.putInt(offsetOnPage + RECORD_OFFSET_REDIRECT_TO, redirectToId);
  }

  private static short roundCapacityUpToBucket(final int offset,
                                               final int pageSize,
                                               final short rawCapacity) {
    short capacityRoundedUp = rawCapacity;
    if (capacityRoundedUp % OFFSET_BUCKET != 0) {
      capacityRoundedUp = (short)((capacityRoundedUp / OFFSET_BUCKET + 1) * OFFSET_BUCKET);
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

  private static boolean isRecordDeleted(final short recordActualLength) {
    return recordActualLength == DELETED_RECORD_MARK;
  }

  private static boolean isValidRecordId(final int recordId) {
    return recordId > NULL_ID;
  }
}
