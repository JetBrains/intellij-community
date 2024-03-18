// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.dev.StorageFactory;
import com.intellij.util.io.dev.appendonlylog.ChunkedAppendOnlyLog.LogChunk;
import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

public class ChunkedAppendOnlyLogOverMMappedFileTest {

  private static final int MAX_CAPACITY = ChunkedAppendOnlyLogOverMMappedFile.MAX_PAYLOAD_SIZE;
  private static final int ENOUGH_CHUNKS_TO_CHECK = 500_000;

  private static final @NotNull StorageFactory<ChunkedAppendOnlyLogOverMMappedFile> LOG_FACTORY = MMappedFileStorageFactory
    .withDefaults()
    .pageSize(64 << 10)
    .compose(ChunkedAppendOnlyLogOverMMappedFile::new);

  private Path pathToLog;
  private ChunkedAppendOnlyLogOverMMappedFile log;


  @Test
  void exampleOfUse() throws IOException {
    int requestedCapacity = 1024;

    LogChunk chunk = log.append(requestedCapacity);
    assertTrue(chunk.id() > 0, "chunk.id must be >0");

    int actualCapacity = chunk.capacity();
    assertTrue(actualCapacity >= requestedCapacity, "Chunk actual capacity(=" + chunk.capacity() + ") must be not less than requested");

    {
      LogChunk chunkReRead = log.read(chunk.id());
      assertEquals(chunk.id(), chunkReRead.id(), "Chunks ids must be same");

      assertEquals(chunk.capacity(), chunkReRead.capacity(), "Chunks capacities must be same");
    }

    {
      boolean appended = chunk.append(buffer -> buffer.putInt(42), 4);
      assertTrue(appended, "Append must succeed since single int32 fits capacity");
      assertEquals(42, chunk.read().getInt(), "Value written is available to read back");
    }

    {
      boolean appended = chunk.append(buffer -> buffer.putInt(43), 4);
      assertTrue(appended, "Append must succeed since 2x int32 fits capacity");
      ByteBuffer buffer = chunk.read();
      assertEquals(42, buffer.getInt(), "Both values written are available to read back");
      assertEquals(43, buffer.getInt(), "Both values written are available to read back");
    }

    {
      int aboveCapacity = actualCapacity - 8 + 1;

      boolean appended = chunk.append(buffer -> buffer.put(new byte[aboveCapacity]), aboveCapacity);
      assertFalse(appended, "Append must NOT succeed since " + aboveCapacity + " NOT fit capacity anymore");

      ByteBuffer buffer = chunk.read();
      assertEquals(8, buffer.remaining(), "Chunk content is untouched: 2x int32 remains");
      assertEquals(42, buffer.getInt(), "Chunk content is untouched: 2x int32 remains");
      assertEquals(43, buffer.getInt(), "Chunk content is untouched: 2x int32 remains");
    }

    {
      int fillUpCapacity = actualCapacity - 2 * 4;

      boolean appended = chunk.append(buffer -> buffer.put(new byte[fillUpCapacity]), fillUpCapacity);
      assertTrue(appended, "Append must succeed since 1024 fits capacity _exactly_");
      assertTrue(chunk.isFull(), "Chunk is full, capacity is filled up");

      ByteBuffer buffer = chunk.read();
      assertEquals(actualCapacity, buffer.remaining(), "All the capacity is now used");
      assertEquals(42, buffer.getInt(), "Chunk content is untouched: 2x int32 written before remains as-is");
      assertEquals(43, buffer.getInt(), "Chunk content is untouched: 2x int32 written before remains as-is");
    }
  }

