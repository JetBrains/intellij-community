// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.circular;

import com.intellij.util.io.blobstorage.ByteBufferWriter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Circular query of byte[]-entries (technically, {@linkplain ByteBuffer} is used to represent the bytes).
 * <p>
 * Entries added to the queue by {@linkplain #append(ByteBuffer)} method. if there is no space for the new entry,
 * {@linkplain #append(ByteBuffer)} throws {@linkplain QueueFullException}.
 * <p>
 * Entries could be read by {@linkplain #read(DataReader)} without consuming them, or by
 * {@linkplain #readConsuming(DataReader)} and marked as processed after the reader returns normally.
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

  /** Deliver not-yet-processed entries to the reader, without marking them processed (consumed). */
  default void read(@NotNull DataReader reader) throws IOException {
    readMaybeConsuming(_ -> ReadDecision.readBy(reader));
  }

  /**
   * Deliver not-yet-processed entries to the consumer.
   * If the reader returns normally -- mark the entry 'processed' (=consumed).
   * Processed entries will not be delivered to the reader anymore in the subsequent calls to this method.
   * 'Consumption' is atomic and only-once: only one reader can mark a record consumed.
   *
   * @return # of entries consumed
   */
  default int readConsuming(@NotNull DataReader consumer) throws IOException {
    return readMaybeConsuming(_ -> ReadDecision.consumeBy(consumer));
  }

  /// Delivers not-yet-processed entries to the reader, in append FIFO-order to ( read | read-n-consume | skip )
  ///
  /// A record for which the reader returns [ReadDecision#readBy(DataReader)]/[ReadDecision#consumeBy(DataReader)] is a
  /// barrier: this read operation must not process later records until this record has either been processed by this
  /// operation or observed as already consumed (by concurrent operation from other thread).
  ///
  /// [ReadDecision#skip()] is the only decision that may let this read operation pass a record without waiting for its
  /// processing -> callers may return [ReadDecision#skip()] only for records that are safe to overtake.
  ///
  /// At most one [ReadDecision#process(ByteBuffer)] callback may run for a logical record at a time.
  /// A [ReadDecision#consumeBy(DataReader)] record is marked consumed only after [ReadDecision#process(ByteBuffer)]
  /// returns normally (without exception).
  int readMaybeConsuming(@NotNull OptionallyConsumingDataReader reader) throws IOException;

  interface DataReader {
    void read(@NotNull ByteBuffer entryData);
  }

  /// The most low-level record processing interface.
  /// Processes the records in 2 phases: 'decide', and (optional) 'process' (read or consume).
  /// 1. [decide]: should very quickly decide is it interested in this record?
  ///    If it is not interested in the record at all -> return [ReadDecision#skip()]
  ///    If it wants to stop the processing -> return [ReadDecision#stop()]
  /// 2. if it is interested in _reading_ (not consuming) the record -> return [ReadDecision#readBy(DataReader)]
  /// 3. if it is interested in _consuming_ the record -> return [ReadDecision#consumeBy(DataReader)]
  /// 4. if it wants to stop the current scan before processing the record -> return [ReadDecision#stop()]
  ///
  /// Why such a complication: to be able to get an optimal tradeoff between concurrency level and consistency
  /// guarantees. We want:
  /// 1. Support a parallel processing from many threads with as little contention between them as possible.
  /// 2. Maintain the consistency guarantees, like:
  ///    - 'all records are always accessed in a FIFO order';
  ///    - 'a record could be consumed only-once';
  ///    - 'a record can't be read after it was consumed';
  /// Fine-graned (and complex) API is needed to get the best tradeoff for a specific use case.
  /// E.g.: to maintain 'only-once' semantics for record consuming and avoid reading already-consumed record
  /// -- some sort of (per-record) mutex is needed to guarantee exclusive access to the record. But because all
  /// the threads must access the records in a FIFO order, such a mutex would delay _all_ the threads iterating
  /// over the buffer records, even the threads that have no interest at all in specific record guarded by the
  /// mutex -- because those threads still need accessing the record content to decide that they don't need it.
  /// 2-phases processing solves that: [decide] allows for quick skipping of non-interesting records, without
  /// acquiring the per-record mutex (or whatever mechanism plays its role), and only request exclusive access
  /// to the records that are (potentially) of our interest -- but at the cost of more complex API.
  interface OptionallyConsumingDataReader {
    /// Chooses how to handle a record before the buffer decides whether it needs an exclusive lease for this reader.
    /// Implementations must not perform I/O or irreversible side effects here.
    @NotNull ReadDecision decide(@NotNull ByteBuffer entryData);
  }

  /// That to do with the record: skip | stop | read | read-and-consume
  final class ReadDecision {
    private static final @NotNull ReadDecision SKIP = new ReadDecision(null, /* consumeAfter: */ false, /* stop: */ false);
    private static final @NotNull ReadDecision STOP = new ReadDecision(null, /* consumeAfter: */ false, /* stop: */ true);

    private final @Nullable DataReader processor;
    private final boolean consumeAfterProcess;
    private final boolean stop;

    private ReadDecision(@Nullable DataReader processor,
                         boolean consumeAfterProcess,
                         boolean stop) {
      this.processor = processor;
      this.consumeAfterProcess = consumeAfterProcess;
      this.stop = stop;
    }

    public boolean shouldStop() {
      return stop;
    }

    public boolean shouldProcess() {
      return processor != null;
    }

    public boolean shouldConsumeAfterProcess() {
      return consumeAfterProcess;
    }

    public void process(@NotNull ByteBuffer entryData) {
      if (processor == null) {
        throw new IllegalStateException("Decision without a processor can't process records");
      }
      processor.read(entryData);
    }

    /// Ignores the current record without leasing it and continues scanning later records.
    public static @NotNull ReadDecision skip() {
      return SKIP;
    }

    /// Stops this scan before leasing the current record, preserving it and later records for a later read.
    public static @NotNull ReadDecision stop() {
      return STOP;
    }

    /// Leases and reads the current record but keeps it unconsumed, making it a FIFO barrier for this reader.
    public static @NotNull ReadDecision readBy(@NotNull DataReader reader) {
      return new ReadDecision(Objects.requireNonNull(reader), /* consumeAfter: */ false, /*stopAfter: */ false);
    }

    /// Leases and reads the current record; a normal reader return marks the record consumed.
    public static @NotNull ReadDecision consumeBy(@NotNull DataReader consumer) {
      return new ReadDecision(Objects.requireNonNull(consumer), /* consumeAfter: */ true, /*stopAfter: */ false);
    }
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
