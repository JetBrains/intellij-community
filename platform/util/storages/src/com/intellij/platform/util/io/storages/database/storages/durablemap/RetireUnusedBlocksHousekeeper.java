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

/// Schedules unused block retirement for a storage
@ApiStatus.Internal
public final class RetireUnusedBlocksHousekeeper implements Housekeeper {

  private final @NotNull Duration initialRunDelay;
  private final @NotNull Duration nextRunDelayBase = Duration.ofMinutes(1);
  private boolean firstRoundDone;

  private final @NotNull UnusedBlocksRetirement target;

  public RetireUnusedBlocksHousekeeper(@NotNull UnusedBlocksRetirement target) {
    this.target = target;

    //Spread different maps housekeeping randomly over timeline: randomize both initial delay
    var rnd = ThreadLocalRandom.current();
    initialRunDelay = Duration.ofMinutes(1).plus(rnd.nextInt(-1_000, 1_000), MILLIS);
  }

  @Override
  public @NotNull Duration runHousekeeping(@NotNull BooleanSupplier cancellationRequested) throws IOException {
    if (!firstRoundDone) {
      firstRoundDone = true;
      return initialRunDelay;
    }

    long startedAtNs = System.nanoTime();
    target.retireUnusedBlocks(cancellationRequested);
    long elapsedNs = System.nanoTime()-startedAtNs;

    //wait 1 sec longer for each ms spent on housekeeping:
    long taxMs = Math.min((elapsedNs / 1_000_000) * 1000, nextRunDelayBase.toMillis() * 2);
    Duration nextDelay = nextRunDelayBase
      .plusMillis(taxMs)
      .plusMillis(ThreadLocalRandom.current().nextInt(1000));//spread to avoid clustering

    //So, in total: next housekeeping scheduled in `[1..3] * baseDelay + rnd(1sec)`
    return nextDelay;
  }
}
