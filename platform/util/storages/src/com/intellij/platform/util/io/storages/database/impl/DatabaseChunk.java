// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState;
import com.intellij.platform.util.io.storages.database.impl.layout.ChunkHeaderLayout;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.RETIRED;

/// Chunk is a fixed-size file mmapped as a single memory region: it is used to allocate [DatabaseBlock]s inside it.
/// [BlocksDatabaseImpl] consists of such chunks, while each chunk contains some (variable-size) [DatabaseBlock]s;
/// Chunk represents a **physical** unit of allocation/compaction -- while e.g., [DatabaseBlock] represents a **logical**
/// unit of data.
///
/// Chunk **lifecycle**:
/// - [ChunkState#ACTIVE]: a new chunk started in this state -- active chunk accepts (=allocates) blocks until it is not
///   (almost) exhausted. The database can keep more than one chunk active, and the decision when to seal the active
///   chunk is an implementation detail of blocks allocation strategy;
/// - [ChunkState#SEALED]: a chunk no longer accepts new blocks -- but existing blocks in the chunk are still valid,
///   and accessible for both read & write;
/// - [ChunkState#RETIRED]: if the chunk is sealed, and _all_ its blocks are [LifecycleState#RETIRED] => chunk itself could
///   be retired, which means it should NOT be accessed anymore, and could be unmapped and removed anytime;
///
/// **Thread-safety**: blocks allocation and chunk state transitions are thread-safe, protected by exclusive lock.
/// Access to the allocated blocks' memory segments is not protected -- allocating code is responsible for that, if needed.
final class DatabaseChunk implements Closeable, Flushable {

  private final @NotNull ChunkIdentity chunkId;
  private final int chunkSize;

  private final @NotNull MMappedFileStorage storage;
  /// Full chunk segment, including header
  private final @NotNull MemorySegment chunkSegment;

  private final transient @NotNull Object lock = new Object();
  /// GuardedBy(lock)
  private final List<DatabaseBlock> blocks;

  private DatabaseChunk(@NotNull MMappedFileStorage storage,
                        @NotNull MemorySegment chunkSegment,
                        @NotNull ChunkIdentity chunkId,
                        @NotNull List<DatabaseBlock> blocks,
                        int chunkSize) {
    this.storage = storage;
    this.chunkSegment = chunkSegment;
    this.chunkSize = chunkSize;
    this.chunkId = chunkId;
    this.blocks = blocks;
  }

  /// Creates a durable chunk before catalog registration
  static @NotNull DatabaseChunk create(@NotNull Path storagePath,
                                       long databaseId,
                                       int chunkId,
                                       int chunkSize) throws IOException {
    return create(storagePath, databaseId, chunkId, chunkSize, /*fsyncOnFlush: */ true);
  }

  static @NotNull DatabaseChunk create(@NotNull Path storagePath,
                                       long databaseId,
                                       int chunkId,
                                       int chunkSize,
                                       boolean fsyncOnFlush) throws IOException {
    validateExpectedFields(databaseId, chunkId, chunkSize);
    Files.createFile(storagePath);

    var identity = new ChunkIdentity(databaseId, chunkId);
    return mmappedFileFactory(chunkSize, fsyncOnFlush).wrapStorageSafely(storagePath, storage -> {
      var chunkMemorySegment = chunkMemorySegment(storage, chunkSize);
      ChunkHeaderLayout.initialize(chunkMemorySegment, databaseId, chunkId);
      storage.fsync();  // The chunk header must persist before the catalog publishes this chunk
      return new DatabaseChunk(
        storage,
        chunkMemorySegment,
        identity,
        /*blocks: */ new ArrayList<>(),
        Math.toIntExact(chunkMemorySegment.byteSize())
      );
    });
  }

  /// Opens an existing chunk. Rejects a file with an incompatible identity
  @SuppressWarnings("SameParameterValue")
  static @NotNull DatabaseChunk open(@NotNull Path storagePath,
                                     long expectedDatabaseId,
                                     int expectedChunkId,
                                     int expectedChunkSize,
                                     boolean fsyncOnFlush) throws IOException {
    return openWithRecovery(storagePath, expectedDatabaseId, expectedChunkId, expectedChunkSize, fsyncOnFlush).chunk();
  }

