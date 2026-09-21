// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.platform.util.io.storages.database.spi.housekeeping.Housekeeper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

import static java.time.temporal.ChronoUnit.MILLIS;

/// Manages housekeeping for a single [DurableMapOverBlocks]
@ApiStatus.Internal
public final class RetireUnusedBlockInDurableMapHousekeeper implements Housekeeper {

  private final Duration initialRunDelay;
  private final Duration nextRunDelay;
  private boolean firstRoundDone = false;

  private final @NotNull DurableMapOverBlocks<?, ?> map;

  public RetireUnusedBlockInDurableMapHousekeeper(@NotNull DurableMapOverBlocks<?, ?> map) {
    this.map = map;

    //Spread different maps housekeeping randomly over timeline: randomize both initial delay and period
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    nextRunDelay = Duration.ofMillis(rnd.nextInt(15_000, 30_000));
    initialRunDelay = Duration.ofMinutes(1).plus(rnd.nextInt(-1_000, 1000), MILLIS);
  }

  @Override
  public @NotNull Duration runHousekeeping(@NotNull BooleanSupplier cancellationRequested) throws IOException {
    if (!firstRoundDone) {
      firstRoundDone = true;
      return initialRunDelay;
    }
    map.retireUnusedBlocks(cancellationRequested);
    return nextRunDelay;
  }
}