  @Test
  void appendedChunkCapacity_IsNotLessThanRequested() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < ENOUGH_CHUNKS_TO_CHECK; i++) {
      int capacity = rnd.nextInt(1, MAX_CAPACITY);
      LogChunk chunk = log.append(capacity);
      assertTrue(
        chunk.capacity() >= capacity,
        "Chunk capacity(=" + chunk.capacity() + ") must be not less than requested"
      );

      assertTrue(chunk.id() > 0, "chunk.id must be >0");
      assertFalse(chunk.isFull(), "chunk must not be full, if just appended");
    }
  }

  @Test
  void appendedChunksIds_AreMonotonicallyIncreases() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    long lastId = 0;
    for (int i = 0; i < ENOUGH_CHUNKS_TO_CHECK; i++) {
      int capacity = rnd.nextInt(1, MAX_CAPACITY);
      LogChunk chunk = log.append(capacity);
      long id = chunk.id();
      assertTrue(
        id > lastId,
        "chunk.id(" + id + ") must be > previous id(" + lastId + ")"
      );
      lastId = id;
    }

    assertEquals(ENOUGH_CHUNKS_TO_CHECK, log.chunksCount(),
                 "log.chunksCount() report exact number of chunks appended");
  }

  @Test
  void valuesAppendedToChunks_CouldBeReadBack_Immediately() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < ENOUGH_CHUNKS_TO_CHECK; i++) {
      int capacity = rnd.nextInt(Long.BYTES, MAX_CAPACITY);
      LogChunk chunk = log.append(capacity);

      long valueWritten = chunk.id();
      boolean appended = chunk.append(buffer -> buffer.putLong(valueWritten), Long.BYTES);
      assertTrue(appended, ".append() must succeed because capacity is > size appended");


      ByteBuffer readBuffer = chunk.read();
      assertEquals(readBuffer.position(), 0,
                   "readBuffer position must be 0");
      assertEquals(readBuffer.limit(), Long.BYTES,
                   "readBuffer limit must be 8 as we appended 8 bytes before");

      long valueReadBack = readBuffer.getLong();
      assertEquals(valueWritten, valueReadBack,
                   "Value readBack must be the same as was just written");
    }
    assertEquals(ENOUGH_CHUNKS_TO_CHECK, log.chunksCount(),
                 "log.chunksCount() report exact number of chunks appended");
  }

  @Test
  void valuesAppendedToChunks_CouldBeReadBack_AfterAWhile() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    long[] chunkIds = new long[ENOUGH_CHUNKS_TO_CHECK];
    for (int i = 0; i < ENOUGH_CHUNKS_TO_CHECK; i++) {
      int capacity = rnd.nextInt(Long.BYTES, MAX_CAPACITY);
      LogChunk chunk = log.append(capacity);

      chunkIds[i] = chunk.id();

      long valueWritten = chunk.id();
      boolean appended = chunk.append(buffer -> buffer.putLong(valueWritten), Long.BYTES);
      assertTrue(appended, ".append() must succeed because capacity is > size appended");
    }

    assertEquals(ENOUGH_CHUNKS_TO_CHECK, log.chunksCount(),
                 "log.chunksCount() report exact number of chunks appended");

    for (long chunkId : chunkIds) {
      LogChunk chunk = log.read(chunkId);

      long valueWritten = chunk.id();

      ByteBuffer readBuffer = chunk.read();
      assertEquals(readBuffer.position(), 0,
                   "readBuffer position must be 0");
      assertEquals(readBuffer.limit(), Long.BYTES,
                   "readBuffer limit must be 8 as we appended 8 bytes before");

      long valueReadBack = readBuffer.getLong();
      assertEquals(valueWritten, valueReadBack,
                   "Value readBack must be the same as was just written");
    }
  }

  @Test
  void valuesAppendedToChunks_CouldBeReadBack_AfterReopen() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    long[] chunkIds = new long[ENOUGH_CHUNKS_TO_CHECK];
    for (int i = 0; i < ENOUGH_CHUNKS_TO_CHECK; i++) {
      int capacity = rnd.nextInt(Long.BYTES, MAX_CAPACITY);
      LogChunk chunk = log.append(capacity);

      chunkIds[i] = chunk.id();

      long valueWritten = chunk.id();
      boolean appended = chunk.append(buffer -> buffer.putLong(valueWritten), Long.BYTES);
      assertTrue(appended, ".append() must succeed because capacity is > size appended");
    }

    assertEquals(ENOUGH_CHUNKS_TO_CHECK, log.chunksCount(),
                 "log.chunksCount() report exact number of chunks appended");

    reopenLog();

    assertEquals(ENOUGH_CHUNKS_TO_CHECK, log.chunksCount(),
                 "log.chunksCount() keep exact number of chunks appended after reopen");

    for (long chunkId : chunkIds) {
      LogChunk chunk = log.read(chunkId);

      long valueWritten = chunk.id();

      ByteBuffer readBuffer = chunk.read();
      assertEquals(readBuffer.position(), 0,
                   "readBuffer position must be 0");
      assertEquals(readBuffer.limit(), Long.BYTES,
                   "readBuffer limit must be 8 as we appended 8 bytes before");

      long valueReadBack = readBuffer.getLong();
      assertEquals(valueWritten, valueReadBack,
                   "Value readBack must be the same as was just written");
    }
  }


  @Test
  void filledUpChunks_CouldBeReadBack_AfterReopen() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    long[] chunkIds = new long[ENOUGH_CHUNKS_TO_CHECK];
    byte[][] chunkValues = new byte[ENOUGH_CHUNKS_TO_CHECK][];
    for (int i = 0; i < ENOUGH_CHUNKS_TO_CHECK; i++) {
      int capacity = rnd.nextInt(Long.BYTES, MAX_CAPACITY);
      LogChunk chunk = log.append(capacity);

      byte[] value = new byte[capacity];
      rnd.nextBytes(value);

      boolean appended = chunk.append(buffer -> buffer.put(value), capacity);
      assertTrue(appended, ".append() must succeed because capacity = size appended");

      chunkIds[i] = chunk.id();
      chunkValues[i] = value;
    }

    reopenLog();

    for (int i = 0; i < chunkIds.length; i++) {
      long chunkId = chunkIds[i];
      byte[] valueWritten = chunkValues[i];
      LogChunk chunk = log.read(chunkId);

      ByteBuffer readBuffer = chunk.read();
      assertEquals(readBuffer.position(), 0,
                   "readBuffer.position must be 0");
      assertEquals(readBuffer.limit(), valueWritten.length,
                   "readBuffer.limit must the length of value written");

      byte[] valueReadBack = new byte[readBuffer.remaining()];
      readBuffer.get(valueReadBack);
      assertArrayEquals(valueWritten, valueReadBack,
                        "Value readBack must be the same as was just written");
    }
  }


  @Test
  void filledUpChunks_CouldBeReadBack_viaForEach_AfterReopen() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    Long2ObjectMap<byte[]> idToValue = new Long2ObjectOpenHashMap<>(ENOUGH_CHUNKS_TO_CHECK);
    for (int i = 0; i < ENOUGH_CHUNKS_TO_CHECK; i++) {
      int capacity = rnd.nextInt(Long.BYTES, MAX_CAPACITY);
      LogChunk chunk = log.append(capacity);

      byte[] value = new byte[capacity];
      rnd.nextBytes(value);

      boolean appended = chunk.append(buffer -> buffer.put(value), capacity);
      assertTrue(appended, ".append() must succeed because capacity = size appended");

      idToValue.put(chunk.id(), value);
    }

    reopenLog();

    log.forEachChunk(chunk -> {
      long id = chunk.id();
      byte[] valueWritten = idToValue.remove(id);
      assertNotNull(valueWritten,
                    "value for id: " + id + " wasn't written, but reported by .forEachChunk()");

      ByteBuffer readBuffer = chunk.read();
      assertEquals(readBuffer.position(), 0,
                   "readBuffer.position must be 0");
      assertEquals(readBuffer.limit(), valueWritten.length,
                   "readBuffer.limit must the length of value written");

      byte[] valueReadBack = new byte[readBuffer.remaining()];
      readBuffer.get(valueReadBack);
      assertArrayEquals(valueWritten, valueReadBack,
                        "Value readBack must be the same as was just written");

      return true;
    });

    assertTrue(
      idToValue.isEmpty(),
      "All chunks written must be reported by .forEach, but following was missed: \n" + idToValue
    );
  }

  @Test
  void chunksFilledUp_MultiThreaded_CouldBeReadBack_AfterReopen() throws Exception {
    int threadsCount = Runtime.getRuntime().availableProcessors() / 2;
    int itemsPerThread = 7;
    int chunksCount = 100_000;//make size less than default to not trigger OoM
    Chunk[] chunkAppended = appendChunks_MultiThreaded(chunksCount, threadsCount, itemsPerThread);

    reopenLog();

    for (Chunk chunk : chunkAppended) {
      LogChunk logChunk = log.read(chunk.chunkId);

      IntBuffer intBuffer = logChunk.read().asIntBuffer();
      int[] itemsReadBack = new int[intBuffer.remaining()];
      intBuffer.get(itemsReadBack);

      assertEquals(
        itemsReadBack.length,
        chunk.itemsToAppend.length,
        ""
      );
      assertEquals(
        new IntArraySet(itemsReadBack),
        new IntArraySet(chunk.itemsToAppend)
      );
    }


  }


  // =========================== infrastructure: ============================================================================= //


  private Chunk[] appendChunks_MultiThreaded(int chunksCount,
                                             int threadsCount,
                                             int maxRecordsToWritePerThread) throws InterruptedException, IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    Chunk[] chunks = IntStream.range(0, chunksCount)
      .mapToObj(i -> {
        int perThreadItems = rnd.nextInt(1, maxRecordsToWritePerThread);
        int itemsCount = perThreadItems * threadsCount;
        int[] items = rnd.ints(itemsCount).toArray();
        return new Chunk(-1, items);
      })
      .toArray(Chunk[]::new);

    for (Chunk chunk : chunks) {
      LogChunk appended = log.append(chunk.itemsToAppend.length * Integer.BYTES);
      chunk.chunkId = appended.id();
    }

    ExecutorService pool = Executors.newFixedThreadPool(threadsCount);
    try {
      for (int threadNo = 0; threadNo < threadsCount; threadNo++) {
        int finalThreadNo = threadNo;
        pool.submit((Callable<Void>)() -> {
          for (int i = 0; i < chunks.length; i++) {
            Chunk chunk = chunks[i];
            LogChunk logChunk = log.read(chunk.chunkId);
            int[] items = chunk.itemsToAppend;
            for (int j = finalThreadNo; j < items.length; j += threadsCount) {
              int item = items[j];
              logChunk.append(buffer -> buffer.putInt(item), Integer.BYTES);
            }
          }
          return null;
        });
      }
      return chunks;
    }
    finally {
      pool.shutdown();
      pool.awaitTermination(10, SECONDS);
    }
  }

  private static class Chunk {
    private long chunkId;
    private int[] itemsToAppend;

    private Chunk(long chunkId, int[] itemsToAppend) {
      this.chunkId = chunkId;
      this.itemsToAppend = itemsToAppend;
    }
  }

  @BeforeEach
  void setup(@TempDir Path tempDir) throws IOException {
    pathToLog = tempDir.resolve("log");
    log = LOG_FACTORY.open(pathToLog);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (log != null) {
      log.closeAndClean();
    }
  }

  private void reopenLog() throws IOException {
    log.close();
    log = LOG_FACTORY.open(pathToLog);
  }
}