  static @NotNull DatabaseChunk.OpenChunkResult openWithRecovery(@NotNull Path storagePath,
                                                                 long expectedDatabaseId,
                                                                 int expectedChunkId,
                                                                 int expectedChunkSize,
                                                                 boolean fsyncOnFlush) throws IOException {
    if (!Files.exists(storagePath)) {
      throw new NoSuchFileException(storagePath.toString());
    }
    var actualFileSize = Files.size(storagePath);
    if (actualFileSize != expectedChunkSize) {
      throw new CorruptedException(
        "[" + storagePath + "]: chunk file size is " + actualFileSize + ", expected " + expectedChunkSize
      );
    }

    return mmappedFileFactory(expectedChunkSize, fsyncOnFlush).wrapStorageSafely(storagePath, storage -> {
      var mapping = chunkMemorySegment(storage, expectedChunkSize);
      ChunkHeaderLayout.validate(storagePath, mapping, expectedChunkSize);
      var chunkId = new ChunkIdentity(
        ChunkHeaderLayout.readDatabaseId(mapping),
        ChunkHeaderLayout.readChunkId(mapping)
      );
      if (chunkId.databaseId() != expectedDatabaseId) {
        throw new CorruptedException(
          "[" + storagePath + "]: databaseId(=" + chunkId.databaseId() + ") != expectedDatabaseId(=" + expectedDatabaseId + ")"
        );
      }
      if (chunkId.chunkId() != expectedChunkId) {
        throw new CorruptedException(
          "[" + storagePath + "]: chunkId(=" + chunkId.chunkId() + ") != expectedChunkId(=" + expectedChunkId + ")"
        );
      }
      var allocatedTail = ChunkHeaderLayout.readAllocatedTail(mapping);
      var committedTail = ChunkHeaderLayout.readCommittedTail(mapping);
      var blocks = recoverBlocks(storagePath, mapping, chunkId.chunkId(), committedTail);
      var tailBytesRolledBack = allocatedTail - committedTail;
      return new OpenChunkResult(
        new DatabaseChunk(storage, mapping, chunkId, blocks, expectedChunkSize),
        tailBytesRolledBack
      );
    });
  }

  /// The chunk and the recovery result from chunk-open operation
  /// (Should be a chunk's metrics/counter field, but I don't want to create the only counter in the chunk just
  /// for a constant value needed only on the open phase)
  record OpenChunkResult(@NotNull DatabaseChunk chunk, long tailBytesRolledBack) { }

  @NotNull DatabaseBlock allocateBlock(int blockId, int storeId, int blockRole, int blockLength) throws IOException {
    synchronized (lock) {
      if (state() != ChunkState.ACTIVE) {
        throw new IllegalStateException("Chunk " + chunkId.chunkId() + " does not accept new blocks");
      }

      var blockOffset = allocatedTail();
      if (blockOffset != committedTail()) {
        throw new IllegalStateException("Chunk " + chunkId.chunkId() + " has an unfinished block allocation");
      }
      DatabaseBlock.validateParameters(blockId, blockLength, storeId, blockRole);
      var blockEnd = blockOffset + blockLength;
      if (blockEnd > chunkSegment.byteSize()) {
        throw new IllegalArgumentException("The block does not fit in chunk " + chunkId.chunkId());
      }

      ChunkHeaderLayout.publishAllocatedTail(chunkSegment, blockEnd);
      var block = DatabaseBlock.create(chunkSegment, chunkId.chunkId(), blockOffset, blockId, blockLength, storeId, blockRole);
      blocks.add(block);
      ChunkHeaderLayout.publishCommittedTail(chunkSegment, blockEnd);
      return block;
    }
  }

  /// Copies a complete block (including block header) into this chunk;
  /// `origin`s chunk must be different and older than this, i.e.: `origin.chunk.chunkId < this.chunkId`;
  /// The caller must prevent changes to the origin block until this method returns.
  @NotNull DatabaseBlock copyBlock(@NotNull DatabaseBlock origin) throws IOException {
    synchronized (lock) {
      if (state() != ChunkState.ACTIVE) {
        throw new IllegalStateException("Chunk " + chunkId.chunkId() + " does not accept evacuated blocks");
      }

      if (chunkId.chunkId() <= origin.chunkId()) {
        // Important invariant: blocks evacuation interrupted in the middle leaves >1 block copies with same blockId.
        // For recovery, we need an ordering over those block copies -- to unambiguously determine which copy is the
        // most recent one. The ordering currently used is 'by chunkId': we always evacuate block into a different,
        // newer chunk => the block copy with highest chunkId is the most recent (=actual) one
        throw new IllegalArgumentException(
          "Target chunkId(=" + chunkId.chunkId() + ") must be newer than source chunkId(=" + origin.chunkId() + ")"
        );
      }

      var originState = origin.state();
      if (originState != LifecycleState.ACTIVE && originState != LifecycleState.SEALED) {
        throw new IllegalArgumentException("Only an active or sealed block can be evacuated: " + originState);
      }

      var blockOffset = allocatedTail();
      if (blockOffset != committedTail()) {
        throw new IllegalStateException("Chunk " + chunkId.chunkId() + " has an unfinished block allocation");
      }
      var blockEnd = blockOffset + origin.blockLength();
      if (blockEnd > chunkSegment.byteSize()) {
        throw new IllegalArgumentException("The block does not fit in chunk " + chunkId.chunkId());
      }

      ChunkHeaderLayout.publishAllocatedTail(chunkSegment, blockEnd);
      origin.copyTo(chunkSegment.asSlice(blockOffset, origin.blockLength()));
      var copy = DatabaseBlock.open(storagePath(), chunkSegment, chunkId.chunkId(), blockOffset, blockEnd);
      blocks.add(copy);
      ChunkHeaderLayout.publishCommittedTail(chunkSegment, blockEnd);
      storage.flush();
      return copy;
    }
  }

