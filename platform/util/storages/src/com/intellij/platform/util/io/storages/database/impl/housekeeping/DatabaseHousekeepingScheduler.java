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

import static com.intellij.diagnostic.ControlFlowExceptionsKt.rethrowControlFlowException;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/// Schedules each housekeeping registration independently without owning either executor
@ApiStatus.Internal
public final class DatabaseHousekeepingScheduler implements AutoCloseable {
  private static final Logger LOG = Logger.getInstance(DatabaseHousekeepingScheduler.class);
  private static final Duration FAILURE_RETRY_DELAY = Duration.ofMinutes(1);

  private final @NotNull DatabaseHousekeepingCoordinator coordinator;
  private final @NotNull ScheduledExecutorService schedulingExecutor;
  private final @NotNull Executor housekeepingExecutor;

  private final transient @NotNull Object lock = new Object();
  private final @NotNull Set<HousekeepingRegistration> registrations = new HashSet<>();
  private final @NotNull Map<HousekeepingRegistration, ScheduledFuture<?>> scheduledRuns = new HashMap<>();
  private final @NotNull ArrayDeque<HousekeepingRegistration> readyRegistrations = new ArrayDeque<>();
  private boolean workerRunning;
  private boolean closed;

  DatabaseHousekeepingScheduler(@NotNull DatabaseHousekeepingCoordinator coordinator,
                                @NotNull ScheduledExecutorService schedulingExecutor,
                                @NotNull Executor housekeepingExecutor) {
    this.coordinator = coordinator;
    this.schedulingExecutor = schedulingExecutor;
    this.housekeepingExecutor = housekeepingExecutor;
  }

  void register(@NotNull HousekeepingRegistration registration,
                @NotNull Duration delay) {
    synchronized (lock) {
      if (closed || !registrations.add(registration)) {
        return;
      }
      schedule(registration, delay);
    }
  }

  void unregister(@NotNull HousekeepingRegistration registration) {
    synchronized (lock) {
      registrations.remove(registration);
      var scheduledRun = scheduledRuns.remove(registration);
      if (scheduledRun != null) {
        scheduledRun.cancel(false);
      }
      readyRegistrations.removeIf(readyRegistration -> readyRegistration == registration);
    }
  }

  private void schedule(@NotNull HousekeepingRegistration registration, @NotNull Duration delay) {
    if (closed || !registrations.contains(registration)) {
      return;
    }
    long delayMs = delay.toMillis();
    var scheduledRun = schedulingExecutor.schedule(
      () -> makeReady(registration),
      delayMs, MILLISECONDS
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
      if (closed || !registrations.contains(registration)) {
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
          schedule(readyRegistrations.removeFirst(), FAILURE_RETRY_DELAY);
        }
      }
    }
  }

  private void runReadyRegistrations() {
    while (true) {
      HousekeepingRegistration registration;
      synchronized (lock) {
        if (closed || readyRegistrations.isEmpty()) {
          workerRunning = false;
          return;
        }
        registration = readyRegistrations.removeFirst();
      }

      var nextRunDelay = runHousekeeping(registration);
      if (nextRunDelay != null) {
        synchronized (lock) {
          schedule(registration, nextRunDelay);
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
      return FAILURE_RETRY_DELAY;
    }
  }

  @Override
  public void close() {
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
      scheduledRuns.values().forEach(scheduledRun -> scheduledRun.cancel(false));
      scheduledRuns.clear();
      readyRegistrations.clear();
      registrations.clear();
    }
    coordinator.stopScheduling(this);
  }
}
