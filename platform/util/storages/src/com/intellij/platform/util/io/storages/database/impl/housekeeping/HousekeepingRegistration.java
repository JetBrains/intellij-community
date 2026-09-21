// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.housekeeping;

import com.intellij.platform.util.io.storages.database.spi.housekeeping.Housekeeper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/// Owns a housekeeper, manages its lifecycle: cancels active run on [#close], waits its active run to finish before closing.
@ApiStatus.Internal
public final class HousekeepingRegistration implements AutoCloseable {
  private final @NotNull DatabaseHousekeepingCoordinator owner;
  private final @NotNull Housekeeper housekeeper;

  private final transient @NotNull Object lock = new Object();
  private boolean closed;
  private @Nullable RunningRoundInfo currentRound;

  HousekeepingRegistration(@NotNull DatabaseHousekeepingCoordinator coordinator, @NotNull Housekeeper housekeeper) {
    this.owner = coordinator;
    this.housekeeper = housekeeper;
  }

  /// @return true if this registration is owned by given coordinator
  boolean isOwnedBy(@NotNull DatabaseHousekeepingCoordinator coordinator) {
    return owner == coordinator;
  }

  @NotNull HousekeepingRegistration.RunningRoundInfo reserveRun() {
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("The housekeeping registration is already closed");
      }
      return createRun();
    }
  }

  @Nullable HousekeepingRegistration.RunningRoundInfo reserveRunIfOpen() {
    synchronized (lock) {
      return closed ? null : createRun();
    }
  }

  private @NotNull HousekeepingRegistration.RunningRoundInfo createRun() {
    if (currentRound != null) {
      throw new IllegalStateException("Housekeeping is already running");
    }
    var running = new RunningRoundInfo(Thread.currentThread());
    currentRound = running;
    return running;
  }

  /// @return requested delay until next housekeeping round
  @NotNull Duration runHousekeeping(@NotNull HousekeepingRegistration.RunningRoundInfo running) throws IOException {
    try {
      BooleanSupplier cancellationRequested = () -> running.cancelled;
      var nextRunDelay = housekeeper.runHousekeeping(cancellationRequested);
      if (nextRunDelay.isZero() || nextRunDelay.isNegative()) {
        throw new IllegalArgumentException("The next housekeeping delay must be positive: " + nextRunDelay);
      }
      return nextRunDelay;
    }
    finally {
      synchronized (lock) {
        if (currentRound == running) {
          currentRound = null;
        }
      }
      running.completion.complete(null);
    }
  }

  @Override
  public void close() {
    RunningRoundInfo running;
    synchronized (lock) {
      if (closed) {
        return;
      }
      if (currentRound != null && currentRound.runningOnThread == Thread.currentThread()) {
        throw new IllegalStateException("A housekeeper cannot close its own registration during a run");
      }
      closed = true;
      running = currentRound;
      if (running != null) {
        running.cancelled = true;
      }
    }

    if (running != null) {
      running.completion.join();
    }
    owner.unregister(this);
  }

  static final class RunningRoundInfo {
    private final @NotNull Thread runningOnThread;
    private final @NotNull CompletableFuture<Void> completion = new CompletableFuture<>();

    /// Housekeeping itself (currently) is single-threaded, but cancellation could be initiated from other thread, e.g. from [close]
    private volatile boolean cancelled;

    private RunningRoundInfo(@NotNull Thread runningOnThread) {
      this.runningOnThread = runningOnThread;
    }
  }
}
