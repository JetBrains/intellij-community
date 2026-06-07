// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.writeaheadlog;

import com.intellij.util.io.blobstorage.ByteBufferWriter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * Write-ahead log:
 * - Writes are postponed, to be applied to the actual file in the background.
 * - Postponed writes are kept in some persistent buffer, so even if the IDE crushes -- the writes
 * are not lost, they could be still applied on IDE restart.
 */
@ApiStatus.Internal
public interface WriteAheadLog extends Closeable {

  @NotNull PerFileWriter openFor(@NotNull Path file) throws IOException;

  boolean hasUnfinished();

  /** @return # of writes flushed */
  int flush() throws IOException;

  @Override
  void close() throws IOException;

  interface PerFileWriter {
    default void write(long fileOffset,
                       byte @NotNull [] data,
                       int dataOffset,
                       int dataLength) throws IOException {
      write(fileOffset, target -> target.put(data, dataOffset, dataLength), dataLength);
    }

    void write(long fileOffset,
               @NotNull ByteBufferWriter writer,
               int recordSize) throws IOException;

    /**
     * Applies all yet-unfinished writes in the [offsetInFile, length) range to the buffer, starting from offsetInBuffer.
     * (I.e., offsetInFile corresponds to the offsetInBuffer)
     * Applied writes are not 'consumed' -- they remain in WAL until flush()-ed
     */
    void applyUnfinished(long offsetInFile,
                         int length,
                         @NotNull ByteBuffer targetBuffer,
                         int offsetInBuffer);

    /** @return true if there are not-yet-applied writes in the buffer */
    boolean hasUnfinished();

    /**
     * @return maximum exclusive end offset (={@code fileOffset + recordSize}) among unfinished writes for this file,
     * or {@code -1} if there are no unfinished writes.
     * This method _does not_ consume unfinished writes.
     */
    long maxUnfinishedWriteOffset() throws IOException;

    /** @return # of writes flushed */
    int flush() throws IOException;
  }

  interface ToFileWriter {
    /**
     * Writes a given data buffer into the file(path), at offsetInFile offset.
     * Data buffer is passed in a 'ready-to-put' state: `[position..limit]` is the range to
     * be written.
     * BEWARE: data buffer could be re-usable => the writer should not use the
     * buffer in any way after the write() call finishes.
     */
    void write(@NotNull Path path,
               long offsetInFile,
               @NotNull ByteBuffer data) throws IOException;
  }
}
