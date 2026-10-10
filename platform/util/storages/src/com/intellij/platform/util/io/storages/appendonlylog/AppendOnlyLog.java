// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.appendonlylog;

import com.intellij.util.io.CleanableStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/**
 * Append-only log: records are written once and never overwritten, each record identified by unique
 * recordId:long
 * Thread-safety properties are implementation-specific.
 */
@ApiStatus.Internal
public interface AppendOnlyLog extends Closeable, Flushable, CleanableStorage {
  /** @return id of appended record */
  long append(@NotNull RecordWriter writer,
              int recordSize) throws IOException;

  /** Simplified version of {@link #append(RecordWriter, int)} -- appends data from byte[] */
  default long append(byte @NotNull [] data) throws IOException {
    return append(target -> MemorySegment.copy(data, 0, target, JAVA_BYTE, 0, data.length), data.length);
  }

  <T> T read(long recordId,
             @NotNull RecordReader<T> reader) throws IOException;

  /**
   * @return true if supplied id looks like valid id of existing record in a log, false otherwise.
   * returned false definitely means id is not valid -- but returned true means 'id _looks_ like a valid record id',
   * because some implementations can't say for sure is id a valid record id or not.
   */
  boolean isValidId(long id) throws IOException;

  /**
   * @return true if all the records were read, false if reader stops the reading prematurely (by
   * returning false from {@link RecordProcessor#process(long, MemorySegment)} method )
   */
  boolean forEachRecord(@NotNull RecordProcessor processor) throws IOException;

  @Override
  void close() throws IOException;

  @Override
  void flush() throws IOException;

  /** @return true if there are no records in the log */
  boolean isEmpty() throws IOException;

  /** @return number of records appended to the log */
  int recordsCount() throws IOException;

  /// Writes one record to an exact writable segment.
  interface RecordWriter {
    void write(@NotNull MemorySegment target) throws IOException;
  }

  /// Reads one record from an exact read-only segment.
  interface RecordReader<T> {
    T read(@NotNull MemorySegment data) throws IOException;
  }

  interface RecordProcessor {
    /** @return true if reading should continue, false to stop the reading */
    boolean process(long recordId,
                    @NotNull MemorySegment data) throws IOException;
  }
}
