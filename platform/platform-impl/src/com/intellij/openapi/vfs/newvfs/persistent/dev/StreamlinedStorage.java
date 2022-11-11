// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.DirectBufferWrapper;
import com.intellij.util.io.PagedFileStorage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Function;

/**
 * Try to re-implement storage: remove intermediate mapping (id -> offset,length), and
 * directly use offset as recordId
 */
public class StreamlinedStorage implements Cloneable, AutoCloseable, Forceable {
  private static final Logger LOG = Logger.getInstance(StreamlinedStorage.class);

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
  //  2. actualLength (<=capacity) is actual size of record payload written into the record, so recordData[0..actualLength)
  //     contains actual data, and recordData[actualLength..capacity) contains trash.
  //     Negative actualLength denote removed/moved records -- to be reclaimed/compacted.
  //  3. redirectToOffset is a 'forwarding pointer' for records that was moved (e.g. re-allocated).
  //  4. records are always allocated on a single page, never break on a page boundary: if record
  //     doesn't fit current page, it is moved to another page (and remaining space filled by placeholder
  //     record)

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


  private int nextRecordId;


  public StreamlinedStorage(final @NotNull PagedFileStorage pagedStorage) throws IOException {
    this.pagedStorage = pagedStorage;

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
                              final @NotNull Function<ByteBuffer, Out> reader) throws IOException {
    return readRecord(recordId, reader, null);
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
                              final @NotNull Function<ByteBuffer, Out> reader,
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
        return reader.apply(slice);
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
   * <br> <br>
   * Capacity: if new payload fits into buffer passed in -> it could be written right into it. If new
   * payload requires more space, writer should allocate its own buffer with enough capacity, write
   * new payload into it, and return that buffer (in a 'after puts' state), instead of buffer passed
   * in. Storage will re-allocate space for the record with capacity >= returned buffer capacity.
   */
  public int writeToRecord(final int recordId,
                           final @NotNull Function<ByteBuffer, ByteBuffer> writer) throws IOException {
    if (!isValidRecordId(recordId)) {
      //insert new record:
      //TODO RC: reconsider buffer allocation/capacity allocation strategy
      final ByteBuffer temp = ByteBuffer.allocate(1024).clear();
      final ByteBuffer bufferWithData = writer.apply(temp);
      bufferWithData.flip();
      return allocateRecord(bufferWithData, (short)bufferWithData.capacity());
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

        final ByteBuffer newRecordContent = writer.apply(recordContent);

        if (newRecordContent != null && newRecordContent != recordContent) {
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
          if (!isValidRecordId(recordRedirectedToId)) {
            throw new IOException("Can't delete record[" + recordId + "]: it was already deleted");
          }
          //hope redirect chains are not too long...
          deleteRecord(recordRedirectedToId);
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


  public interface Processor {
    boolean process(final int recordId,
                           final short recordCapacity,
                           final short recordLength,
                           final ByteBuffer payload) throws IOException;
  }

  public interface RecordAllocationStrategy {
    short capacity(final short actualLength,
                   final short currentCapacity);

    short defaultCapacity();

    RecordAllocationStrategy WRITER_DECIDES = new RecordAllocationStrategy() {
      @Override
      public short defaultCapacity() {
        return 128;
      }

      @Override
      public short capacity(final short actualLength,
                            final short currentCapacity) {
        return currentCapacity;
      }
    };
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
  public void close() throws Exception {
    pagedStorage.lockWrite();
    try {
      putHeaderFileStatus(FILE_STATUS_SAFELY_CLOSED);
      pagedStorage.close();
    }
    finally {
      pagedStorage.unlockWrite();
    }
  }

  /* ============================= implementation ================================================================================= */

  /**
   * content buffer is passed in 'ready for write' state: position=0, limit=[#last byte of payload]
   */
  private int allocateRecord(final ByteBuffer content,
                             final short newRecordCapacity) throws IOException {
    final int pageSize = pagedStorage.getPageSize();
    if (RECORD_HEADER_SIZE + newRecordCapacity > pageSize) {
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
      final long endPage = (recordOffset + RECORD_HEADER_SIZE + newRecordCapacity - 1) / pageSize;
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
    if (!isValidRecordId(recordId)) {
      throw new IllegalArgumentException("recordId(" + recordId + ") is invalid: must be > 0");
    }
    if (recordId >= nextRecordId) {
      throw new IllegalArgumentException("recordId(" + recordId + ") is not yet allocated: must be < " + nextRecordId);
    }
  }

  private static boolean isRecordDeleted(final short recordActualLength) {
    return recordActualLength < 0;
  }

  private static boolean isValidRecordId(final int recordId) {
    return recordId > NULL_ID;
  }
}
