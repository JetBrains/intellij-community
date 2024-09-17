// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.blobstorage;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.util.IntRef;
import com.intellij.util.io.CleanableStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Store blobs, like {@link com.intellij.util.io.storage.AbstractStorage}, but tries to be faster:
 * remove intermediate mapping (id -> offset,length), and recordId directly mapped to the record
 * offset in file.
 * Also, read/write methods have direct access to underlying ByteBuffers, to reduce memcopy-ing
 * overhead.
 * <br/>
 * Direct mapping between recordId and offsets means that recordId changes if the record is relocated
 * (e.g. because its content after update does not fit currently allocated space). This opens the
 * concept of record redirection (redirect-to pointers): if record is relocated to a new location,
 * the old location gets special mark 'redirected', and keeps the new recordId. Clients could
 * still access relocated record by that old recordId -- storage follows the redirection chain
 * internally. But storage also returns new (actual) recordId via redirectToIdRef out-param, so
 * clients could update their links, and not bear the cost of redirection next time.
 * <br/>
 * Storage is designed for performance, hence API is quite low-level, and needs care to be used correctly.
 * I've tried to hide implementation details AMAP, but some of them are visible through API anyway,
 * because hiding them (seems to) will cost performance.
 * <br/>
 * <br/>
 * Thread safety is a property of specific implementation
 */
@ApiStatus.Internal
public interface StreamlinedBlobStorage extends Closeable, AutoCloseable, Forceable, CleanableStorage {
  int NULL_ID = 0;

  /** @return version of storage internal format. Read only -- storage manages it */
  int getStorageVersion() throws IOException;

  /**
   * @return true if the storage was properly closed last time, false if it wasn't and data could be inconsistent
   * because of that
   */
  boolean wasClosedProperly() throws IOException;

  /** @return version of application data stored in storage -- managed by application */
  int getDataFormatVersion() throws IOException;

  void setDataFormatVersion(int expectedVersion) throws IOException;

  /**
   * @return max size of a record this storage could store.
   * It is guaranteed storage could store record of that size, but not larger
   */
  int maxPayloadSupported();

  boolean hasRecord(int recordId) throws IOException;

  boolean hasRecord(int recordId,
                    @Nullable IntRef redirectToIdRef) throws IOException;

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
  <Out> Out readRecord(int recordId,
                       @NotNull ByteBufferReader<Out> reader,
                       @Nullable IntRef redirectToIdRef) throws IOException;

  default <Out> Out readRecord(int recordId,
                               @NotNull ByteBufferReader<Out> reader) throws IOException {
    return readRecord(recordId, reader, /*redirectToIdRef: */null);
  }


  /**
   * Writer is called with writeable ByteBuffer represented current record content (payload).
   * Buffer is prepared for read: position=0, limit=payload.length, capacity=[current record capacity].
   * <br> <br>
   * Writer is free to read and/or modify the buffer, and return it in an 'after puts' state, i.e.
   * position=[#last byte of payload], new payload content = buffer[0..position].
   * <br> <br>
   * NOTE: this implies that even if the writer writes nothing, only reads -- it must set
   * buffer.position=limit, because otherwise storage will treat it as if record should be set length=0
   * To avoid this complication, if the writer changes nothing, it could return null.
   * <br> <br>
   * Capacity: if new payload fits into buffer passed in -> it could be written right into it. If the
   * new payload requires more space, the writer should allocate its own buffer with enough capacity,
   * write new payload into it, and return that buffer (in a 'after puts' state), instead of buffer
   * passed in. Storage will re-allocate space for the record with capacity >= returned buffer capacity.
   *
   * @param expectedRecordSizeHint          hint to a storage about how big data the writer intends to
   *                                        write. May be used for allocating buffer of that size.
   *                                        value <=0 means 'no hints, use default buffer allocation
   *                                        strategy'
   * @param leaveRedirectOnRecordRelocation if current record is relocated during writing, old record
   *                                        could be either removed right now, or left as 'redirect-to'
   *                                        record, so new content could still be accesses via old
   *                                        recordId.
   */
  int writeToRecord(int recordId,
                    @NotNull ByteBufferWriter writer,
                    int expectedRecordSizeHint,
                    boolean leaveRedirectOnRecordRelocation) throws IOException;

  default int writeToRecord(int recordId,
                            @NotNull ByteBufferWriter writer,
                            int expectedRecordSizeHint) throws IOException {
    return writeToRecord(recordId, writer, expectedRecordSizeHint, /* leaveRedirectOnRecordRelocation: */ false);
  }

  default int writeToRecord(int recordId,
                            @NotNull ByteBufferWriter writer) throws IOException {
    return writeToRecord(recordId, writer, /*expectedRecordSizeHint: */ -1);
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

  void deleteRecord(int recordId) throws IOException;

  /**
   * Scan all records (even deleted one), and deliver their content to processor. ByteBuffer is read-only, and
   * prepared for reading (i.e. position=0, limit=payload.length). For deleted/moved records recordLength is negative
   * see {@link #isRecordActual(int)}.
   * Scanning stops prematurely if processor returns false.
   *
   * @return how many records were processed
   */
  <E extends Exception> int forEach(@NotNull Processor<E> processor) throws IOException, E;

  boolean isRecordActual(int recordActualLength);

  int liveRecordsCount() throws IOException;

  /**
   * Total size of data in a storage -- including metadata, reserved and deleted
   * records.
   * Not guaranteed to be == actual file size on disk -- disk file could be pre-allocated
   * in advance.
   */
  long sizeInBytes() throws IOException;

  @Override
  boolean isDirty();

  @Override
  void force() throws IOException;

  @Override
  void close() throws IOException;

  boolean isClosed();


  interface Processor<E extends Exception> {
    boolean processRecord(int recordId,
                          int recordCapacity,
                          int recordLength,
                          @NotNull ByteBuffer payload) throws IOException, E;
  }
}
