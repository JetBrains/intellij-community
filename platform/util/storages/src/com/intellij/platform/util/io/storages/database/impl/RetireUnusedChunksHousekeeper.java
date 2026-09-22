// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.spi.housekeeping.Housekeeper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/// Retires a sealed chunks when all theirs blocks are retired.
@ApiStatus.Internal
public final class RetireUnusedChunksHousekeeper implements Housekeeper {
  private static final Duration DEFAULT_NEXT_RUN_DELAY = Duration.ofMinutes(1);

  private final @NotNull BlocksDatabaseImpl database;
  /// Housekeepers are all run by a single thread, so synchronization is not needed
  private int lastCheckedChunkId;

  public RetireUnusedChunksHousekeeper(@NotNull BlocksDatabaseImpl database) {
    this.database = database;
  }

  @Override
  public @NotNull Duration runHousekeeping(@NotNull BooleanSupplier cancellationRequested) throws IOException {
    if (cancellationRequested.getAsBoolean()) {
      return Duration.ofSeconds(10);
    }

    var chunksCandidatesForRetirement = database.chunksForRetirementCheck();
    if (chunksCandidatesForRetirement.isEmpty()) {
      return DEFAULT_NEXT_RUN_DELAY;
    }

    // retire <=1 chunk per round:
    var chunkToRetire = chunksCandidatesForRetirement.getFirst();
    for (var candidate : chunksCandidatesForRetirement) {
      if (candidate.chunkId() > lastCheckedChunkId) {
        chunkToRetire = candidate;
        break;
      }
    }
    database.retireChunkIfUnused(chunkToRetire, cancellationRequested);
    lastCheckedChunkId = chunkToRetire.chunkId();
    return DEFAULT_NEXT_RUN_DELAY;
  }
}
