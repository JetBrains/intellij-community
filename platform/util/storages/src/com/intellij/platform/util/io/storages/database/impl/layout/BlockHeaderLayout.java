// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.layout;

import com.intellij.platform.util.io.storages.UnsupportedFormatException;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT8_LAYOUT;

/// ```
/// BlockHeaderLayout[=20 bytes] {
///   headerVersion : int8
///   role          : int8     // an opaque application tag, DB doesn't interpret it
///   [padding]     : int8[2]
///   storeId       : int32    // a store this block belongs to
///   blockId       : int32    // logical ID: kept as block is relocated on compaction
///   blockLength   : int32
///   state         : int32    // int8 really suffice, but made int32 to enable CAS
/// }
/// ```
/// TODO RC: (headerVersion, role, state) can all be packed into int32, making header just 16 bytes long
@ApiStatus.Internal
public final class BlockHeaderLayout {
  public static final byte FORMAT_VERSION = 1;

  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
    INT8_LAYOUT.withName("headerVersion"),
    INT8_LAYOUT.withName("role"),
    MemoryLayout.paddingLayout(2),
    INT32_LAYOUT.withName("storeId"),
    INT32_LAYOUT.withName("blockId"),
    INT32_LAYOUT.withName("blockLength"),
    INT32_LAYOUT.withName("state")
  ).withName("BlockHeaderLayout");

  //@formatter:off
  public static final VarHandle HEADER_VERSION_HANDLE = LayoutUtils.fieldHandle(LAYOUT, "headerVersion");
  public static final VarHandle ROLE_HANDLE           = LayoutUtils.fieldHandle(LAYOUT, "role");
  public static final VarHandle STORE_ID_HANDLE       = LayoutUtils.fieldHandle(LAYOUT, "storeId");
  public static final VarHandle BLOCK_ID_HANDLE       = LayoutUtils.fieldHandle(LAYOUT, "blockId");
  public static final VarHandle BLOCK_LENGTH_HANDLE   = LayoutUtils.fieldHandle(LAYOUT, "blockLength");
  public static final VarHandle STATE_HANDLE          = LayoutUtils.fieldHandle(LAYOUT, "state");

  public static final int HEADER_SIZE                 = Math.toIntExact(LAYOUT.byteSize());
  public static final int BLOCK_ALIGNMENT             = Math.toIntExact(LAYOUT.byteAlignment());

  public static final int UNPUBLISHED_STATE_CODE      = 0;
  public static final int ALLOCATED_STATE_CODE        = 1;
  public static final int ACTIVE_STATE_CODE           = 2;
  public static final int SEALED_STATE_CODE           = 3;
  public static final int RETIRED_STATE_CODE          = 4;
  //@formatter:on

  public static void initialize(@NotNull MemorySegment blockSegment,
                                int role,
                                int storeId,
                                int blockId,
                                int blockLength) throws IOException {
    var stateBefore = (int)STATE_HANDLE.get(blockSegment, 0L);
    if (stateBefore != UNPUBLISHED_STATE_CODE) {
      ByteBuffer buffer = blockSegment.asByteBuffer();
      if (buffer.limit() > 1024) buffer.limit(1024);
      throw new CorruptedException(
        "[.state: " + stateBefore + " <> " + UNPUBLISHED_STATE_CODE + "] => the region was not zeroed. " +
        "[blockId: " + blockId + ", blockLength: " + blockLength + ", storeId: " + storeId + ", role: " + role + "] " +
        "region: " + IOUtil.toHexString(buffer)
      );
    }

    HEADER_VERSION_HANDLE.set(blockSegment, 0L, FORMAT_VERSION);
    ROLE_HANDLE.set(blockSegment, 0L, (byte)role);
    STORE_ID_HANDLE.set(blockSegment, 0L, storeId);
    BLOCK_ID_HANDLE.set(blockSegment, 0L, blockId);
    BLOCK_LENGTH_HANDLE.set(blockSegment, 0L, blockLength);

    STATE_HANDLE.setRelease(blockSegment, 0L, stateToPersistentCode(LifecycleState.ALLOCATED));
  }

  public static void validate(@NotNull Path chunkPath,
                              @NotNull MemorySegment source,
                              long blockOffset) throws IOException {
    var persistentStateCode = (int)STATE_HANDLE.getAcquire(source, 0L);
    if (persistentStateCode == UNPUBLISHED_STATE_CODE) {
      throw corrupted(chunkPath, blockOffset, "block header is not published");
    }
    var state = persistentCodeToState(persistentStateCode);
    if (state == null) {
      throw corrupted(chunkPath, blockOffset, "unknown block state code " + Integer.toUnsignedString(persistentStateCode));
    }

    var headerVersion = (byte)HEADER_VERSION_HANDLE.get(source, 0L);
    if (headerVersion != FORMAT_VERSION) {
      throw new UnsupportedFormatException(
        "block header at offset " + blockOffset + " in " + chunkPath,
        Byte.toUnsignedInt(FORMAT_VERSION),
        Byte.toUnsignedInt(headerVersion)
      );
    }
  }

  public static int readRole(@NotNull MemorySegment source) {
    return Byte.toUnsignedInt((byte)ROLE_HANDLE.get(source, 0L));
  }

  /// @return a state of the block
  /// @throws IllegalStateException if the state is unrecognizable, or the source is invalid (un-mapped)
  public static @NotNull LifecycleState readState(@NotNull MemorySegment source) {
    int persistentStateCode = (int)STATE_HANDLE.getAcquire(source, 0L);
    var state = persistentCodeToState(persistentStateCode);
    if (state == null) {//MAYBE RC: throw CorruptedException here?
      throw new IllegalStateException("Unknown block state code " + Integer.toUnsignedString(persistentStateCode));
    }
    return state;
  }

  /// Publishes one valid lifecycle transition and rejects a stale or concurrent transition
  public static void transitionState(@NotNull MemorySegment blockSegment,
                                     @NotNull LifecycleState expectedState,
                                     @NotNull LifecycleState newState) {
    if (!isValidTransition(expectedState, newState)) {
      throw new IllegalArgumentException("Block state transition " + expectedState + " -> " + newState + " is not valid");
    }

    int expectedStateCode = stateToPersistentCode(expectedState);
    int newStateCode = stateToPersistentCode(newState);
    var actualStateCode = (int)STATE_HANDLE.compareAndExchange(
      blockSegment,
      0L,
      expectedStateCode,
      newStateCode
    );
    if (actualStateCode != expectedStateCode) {
      var actualState = persistentCodeToState(actualStateCode);
      var actualStateText = actualState == null ?
                            "unknown code " + Integer.toUnsignedString(actualStateCode) :
                            actualState.toString();
      throw new IllegalStateException(
        "Block state is " + actualStateText + ", but transition [" + expectedState + " -> " + newState + "] requested"
      );
    }
  }

  /// Unconditional lifecycle transition: `<any state>` -> [LifecycleState#RETIRED]
  /// Used to discard a block when it is removed from its owning store
  public static void retireForStoreDrop(@NotNull MemorySegment blockSegment) {
    while (true) {
      int stateCode = (int)STATE_HANDLE.getVolatile(blockSegment, 0L);
      switch (stateCode) {
        case ALLOCATED_STATE_CODE,
             ACTIVE_STATE_CODE,
             SEALED_STATE_CODE -> {
          if (STATE_HANDLE.compareAndSet(blockSegment, 0L, stateCode, RETIRED_STATE_CODE)) {
            return;
          }
        }
        case RETIRED_STATE_CODE -> {
          return;
        }

        default -> throw new IllegalStateException("unexpected stateCode(=" + stateCode + ")");
      }
    }
  }

  /// Retires an old block copy of evacuated block, after block evacuation publishes a newer copy;
  /// The block could be in any state except [LifecycleState#ALLOCATED] (allocated blocks can't be evacuated);
  /// If the block is already retired => the method does nothing;
  public static void retireEvacuatedBlock(@NotNull MemorySegment blockSegment) {
    while (true) {
      int stateCode = (int)STATE_HANDLE.getVolatile(blockSegment, 0L);
      switch (stateCode) {
        case ALLOCATED_STATE_CODE -> {
          throw new IllegalStateException("An ALLOCATED block cannot be an evacuated source");
        }
        case RETIRED_STATE_CODE -> {
          return;
        }
        case ACTIVE_STATE_CODE,
             SEALED_STATE_CODE -> {
          if (STATE_HANDLE.compareAndSet(blockSegment, 0L, stateCode, RETIRED_STATE_CODE)) {
            return;
          }
        }

        default -> throw new IllegalStateException("unexpected stateCode(=" + stateCode + ")");
      }
    }
  }

  public static int readStoreId(@NotNull MemorySegment source) {
    return (int)STORE_ID_HANDLE.get(source, 0L);
  }

  public static int readBlockId(@NotNull MemorySegment source) {
    return (int)BLOCK_ID_HANDLE.get(source, 0L);
  }

  public static int readBlockLength(@NotNull MemorySegment source) {
    return (int)BLOCK_LENGTH_HANDLE.get(source, 0L);
  }

  /** Allows normal lifecycle transitions and discarding an allocated block. */
  private static boolean isValidTransition(@NotNull LifecycleState oldState,
                                           @NotNull LifecycleState newState) {
    return switch (oldState) {
      case ALLOCATED -> newState == LifecycleState.ACTIVE || newState == LifecycleState.RETIRED;
      case ACTIVE -> newState == LifecycleState.SEALED;
      case SEALED -> newState == BlocksStore.Block.LifecycleState.RETIRED;
      case RETIRED -> false;//terminal state
    };
  }

  private static int stateToPersistentCode(@NotNull LifecycleState state) {
    return switch (state) {
      case ALLOCATED -> ALLOCATED_STATE_CODE;
      case ACTIVE -> ACTIVE_STATE_CODE;
      case SEALED -> SEALED_STATE_CODE;
      case RETIRED -> RETIRED_STATE_CODE;
    };
  }

  private static @Nullable LifecycleState persistentCodeToState(int persistentCode) {
    return switch (persistentCode) {
      case ALLOCATED_STATE_CODE -> LifecycleState.ALLOCATED;
      case ACTIVE_STATE_CODE -> LifecycleState.ACTIVE;
      case SEALED_STATE_CODE -> LifecycleState.SEALED;
      case RETIRED_STATE_CODE -> LifecycleState.RETIRED;
      default -> null;
    };
  }

  private static @NotNull CorruptedException corrupted(@NotNull Path chunkPath, long blockOffset, @NotNull String details) {
    return new CorruptedException("[" + chunkPath + "]: invalid block at offset " + blockOffset + ": " + details);
  }

  private BlockHeaderLayout() { }
}
