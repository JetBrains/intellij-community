// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabase;
import com.intellij.platform.util.io.storages.database.spi.housekeeping.OnStartupHousekeeper;
import com.intellij.platform.util.io.storages.database.impl.layout.ChunkHeaderLayout;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/// Evacuates blocks from sparse sealed chunks into a newer chunks, and retires empty chunks.
/// Runs before application storages open.
///
/// Blocks are evacuated within the budgets.
/// If all the live blocks of the chunk are evacuated -> empty chunk is retired.
/// Partially evacuated chunks remain for the next attempt.
@ApiStatus.Internal
public final class SparseChunksEvacuationHousekeeper implements OnStartupHousekeeper {
  private static final Logger LOG = Logger.getInstance(SparseChunksEvacuationHousekeeper.class);

  /// (0,1): if fraction of bytes in chunk belonging to live blocks <= `maximumLiveBytesFraction` -- chunk should be considered
  /// for evacuation
  private final float maximumLiveBytesFraction;
  /// Limit on total # evacuated bytes per one round (to not delay DB startup too much)
  private final long evacuatedBytesBudget;
  /// Limit on total # evacuated blocks per one round (to not delay DB startup too much)
  private final int evacuatedBlocksBudget;

  public SparseChunksEvacuationHousekeeper(float maximumLiveBytesFraction,
                                           long evacuatedBytesBudget,
                                           int evacuatedBlocksBudget) {
    if (!(maximumLiveBytesFraction > 0.0f && maximumLiveBytesFraction < 1.0f)) {
      throw new IllegalArgumentException(
        "maximumLiveBytesFraction(=" + maximumLiveBytesFraction + ") must be in (0..1)"
      );
    }
    if (evacuatedBytesBudget <= 0) {
      throw new IllegalArgumentException("maximumEvacuatedBytes(=" + evacuatedBytesBudget + ") must be positive");
    }
    if (evacuatedBlocksBudget <= 0) {
      throw new IllegalArgumentException("maximumEvacuatedBlocks(=" + evacuatedBlocksBudget + ") must be positive");
    }
    this.maximumLiveBytesFraction = maximumLiveBytesFraction;
    this.evacuatedBytesBudget = evacuatedBytesBudget;
    this.evacuatedBlocksBudget = evacuatedBlocksBudget;
  }

  @Override
  public void runHousekeeping(@NotNull BlocksDatabase database) throws IOException {
    BlocksDatabaseImpl dbImpl = (BlocksDatabaseImpl)database;
    var sparseChunksInfo = sparseChunks(dbImpl);

    evacuateWithinBudget(dbImpl, sparseChunksInfo);
  }

  private void evacuateWithinBudget(@NotNull BlocksDatabaseImpl database,
                                    @NotNull List<ChunkInfo> sparseChunksInfo) throws IOException {
    List<ChunkInfo> sparseChunksSmallerToLarger = sparseChunksInfo.stream()
      .sorted(Comparator.comparingLong(ChunkInfo::liveBytes)
                .thenComparingInt(info -> -info.liveBlocks())//more blocks better
                .thenComparingInt(chunkInfo -> chunkInfo.chunk.chunkId()))
      .toList();


    // Evacuation algorithm is greedy: first evacuate chunks with fewer total bytes to evacuate, and with more blocks
    // (which implies smaller average block) -- assume that fits more tightly into the free chunk's prefix

    long remainingBytes = evacuatedBytesBudget;
    int remainingBlocks = evacuatedBlocksBudget;
    int retiredChunks = 0;
    long evacuatedBytes = 0;
    int evacuatedBlocks = 0;
    for (var sparseChunkInfo : sparseChunksSmallerToLarger) {
      if (sparseChunkInfo.liveBlocks == 0) {
        //shortcut: empty chunk doesn't need to be evacuated => retire it right away
        database.retireChunkIfUnused(sparseChunkInfo.chunk, /*cancellationRequest: */() -> false);
        retiredChunks++;
        continue;
      }

      if (sparseChunkInfo.maxLiveBlockSizeBytes > evacuatedBytesBudget) {
        LOG.warn("Chunk #" + sparseChunkInfo.chunk.chunkId() + " contains a live block of " +
                 sparseChunkInfo.maxLiveBlockSizeBytes + " bytes > " + evacuatedBytesBudget + " bytes evacuation budget " +
                 "=> could never be evacuated!");
      }


      var evacuationResult = database.evacuateBlocksFromChunk(
        sparseChunkInfo.chunk,
        remainingBytes,
        remainingBlocks
      );
      if (evacuationResult.sourceChunkRetired()) {
        retiredChunks++;
      }
      evacuatedBytes += evacuationResult.evacuatedBytes();
      evacuatedBlocks += evacuationResult.evacuatedBlocks();
      remainingBytes -= evacuationResult.evacuatedBytes();
      remainingBlocks -= evacuationResult.evacuatedBlocks();
      if (remainingBytes == 0 || remainingBlocks == 0) {
        break;
      }
    }

    if (evacuatedBlocks > 0) {
      LOG.info("Sparse chunk evacuation: " +
               "retired " + retiredChunks + " chunks (of " + sparseChunksInfo.size() + " sparse chunks found), " +
               "evacuated " + evacuatedBlocks + " blocks, " + evacuatedBytes + " bytes"
      );
    }
  }

  private @NotNull List<ChunkInfo> sparseChunks(@NotNull BlocksDatabaseImpl database) {
    var usableChunkSize = database.chunkSize() - ChunkHeaderLayout.HEADER_SIZE;
    float maximumLiveBytes = usableChunkSize * maximumLiveBytesFraction;
    return database.sealedChunks().stream()
      .map(SparseChunksEvacuationHousekeeper::describeChunk)
      .filter(chunkInfo -> chunkInfo.liveBytes <= maximumLiveBytes)
      .toList();
  }

  private static @NotNull ChunkInfo describeChunk(@NotNull DatabaseChunk chunk) {
    long liveBytes = 0;
    int liveBlocks = 0;
    int maximumLiveBlockBytes = 0;
    for (var block : chunk.blocks()) {
      if (block.state() != BlocksStore.Block.LifecycleState.RETIRED) {
        liveBytes += block.blockLength();
        liveBlocks++;
        maximumLiveBlockBytes = Math.max(maximumLiveBlockBytes, block.blockLength());
      }
    }
    return new ChunkInfo(chunk, liveBytes, liveBlocks, maximumLiveBlockBytes);
  }

  private record ChunkInfo(@NotNull DatabaseChunk chunk, long liveBytes, int liveBlocks, int maxLiveBlockSizeBytes) { }
}
