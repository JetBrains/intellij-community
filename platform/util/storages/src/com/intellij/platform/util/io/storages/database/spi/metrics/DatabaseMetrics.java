// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.spi.metrics;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// An immutable snapshot of the database state and the events from the current session
@ApiStatus.Internal
public record DatabaseMetrics(@NotNull CatalogMetrics catalog,
                              @NotNull ChunksMetrics chunks,
                              @NotNull BlocksMetrics blocks) {
  public static final DatabaseMetrics DUMMY = new DatabaseMetrics(
    new CatalogMetrics(0, 0, 0, 0),
    new ChunksMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    new BlocksMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
  );
  public record CatalogMetrics(
    int storesTotal,
    int storesCreated,       // Stores created during the current session
    int storesDropped,       // Stores dropped during the current session
    int catalogLogRecoveries // # of catalog-log recoveries during database opening
  ) {
  }

  public record ChunksMetrics(
    int total,
    int activeCurrent,
    int sealedCurrent,          // Chunks that are currently sealed
    int retiredCurrent,         // Chunks that are currently retired but still tracked
    long totalMappedBytes,      // Bytes in all current chunk mappings
    long created,               // Chunks created during the current session
    long opened,                // Existing chunk files opened during database opening
    long sealed,                // Chunk seal transitions completed during the current session
    long retired,               // Chunk retirement transitions completed during the current session
    long released,              // Retired chunks whose resources were released during the current session
    long statesReconciled,      // Catalog chunk states reconciled with chunk headers during database opening
    long tailsRolledBack,       // # of uncommitted chunk tails rolled back during database opening
    long tailBytesRolledBack,   // Total bytes removed by chunk tail rollbacks
    long filesDeleted           // Unregistered or retired chunk files deleted during database opening
  ) {
  }

  public record BlocksMetrics(
    int total,
    int allocatedCurrent,             // Blocks that are currently allocated but not published
    int activeCurrent,
    int sealedCurrent,                // Blocks that are currently sealed
    int retiredCurrent,               // Blocks that are currently retired
    long allocated,                   // Blocks allocated during the current session
    long activated,                   // Blocks activated during the current session
    long discarded,                   // Blocks discarded during the current session
    long sealed,                      // Block seal transitions completed during the current session
    long retired,                     // Block retirement transitions completed during the current session
    long evacuated,                   // Blocks copied during evacuation in the current session
    long incompleteBlocksDiscarded,   // Incomplete blocks discarded during database opening
    long droppedStoreBlocksRetired,   // Blocks retired because their stores were dropped before a crash
    long staleBlockCopiesRetired      // Old block copies retired after interrupted evacuation
  ) {
  }
}
