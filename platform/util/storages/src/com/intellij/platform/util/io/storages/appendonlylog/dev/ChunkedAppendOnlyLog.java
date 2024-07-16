// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.appendonlylog.dev;

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
 * <p>
 * There are 2 types of chunks: 'fixed' and 'appendable' (planned - 'fixed' type is not yet implemented)
 * For both chunks types data is immutable since put in -- no modification of data is allowed.
 * The difference is that 'appendable' chunk allows appending _new_ data to the chunk, up to capacity -- while with 'fixed'
 * chunk all the data must be supplied on chunk allocation, and nothing could be appended later on.
 * <p>
 * 'Appendable' chunks reduce storage overhead for many small records -- instead of appending each such a record in a dedicated
 * chunk, with full header, one could append many small records into a single chunk, with a single header overhead only.
 * It is also reduced the fragmentation if many small records appended not at once, together, but incrementally, one-by-one
 * -- instead of 'copy-with-append' to the new chunk on each appended record, those small records could be accumulated in one
 * chunk.
 * <p>
 * The downside is that max capacity of 'appendable' chunk is much smaller -- more fields needs to be fit into a header to
 * support append-ability, so less bits remains for a max length. Actual max capacity is implementation-dependent
 */
@ApiStatus.Internal
public interface ChunkedAppendOnlyLog extends Closeable, Flushable, CleanableStorage {

  int NULL_ID = 0;

  interface LogChunk {

    long id();

    /** payload capacity of the chunk */
    int capacity();

    /** Has this chunk .nextChunkId field reserved (allocated) at creation */
    boolean hasNextChunkIdField();

    /**
     * @return nextChunkId if set, or NULL_ID (=0) if not yet..
     * @throws IllegalStateException if {@link #hasNextChunkIdField()} is false (i.e. there
     *                               is no nextChunkId field reserved in the chunk at creation)
     */
    long nextChunkId();

    /**
     * Sets nextChunkId if not set yet.
     * nextChunkId could be set only once -- following attempts do not change the nextChunkId value, and return false to
     * indicate that
     * FiXME: much better to move to locking-based approach, instead of CAS-like
     *
     * @return true if nextChunkId was set, false if it was already set
     * @throws IllegalStateException if {@link #hasNextChunkIdField()} is false (i.e. there
     *                               is no nextChunkId field reserved in the chunk at creation)
     */
    boolean nextChunkId(long nextChunkId);


    /**
     * @return true if the chunk is appendable -- i.e. data could be {@link #append(ByteBufferWriter, int)} to it,
     * or false, if chunk is non-appendable -- i.e. all the data is appended on chunk allocation, and immutable after
     * that.
     * If chunk is 'appendable' it doesn't mean it still has free room for new data to append -- chunk could be
     * appendable in principle, but already full now -- use {@link #remaining()}/{@link #isFull()} to check that.
     */
    boolean isAppendable();

    boolean isFull();

    int remaining();

    /** @return true if record of recordSize is appended, false if there is no room remains in the chunk for recordSize */
    boolean append(@NotNull ByteBufferWriter writer,
                   int recordSize) throws IOException;


    /**
     * @return buffer over data appended to the chunk so far -- i.e. spare room, if exist, not
     * included.
     * MAYBE RC: better wrap reading into lambda? Otherwise ByteBuffer (which is == slice over memory-mapped region) could
     *           be spread uncontrollable through the app
     */
    ByteBuffer read() throws IOException;
  }


  default LogChunk append(int chunkSize) throws IOException {
    return append(chunkSize, /*reserveNextChunkIdField: */ false);
  }

  LogChunk append(int chunkPayloadCapacity,
                  boolean reserveNextChunkIdField) throws IOException;
  //TODO RC: 'fixed-size' (non-appendable) chunk
  //TODO RC: public long append(int chunkSize, ByteBufferWriter writer) throws IOException;

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
