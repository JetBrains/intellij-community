// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.housekeeping;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

import static com.intellij.diagnostic.ControlFlowExceptionsKt.rethrowControlFlowException;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/// Schedules each housekeeping registration independently;
@ApiStatus.Internal
public final class AsyncHousekeepingScheduler implements DatabaseHousekeepingScheduler {
  private static final Logger LOG = Logger.getInstance(AsyncHousekeepingScheduler.class);

  private static final Duration FAILURE_RETRY_DELAY = Duration.ofMinutes(1);

  /// We don't know a housekeeping periodicity until the first [com.intellij.platform.util.io.storages.database.spi.housekeeping.Housekeeper#runHousekeeping(BooleanSupplier)]
  /// call -- this delay is used to spread this first call slightly in timeline.
  /// Value is arbitrary, chosen from common sense.
  private static final Duration INITIAL_RUN_DELAY = Duration.ofMillis(1);

  private final @NotNull DatabaseHousekeepingCoordinator coordinator;

  /// Scheduler doesn't own this executor -- its lifecycle is up to calling code
  private final @NotNull ScheduledExecutorService schedulingExecutor;
  /// Housekeeping tasks are running on this executor;
  /// Scheduler doesn't own this executor -- its lifecycle is up to calling code
  private final @NotNull Executor housekeepingExecutor;

  private final transient @NotNull Object lock = new Object();
  private final @NotNull Set<HousekeepingRegistration> registrations = new HashSet<>();
  private final @NotNull Map<HousekeepingRegistration, ScheduledFuture<?>> scheduledRuns = new HashMap<>();
  private final @NotNull ArrayDeque<HousekeepingRegistration> readyRegistrations = new ArrayDeque<>();
  private boolean workerRunning;

  public AsyncHousekeepingScheduler(@NotNull DatabaseHousekeepingCoordinator coordinator,
                                    @NotNull ScheduledExecutorService schedulingExecutor,
                                    @NotNull Executor housekeepingExecutor) {
    this.coordinator = coordinator;
    this.schedulingExecutor = schedulingExecutor;
    this.housekeepingExecutor = housekeepingExecutor;
  }

  @Override
  public void register(@NotNull HousekeepingRegistration registration) {
    synchronized (lock) {
      if (!registrations.add(registration)) {
        return;
      }
      //Randomize initial delay to spread multiple registrations' first runs from each other
      long randomInitialDelay = ThreadLocalRandom.current().nextLong(
        INITIAL_RUN_DELAY.toMillis() / 2,
        INITIAL_RUN_DELAY.toMillis()
      );
      scheduleAfterPhysicalDelay(registration, Duration.ofMillis(randomInitialDelay));
    }
  }

  @Override
  public void unregister(@NotNull HousekeepingRegistration registration) {
    synchronized (lock) {
      registrations.remove(registration);
      var scheduledRun = scheduledRuns.remove(registration);
      if (scheduledRun != null) {
        scheduledRun.cancel(false);
      }
      readyRegistrations.removeIf(readyRegistration -> readyRegistration == registration);
    }
  }

  private void scheduleAfterLogicalDelay(@NotNull HousekeepingRegistration registration,
                                         @NotNull Duration logicalDelay) {
    // The asynchronous scheduler currently maps logical time to physical time one-to-one.
    scheduleAfterPhysicalDelay(registration, logicalDelay);
  }

  private void scheduleAfterPhysicalDelay(@NotNull HousekeepingRegistration registration,
                                          @NotNull Duration physicalDelay) {
    if (!registrations.contains(registration)) {
      return;
    }
    var scheduledRun = schedulingExecutor.schedule(
      () -> makeReady(registration),
      physicalDelay.toMillis(), MILLISECONDS
    );
    var previousRun = scheduledRuns.put(registration, scheduledRun);
    if (previousRun != null) {
      previousRun.cancel(false);
    }
  }

  private void makeReady(@NotNull HousekeepingRegistration registration) {
    boolean startWorker = false;
    synchronized (lock) {
      scheduledRuns.remove(registration);
      if (!registrations.contains(registration)) {
        return;
      }
      readyRegistrations.addLast(registration);
      if (!workerRunning) {
        workerRunning = true;
        startWorker = true;
      }
    }
    if (startWorker) {
      submitWorker();
    }
  }

  private void submitWorker() {
    try {
      housekeepingExecutor.execute(this::runReadyRegistrations);
    }
    catch (RuntimeException e) {
      rethrowControlFlowException(e);
      LOG.error("Failed to submit database housekeeping", e);
      synchronized (lock) {
        workerRunning = false;
        while (!readyRegistrations.isEmpty()) {
          scheduleAfterPhysicalDelay(readyRegistrations.removeFirst(), FAILURE_RETRY_DELAY);
        }
      }
    }
  }

  private void runReadyRegistrations() {
    while (true) {
      HousekeepingRegistration registration;
      synchronized (lock) {
        if (readyRegistrations.isEmpty()) {
          workerRunning = false;
          return;
        }
        registration = readyRegistrations.removeFirst();
      }

      var nextRunLogicalDelay = runHousekeeping(registration);
      if (nextRunLogicalDelay != null) {
        synchronized (lock) {
          scheduleAfterLogicalDelay(registration, nextRunLogicalDelay);
        }
      }
    }
  }

  private @Nullable Duration runHousekeeping(@NotNull HousekeepingRegistration registration) {
    try {
      return coordinator.runHousekeepingIfOpen(registration);
    }
    catch (Exception e) {
      rethrowControlFlowException(e);
      LOG.error("Database housekeeping failed", e);
      synchronized (lock) {
        scheduleAfterPhysicalDelay(registration, FAILURE_RETRY_DELAY);
      }
      return null;
    }
  }
}
