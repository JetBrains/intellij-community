// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.layout;

import com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT64_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT8_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemoryLayout.PathElement.sequenceElement;

/// Binary layout of a chunk-file header:
/// ```
/// ChunkHeaderLayout[=64 bytes] {
///   magic                : int32   //='DMCH'
///   chunkId              : int32
///   databaseId           : int64   // mainly to prevent accidential use of undeleted chunk-file from previous DB instance
///   committedTail        : int64
///   allocatedTail        : int64
///   chunkState           : int32   // ChunkState (actually, int8 is enough)
///
///   reserved             : int8[28]
/// }
/// ```
@ApiStatus.Internal
public final class ChunkHeaderLayout {
  public static final int CHUNK_FILE_MAGIC = IOUtil.asciiToMagicWord("DMCH");

  public static final int RESERVED_BYTES_IN_HEADER = 28;

  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
      INT32_LAYOUT.withName("magic"),
      INT32_LAYOUT.withName("chunkId"),
      INT64_LAYOUT.withName("databaseId"),
      INT64_LAYOUT.withName("committedTail"),
      INT64_LAYOUT.withName("allocatedTail"),
      INT32_LAYOUT.withName("chunkState"),
      MemoryLayout.sequenceLayout(RESERVED_BYTES_IN_HEADER, INT8_LAYOUT).withName("reserved")
    )
    .withByteAlignment(Long.BYTES)
    .withName("ChunkHeaderLayout");

  //@formatter:off
  public static final VarHandle MAGIC_HANDLE                 = fieldHandle(LAYOUT, "magic");
  public static final VarHandle CHUNK_ID_HANDLE              = fieldHandle(LAYOUT, "chunkId");
  public static final VarHandle DATABASE_ID_HANDLE           = fieldHandle(LAYOUT, "databaseId");
  public static final VarHandle COMMITTED_TAIL_HANDLE        = fieldHandle(LAYOUT, "committedTail");
  public static final VarHandle ALLOCATED_TAIL_HANDLE        = fieldHandle(LAYOUT, "allocatedTail");
  public static final VarHandle CHUNK_STATE_HANDLE           = fieldHandle(LAYOUT, "chunkState");
  public static final VarHandle RESERVED_HANDLE              = LAYOUT.varHandle(groupElement("reserved"), sequenceElement())
    .withInvokeExactBehavior();

  public static final int HEADER_SIZE                        = Math.toIntExact(LAYOUT.byteSize());
  //@formatter:on

  /** Initializes a new active chunk with an empty committed prefix. */
  public static void initialize(@NotNull MemorySegment target, long databaseId, int chunkId) {
    MAGIC_HANDLE.set(target, 0L, CHUNK_FILE_MAGIC);
    CHUNK_ID_HANDLE.set(target, 0L, chunkId);
    DATABASE_ID_HANDLE.set(target, 0L, databaseId);
    COMMITTED_TAIL_HANDLE.set(target, 0L, (long)HEADER_SIZE);
    ALLOCATED_TAIL_HANDLE.set(target, 0L, (long)HEADER_SIZE);
    CHUNK_STATE_HANDLE.set(target, 0L, ChunkState.ACTIVE.persistentCode());

    for (long index = 0; index < RESERVED_BYTES_IN_HEADER; index++) {
      RESERVED_HANDLE.set(target, 0L, index, (byte)0);
    }
  }

  public static void validate(@NotNull Path storagePath,
                              @NotNull MemorySegment source,
                              int chunkSize) throws CorruptedException {
    if ((int)MAGIC_HANDLE.get(source, 0L) != CHUNK_FILE_MAGIC) {
      throw new CorruptedException("[" + storagePath + "]: invalid chunk magic");
    }

    var databaseId = readDatabaseId(source);
    var chunkId = readChunkId(source);
    validateState(storagePath, (int)CHUNK_STATE_HANDLE.getVolatile(source, 0L));
    var committedTail = readCommittedTail(source);
    var allocatedTail = readAllocatedTail(source);

    if (databaseId == 0) {
      throw new CorruptedException("[" + storagePath + "]: databaseId must be non-zero");
    }
    if (chunkId <= 0) {
      throw new CorruptedException("[" + storagePath + "]: chunkId(=" + chunkId + ") must be positive");
    }
    validateTails(storagePath, committedTail, allocatedTail, chunkSize);
    for (long index = 0; index < RESERVED_BYTES_IN_HEADER; index++) {
      if ((byte)RESERVED_HANDLE.get(source, 0L, index) != 0) {
        throw new CorruptedException("[" + storagePath + "]: reserved header bytes must be zero");
      }
    }
  }

  public static long readDatabaseId(@NotNull MemorySegment source) {
    return (long)DATABASE_ID_HANDLE.get(source, 0L);
  }

  public static int readChunkId(@NotNull MemorySegment source) {
    return (int)CHUNK_ID_HANDLE.get(source, 0L);
  }

  public static long readCommittedTail(@NotNull MemorySegment source) {
    return (long)COMMITTED_TAIL_HANDLE.getVolatile(source, 0L);
  }

  public static long readAllocatedTail(@NotNull MemorySegment source) {
    return (long)ALLOCATED_TAIL_HANDLE.getVolatile(source, 0L);
  }

  public static @NotNull ChunkState readState(@NotNull MemorySegment source) {
    var persistentCode = (int)CHUNK_STATE_HANDLE.getAcquire(source, 0L);
    var state = ChunkState.fromPersistentCode(persistentCode);
    if (state == null) {
      throw new IllegalStateException("Unknown chunk state code " + Integer.toUnsignedString(persistentCode));
    }
    return state;
  }

  /** Publishes one valid lifecycle transition and rejects a stale transition. */
  public static void transitionState(@NotNull MemorySegment target,
                                     @NotNull ChunkState expectedState,
                                     @NotNull ChunkState newState) {
    if (!isValidTransition(expectedState, newState)) {
      throw new IllegalArgumentException("Chunk state transition " + expectedState + " -> " + newState + " is not valid");
    }
    var previousCode = (int)CHUNK_STATE_HANDLE.compareAndExchange(
      target,
      0L,
      expectedState.persistentCode(),
      newState.persistentCode()
    );
    if (previousCode != expectedState.persistentCode()) {
      var previousState = ChunkState.fromPersistentCode(previousCode);
      var previousStateText = previousState == null ? "unknown code " + Integer.toUnsignedString(previousCode) : previousState.toString();
      throw new IllegalStateException(
        "Chunk state is " + previousStateText + ", but transition " + expectedState + " -> " + newState + " requires " + expectedState
      );
    }
  }

  public static void publishCommittedTail(@NotNull MemorySegment target, long value) {
    COMMITTED_TAIL_HANDLE.setRelease(target, 0L, value);
  }

  public static void publishAllocatedTail(@NotNull MemorySegment target, long value) {
    ALLOCATED_TAIL_HANDLE.setRelease(target, 0L, value);
  }

  private static boolean isValidTransition(@NotNull ChunkState oldState,
                                           @NotNull ChunkState newState) {
    return switch (oldState) {
      case ACTIVE -> newState == ChunkState.SEALED;
      case SEALED -> newState == ChunkState.RETIRED;
      case RETIRED -> false;
    };
  }

  private static void validateState(@NotNull Path storagePath, int persistentCode) throws CorruptedException {
    if (ChunkState.fromPersistentCode(persistentCode) == null) {
      throw new CorruptedException("[" + storagePath + "]: unknown chunk state code " + persistentCode);
    }
  }

  private static void validateTails(@NotNull Path storagePath,
                                    long committedTail,
                                    long allocatedTail,
                                    int chunkSize) throws CorruptedException {
    if (committedTail < HEADER_SIZE || committedTail > allocatedTail || allocatedTail > chunkSize) {
      throw new CorruptedException(
        "[" + storagePath + "]: expected " + HEADER_SIZE + " <= committedTail(=" + committedTail +
        ") <= allocatedTail(=" + allocatedTail + ") <= chunkSize(=" + chunkSize + ")"
      );
    }
    if ((committedTail & (BlockHeaderLayout.BLOCK_ALIGNMENT - 1)) != 0 ||
        (allocatedTail & (BlockHeaderLayout.BLOCK_ALIGNMENT - 1)) != 0) {
      throw new CorruptedException(
        "[" + storagePath + "]: chunk tails must be " + BlockHeaderLayout.BLOCK_ALIGNMENT + "-byte aligned"
      );
    }
  }

  private ChunkHeaderLayout() { }
}
