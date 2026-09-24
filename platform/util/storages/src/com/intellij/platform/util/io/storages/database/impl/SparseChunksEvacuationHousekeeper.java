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

/// Evacuates blocks from sparse sealed chunks and retires the source chunks.
/// Completely empty chunks that do not need evacuation -- are retired right away
/// Runs before application storages open.
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
    int evacuatedChunks = 0;
    long evacuatedBytes = 0;
    int evacuatedBlocks = 0;
    for (var sparseChunkInfo : sparseChunksSmallerToLarger) {
      if (sparseChunkInfo.liveBlocks == 0) {
        //shortcut: empty chunk doesn't need to be evacuated, so retire them right away
        database.retireChunkIfUnused(sparseChunkInfo.chunk, /*cancellationRequest: */() -> false);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Empty chunk " + sparseChunkInfo + " -> retired immediately");
        }
        continue;
      }

      if (sparseChunkInfo.liveBytes > remainingBytes || sparseChunkInfo.liveBlocks > remainingBlocks) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(
            "Sparse chunk " + sparseChunkInfo + " -> skipped (over budget): " +
            "remaining budget is " + remainingBlocks + " blocks and " + remainingBytes + " bytes"
          );
        }
        continue;
      }

      database.evacuateChunk(sparseChunkInfo.chunk);

      evacuatedChunks++;
      evacuatedBytes += sparseChunkInfo.liveBytes;
      evacuatedBlocks += sparseChunkInfo.liveBlocks;
      remainingBytes -= sparseChunkInfo.liveBytes;
      remainingBlocks -= sparseChunkInfo.liveBlocks;
      if (LOG.isDebugEnabled()) {
        LOG.debug("Sparse chunk " + sparseChunkInfo + " -> evacuated");
      }
    }

    if (evacuatedChunks > 0) {
      LOG.info("Sparse chunk evacuation: " +
               "evacuated " + evacuatedChunks + " chunks (of " + sparseChunksInfo.size() + " sparse chunks found), " +
               evacuatedBlocks + " blocks, " + evacuatedBytes + " bytes"
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
    for (var block : chunk.blocks()) {
      if (block.state() != BlocksStore.Block.LifecycleState.RETIRED) {
        liveBytes += block.blockLength();
        liveBlocks++;
      }
    }
    return new ChunkInfo(chunk, liveBytes, liveBlocks);
  }

  private record ChunkInfo(@NotNull DatabaseChunk chunk, long liveBytes, int liveBlocks) {
    @Override
    public String toString() {
      return "{chunk #" + chunk.chunkId() + ": liveBytes=" + liveBytes + ", liveBlocks=" + liveBlocks + '}';
    }
  }
}
