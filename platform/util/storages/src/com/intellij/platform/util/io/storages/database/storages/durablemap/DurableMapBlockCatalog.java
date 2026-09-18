// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/// An in-memory catalog of blocks for one durable map store
@ApiStatus.Internal
public final class DurableMapBlockCatalog {
  private final BlocksStore store;
  private final ArrayList<BlocksStore.Block> blocks;
  private final EnumMap<DurableMapBlockRole, ArrayList<BlocksStore.Block>> blocksByRole;

  private DurableMapBlockCatalog(@NotNull BlocksStore store,
                                 @NotNull ArrayList<BlocksStore.Block> blocks,
                                 @NotNull EnumMap<DurableMapBlockRole, ArrayList<BlocksStore.Block>> blocksByRole) {
    this.store = store;
    this.blocks = blocks;
    this.blocksByRole = blocksByRole;
  }

  public static @NotNull DurableMapBlockCatalog open(@NotNull BlocksStore store) throws CorruptedException {
    var blocks = new ArrayList<BlocksStore.Block>();
    var blocksByRole = emptyRoleCatalog();
    for (var block : store.blocks()) {
      addBlock(blocks, blocksByRole, block);
    }
    return new DurableMapBlockCatalog(store, blocks, blocksByRole);
  }

  /** @return all blocks known to the store, including retired blocks */
  public synchronized @NotNull List<BlocksStore.Block> blocks() {
    return List.copyOf(blocks);
  }

  /** @return all blocks with the specified role, including retired blocks */
  public synchronized @NotNull List<BlocksStore.Block> blocks(@NotNull DurableMapBlockRole role) {
    return List.copyOf(blocksByRole.get(role));
  }

  /** Allocates a block and adds it to this catalog */
  public synchronized @NotNull BlocksStore.Block allocateBlock(@NotNull DurableMapBlockRole role,
                                                               int minimumContentLength) throws IOException {
    var block = store.allocateBlock(role.persistentCode(), minimumContentLength);
    addBlock(blocks, blocksByRole, block);
    return block;
  }

  /// @return access restricted to the specified lookup implementation and generation
  public @NotNull LookupBlocks lookupBlocks(int implementationId, int generation) throws CorruptedException {
    return new ScopedLookupBlocks(implementationId, generation);
  }

  private final class ScopedLookupBlocks implements LookupBlocks {
    private final int implementationId;
    private final int generation;
    private final ArrayList<LookupBlocks.Block> scopedBlocks = new ArrayList<>();

    private ScopedLookupBlocks(int implementationId, int generation) throws CorruptedException {
      if (implementationId <= 0) {
        throw new IllegalArgumentException("implementationId(=" + implementationId + ") must be positive");
      }
      if (generation < 0) {
        throw new IllegalArgumentException("generation(=" + generation + ") must not be negative");
      }
      this.implementationId = implementationId;
      this.generation = generation;
      for (var block : DurableMapBlockCatalog.this.blocks(DurableMapBlockRole.LOOKUP)) {
        if (block.state() == BlocksStore.Block.LifecycleState.RETIRED) {
          continue;
        }
        if (LookupBlockHeaderLayout.implementationId(block) == implementationId &&
            LookupBlockHeaderLayout.generation(block) == generation) {
          scopedBlocks.add(new ScopedLookupBlock(block));
        }
      }
    }

    @Override
    public synchronized @NotNull List<LookupBlocks.Block> blocks() {
      return List.copyOf(scopedBlocks);
    }

    @Override
    public synchronized @NotNull LookupBlocks.Block allocate(int minimumPayloadLength) throws IOException {
      if (minimumPayloadLength < 0) {
        throw new IllegalArgumentException("minimumPayloadLength(=" + minimumPayloadLength + ") must not be negative");
      }
      var block = allocateBlock(
        DurableMapBlockRole.LOOKUP,
        Math.addExact(LookupBlockHeaderLayout.HEADER_SIZE, minimumPayloadLength)
      );
      var initialized = false;
      try {
        LookupBlockHeaderLayout.initialize(block, implementationId, generation);
        var scopedBlock = new ScopedLookupBlock(block);
        scopedBlocks.add(scopedBlock);
        initialized = true;
        return scopedBlock;
      }
      finally {
        if (!initialized && block.state() == BlocksStore.Block.LifecycleState.ALLOCATED) {
          block.discard();
        }
      }
    }

    @Override
    public synchronized void retireAll() {
      for (var block : scopedBlocks) {
        switch (block.state()) {
          case ALLOCATED -> block.discard();
          case ACTIVE -> {
            block.seal();
            block.retire();
          }
          case SEALED -> block.retire();
          case RETIRED -> { }
        }
      }
    }

    @Override
    public void flush() throws IOException {
      store.flush();
    }
  }

  private static final class ScopedLookupBlock implements LookupBlocks.Block {
    private final @NotNull BlocksStore.Block block;

    private ScopedLookupBlock(@NotNull BlocksStore.Block block) {
      this.block = block;
    }

    @Override
    public int id() {
      return block.id();
    }

    @Override
    public @NotNull BlocksStore.Block.LifecycleState state() {
      return block.state();
    }

    @Override
    public @NotNull MemorySegment payload() throws IOException {
      return LookupBlockHeaderLayout.payload(block);
    }

    @Override
    public void activate() {
      block.activate();
    }

    @Override
    public void discard() {
      block.discard();
    }

    @Override
    public void seal() {
      block.seal();
    }

    @Override
    public void retire() {
      block.retire();
    }

    @Override
    public String toString() {
      return "ScopedLookupBlock{wrapped: " + block + "}";
    }
  }

  private static @NotNull EnumMap<DurableMapBlockRole, ArrayList<BlocksStore.Block>> emptyRoleCatalog() {
    var blocksByRole = new EnumMap<DurableMapBlockRole, ArrayList<BlocksStore.Block>>(DurableMapBlockRole.class);
    for (var role : DurableMapBlockRole.values()) {
      blocksByRole.put(role, new ArrayList<>());
    }
    return blocksByRole;
  }

  private static void addBlock(@NotNull ArrayList<BlocksStore.Block> blocks,
                               @NotNull EnumMap<DurableMapBlockRole, ArrayList<BlocksStore.Block>> blocksByRole,
                               @NotNull BlocksStore.Block block) throws CorruptedException {
    var role = DurableMapBlockRole.fromPersistentCode(block.role());
    blocks.add(block);
    blocksByRole.get(role).add(block);
  }

  /** Type ('role') of content in a durable map [Block] */
  @ApiStatus.Internal
  public enum DurableMapBlockRole {
    /** Blocks with key-value records */
    DATA(1),
    /** Blocks with persistent lookup structures */
    LOOKUP(2);

    private final int persistentCode;

    DurableMapBlockRole(int persistentCode) {
      this.persistentCode = persistentCode;
    }

    public int persistentCode() {
      return persistentCode;
    }

    private static @NotNull DurableMapBlockRole fromPersistentCode(int persistentCode) throws CorruptedException {
      return switch (persistentCode) {
        case 1 -> DATA;
        case 2 -> LOOKUP;
        default -> throw new CorruptedException("Unknown durable map block role " + persistentCode);
      };
    }
  }
}