  boolean canAllocate(int blockLength) {
    if (blockLength <= 0) {
      throw new IllegalArgumentException("blockLength(=" + blockLength + ") must be positive");
    }
    synchronized (lock) {
      if (state() != ChunkState.ACTIVE) {
        return false;
      }
      var allocatedTail = allocatedTail();
      return allocatedTail == committedTail() && blockLength <= chunkSegment.byteSize() - allocatedTail;
    }
  }

  /// @return a snapshot of blocks currently allocated in the chunk
  @NotNull List<DatabaseBlock> blocks() {
    synchronized (lock) {
      return List.copyOf(blocks);
    }
  }

  boolean containsOnlyRetiredBlocks() {
    synchronized (lock) {
      if (blocks.isEmpty()) {
        return false;
      }
      for (var block : blocks) {
        if (block.state() != RETIRED) {
          return false;
        }
      }
      return true;
    }
  }

  void seal() throws IOException {
    synchronized (lock) {
      ChunkHeaderLayout.transitionState(chunkSegment, ChunkState.ACTIVE, ChunkState.SEALED);
      storage.flush();
    }
  }

  void retire() throws IOException {
    synchronized (lock) {
      ChunkHeaderLayout.transitionState(chunkSegment, ChunkState.SEALED, ChunkState.RETIRED);
      storage.flush();
    }
  }

  private static @NotNull MMappedFileStorageFactory mmappedFileFactory(int chunkSize, boolean fsyncOnFlush) {
    return MMappedFileStorageFactory
      .withDefaults()
      .pageSize(chunkSize)
      .fsyncOnFlush(fsyncOnFlush)
      .createParentDirectories(false);//must be already created
  }

  private static @NotNull MemorySegment chunkMemorySegment(@NotNull MMappedFileStorage storage, int chunkSize) throws IOException {
    var page = storage.pageByIndex(0);//trigger file expansion
    long actualFileSize = storage.actualFileSize();
    if (actualFileSize != chunkSize) {
      throw new IOException(
        "Chunk storage must create one full-size mapping, but: storage(=" + actualFileSize + ") <> chunkSize(=" + chunkSize + ")");
    }
    long firstMappedPageSize = page.rawPageSegment().byteSize();
    if (firstMappedPageSize != chunkSize) {
      throw new IOException(
        "Chunk storage must create a full-file mapping, but: storage(=" + firstMappedPageSize + ") <> chunkSize(=" + chunkSize + ")");
    }
    return page.rawPageSegment();
  }

  private static @NotNull List<DatabaseBlock> recoverBlocks(@NotNull Path storagePath,
                                                            @NotNull MemorySegment mapping,
                                                            int chunkId,
                                                            long committedTail) throws IOException {
    var blocks = new ArrayList<DatabaseBlock>();
    var recoveredTail = (long)ChunkHeaderLayout.HEADER_SIZE;

    while (recoveredTail < committedTail) {
      var block = DatabaseBlock.open(storagePath, mapping, chunkId, recoveredTail, committedTail);
      blocks.add(block);
      recoveredTail += block.blockLength();
    }
    if (recoveredTail != committedTail) {
      throw new CorruptedException("[" + storagePath + "]: the committed chunk prefix does not end at a block boundary");
    }

    ChunkHeaderLayout.publishAllocatedTail(mapping, recoveredTail);
    return blocks;
  }

  private static void validateExpectedFields(long databaseId, int chunkId, int chunkSize) {
    if (databaseId == 0) {
      throw new IllegalArgumentException("databaseId must be non-zero");
    }
    if (chunkId <= 0) {
      throw new IllegalArgumentException("chunkId(=" + chunkId + ") must be positive");
    }
    if (chunkSize < ChunkHeaderLayout.HEADER_SIZE || Integer.bitCount(chunkSize) != 1) {
      throw new IllegalArgumentException(
        "chunkSize(=" + chunkSize + ") must be a power of 2 and at least " + ChunkHeaderLayout.HEADER_SIZE
      );
    }
  }

  @NotNull Path storagePath() {
    return storage.storagePath();
  }

  long databaseId() {
    return chunkId.databaseId();
  }

  int chunkId() {
    return chunkId.chunkId();
  }

  @NotNull ChunkState state() {
    return ChunkHeaderLayout.readState(chunkSegment);
  }

  long committedTail() {
    return ChunkHeaderLayout.readCommittedTail(chunkSegment);
  }

  long allocatedTail() {
    return ChunkHeaderLayout.readAllocatedTail(chunkSegment);
  }

  int mappedSize() {
    return chunkSize;
  }

  @Override
  public void flush() throws IOException {
    storage.flush();
  }

  /// Only for use from [DatabaseChunks]
  void fsync() throws IOException {
    storage.fsync();
  }

  @Override
  public void close() throws IOException {
    storage.close();
  }

  @Override
  public String toString() {
    // avoid accessing chunkSegment's data: it can be closed already
    return "DatabaseChunk[#" + chunkId.chunkId() + ", db: " + databaseId() + ", " + chunkSize + "b]{" + storagePath() + '}';
  }

  /// Immutable chunk fields -- identifies one chunk within a database
  private record ChunkIdentity(long databaseId, int chunkId) { }
}
