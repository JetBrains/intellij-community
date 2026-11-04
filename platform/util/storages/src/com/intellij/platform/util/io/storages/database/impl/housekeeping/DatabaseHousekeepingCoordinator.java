// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.housekeeping;

import com.intellij.platform.util.io.storages.database.spi.housekeeping.Housekeeper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/// Manages [Housekeeper]'s registration and lifecycle.
@ApiStatus.Internal
public final class DatabaseHousekeepingCoordinator implements AutoCloseable {

  private final transient @NotNull Object lock = new Object();
  private final transient @NotNull Object housekeepingExecutionLock = new Object();

  //@GuardedBy(lock)
  private final @NotNull Set<HousekeepingRegistration> registrations = new HashSet<>();
  //@GuardedBy(lock)
  private boolean closed;

  /// `null` means there is no automatic housekeeping, client must use [#runHousekeeping] methods explicitly, or live without
  /// housekeeping at all
  private final @Nullable DatabaseHousekeepingScheduler scheduler;

  /// Creates a coordinator without scheduler -- i.e. for explicit [#runHousekeeping] calls
  /// (Most likely a test option)
  public DatabaseHousekeepingCoordinator() {
    scheduler = null;
  }

  /// Creates a coordinator that uses a scheduler from the specified factory
  public DatabaseHousekeepingCoordinator(
    @NotNull Function<? super DatabaseHousekeepingCoordinator, ? extends @NotNull DatabaseHousekeepingScheduler> schedulerFactory
  ) {
    scheduler = requireNonNull(
      schedulerFactory.apply(this),
      "The housekeeping scheduler factory returned null"
    );
  }

  public @NotNull HousekeepingRegistration register(@NotNull Housekeeper housekeeper) {
    synchronized (lock) {
      ensureNotClosed();
      var registration = new HousekeepingRegistration(this, housekeeper);
      registrations.add(registration);
      if (scheduler != null) {
        scheduler.register(registration);
      }
      return registration;
    }
  }

  /// Runs each registered housekeeper on the calling thread; for testing
  @TestOnly
  public void runHousekeeping() throws IOException {
    List<HousekeepingRegistration> registrationsToRun;
    synchronized (lock) {
      ensureNotClosed();
      registrationsToRun = List.copyOf(registrations);
    }
    for (var registration : registrationsToRun) {
      var running = registration.reserveRunIfOpen();
      if (running != null) {
        synchronized (housekeepingExecutionLock) {
          registration.runHousekeeping(running);
        }
      }
    }
  }

  /// Runs one registered housekeeper on the calling thread
  @TestOnly
  public @NotNull Duration runHousekeeping(@NotNull HousekeepingRegistration registration) throws IOException {
    if (!registration.isOwnedBy(this)) {
      throw new IllegalArgumentException("The registration belongs to another housekeeping coordinator");
    }
    var running = registration.reserveRun();
    synchronized (housekeepingExecutionLock) {
      return registration.runHousekeeping(running);
    }
  }

  @Nullable Duration runHousekeepingIfOpen(@NotNull HousekeepingRegistration registration) throws IOException {
    return runHousekeepingIfOpen(registration, () -> false);
  }

  @Nullable Duration runHousekeepingIfOpen(@NotNull HousekeepingRegistration registration,
                                           @NotNull BooleanSupplier cancellationRequested) throws IOException {
    var running = registration.reserveRunIfOpen();
    if (running == null) {
      return null;
    }
    synchronized (housekeepingExecutionLock) {
      return registration.runHousekeeping(running, cancellationRequested);
    }
  }

  void unregister(@NotNull HousekeepingRegistration registration) {
    DatabaseHousekeepingScheduler currentScheduler;
    synchronized (lock) {
      registrations.remove(registration);
      currentScheduler = scheduler;
    }
    if (currentScheduler != null) {
      currentScheduler.unregister(registration);
    }
  }

  @Override
  public void close() throws IOException {
    List<HousekeepingRegistration> registrationsToClose;
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
      registrationsToClose = List.copyOf(registrations);
    }
    registrationsToClose.forEach(HousekeepingRegistration::close);
  }

  private void ensureNotClosed() {
    if (closed) {
      throw new IllegalStateException("The housekeeping coordinator is already closed");
    }
  }
}
