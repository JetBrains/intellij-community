// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.appendonlylog;

import com.intellij.util.io.CleanableStorage;
import com.intellij.util.io.blobstorage.ByteBufferWriter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Chunked append-only log: log consists of chunks, and each chunk is an append-only log itself.
 */
@ApiStatus.Internal
public interface ChunkedAppendOnlyLog extends Closeable, Flushable, CleanableStorage {
  interface LogChunk {

    long id();

    /** payload capacity of the chunk */
    int capacity();

    boolean isFull();

    int remaining();

    /** @return true if record of recordSize is appended, false if there is no room remains in the chunk for recordSize */
    boolean append(@NotNull ByteBufferWriter writer,
                   int recordSize) throws IOException;

    //MAYBE RC: better wrap reading into lambda? Otherwise ByteBuffer (slice over memory-mapped region) could be spread
    //          uncontrollable through the app
    ByteBuffer read() throws IOException;
  }

  LogChunk append(int chunkSize) throws IOException;


  LogChunk read(long chunkId) throws IOException;

  /**
   * @return true if all the records were read, false if reader stops the reading prematurely (by
   * returning false from {@link ChunkReader#read(LogChunk)} method )
   */
  boolean forEachChunk(@NotNull ChunkReader reader) throws IOException;

  @Override
  void close() throws IOException;

  @Override
  void flush() throws IOException;

  /** @return true if there are no records in the log */
  boolean isEmpty();

  /** @return number of records appended to the log */
  int chunksCount() throws IOException;

  interface ChunkReader {
    /** @return true if reading should continue, false to stop the reading */
    boolean read(@NotNull LogChunk chunk) throws IOException;
  }
}
