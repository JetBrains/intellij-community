// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.*;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Append-only log: records are written once and never overwritten, each record identified by unique
 * recordId:long
 * Thread-safety properties are implementation-specific.
 */
@ApiStatus.Internal
public interface AppendOnlyLog extends Closeable {
  /** @return id of appended record */
  long append(@NotNull ByteBufferWriter writer,
              int recordSize) throws IOException;

  default long append(byte[] data) throws IOException {
    return append(buffer -> buffer.put(data), data.length);
  }

  <T> T read(long recordId,
             @NotNull ByteBufferReader<T> reader) throws IOException;

  /**
   * @return true if all the records were read, false if reader stops the reading prematurely (by
   * returning false from {@link RecordReader#read(long, ByteBuffer)} method )
   */
  boolean forEachRecord(@NotNull RecordReader reader) throws IOException;

  @Override
  void close() throws IOException;

  interface RecordReader {
    /** @return true if reading should continue, false to stop the reading */
    boolean read(long recordId,
                 ByteBuffer buffer) throws IOException;
  }
}
