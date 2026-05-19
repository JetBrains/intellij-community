// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.circular;

import com.intellij.util.io.blobstorage.ByteBufferWriter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Circular query of byte[]-entries (technically, {@linkplain ByteBuffer} is used to represent the bytes).
 * <p>
 * Entries added to the queue by {@linkplain #append(ByteBuffer)} method. if there is no space for the new entry,
 * {@linkplain #append(ByteBuffer)} throws {@linkplain QueueFullException}.
 * <p>
 * Entries could be read by {@linkplain #read(DataReader)} -- either "read and keep the entry in queue" or "read and mark
 * the entry processed" (which effectively removes the entry from the queue, even though technically it may be just marked
 * as 'consumed')
 * <p>
 */
@ApiStatus.Internal
public interface CircularBytesBuffer extends Closeable, Flushable {

  boolean hasUnprocessedRecords() throws IOException;

  /** @return maximum entry size that can this buffer could store */
  int maxEntrySize();

  default void append(byte[] data, int offset, int length) throws IOException, QueueFullException {
    append(target -> target.put(data, offset, length),
           length);
  }

  default void append(@NotNull ByteBuffer data) throws IOException, QueueFullException {
    append(target -> target.put(data),
           data.remaining());
  }

  /**
   * The writer will be passed in a buffer with `(limit-position=entrySize)`.
   * The writer must fill the buffer with data and return the buffer, with `position=limit`. If the writer doesn't fill up
   * whole entrySize bytes in the buffer -- {@linkplain IllegalStateException} is thrown
   *
   * @param entrySize must not exceed {@linkplain #maxEntrySize()} or {@linkplain IllegalArgumentException} is thrown
   */
  void append(@NotNull ByteBufferWriter writer,
              int entrySize) throws IOException, QueueFullException;

  /**
   * Deliver not-yet-processed entries to the reader.
   * If the reader returns true -- mark the entry 'processed' (=consumed), otherwise leave it unprocessed.
   * Processed entries will not be delivered to the reader anymore in the subsequent calls to this method.
   *
   * @return # of entries consumed
   */
  int read(@NotNull DataReader reader) throws IOException;

  interface DataReader {
    /** @return true to mark the entry as processed (=consumed), false to keep the entry unprocessed */
    boolean read(@NotNull ByteBuffer entryData);
  }

  /** Thrown by append-methods when there is no free space left in the queue for the new entry. */
  @SuppressWarnings("unused")
  class QueueFullException extends Exception {
    public QueueFullException() {
    }

    public QueueFullException(String message) {
      super(message);
    }

    public QueueFullException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
