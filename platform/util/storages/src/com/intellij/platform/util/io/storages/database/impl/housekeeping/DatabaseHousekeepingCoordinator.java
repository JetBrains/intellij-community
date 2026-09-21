// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.housekeeping;

import com.intellij.platform.util.io.storages.database.spi.housekeeping.Housekeeper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// Manages [Housekeeper]'s registration and lifecycle.
///
///Currently, [#runHousekeeping] runs [Housekeeper]s on the calling thread.
@ApiStatus.Internal
public final class DatabaseHousekeepingCoordinator implements AutoCloseable {
  private static final Duration INITIAL_HOUSEKEEPER_DELAY_AFTER_REGISTRATION = Duration.ofMillis(10);
  /// Run first housekeepers rounds on startup randomly spread
  private static final int STARTUP_HOUSEKEEPERS_SPREADING_MS = 5_000;

  private final transient @NotNull Object lock = new Object();
  private final transient @NotNull Object executionLock = new Object();

  private final @NotNull Set<HousekeepingRegistration> registrations = new HashSet<>();
  private boolean closed;

  public @NotNull HousekeepingRegistration register(@NotNull Housekeeper housekeeper) {
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("The housekeeping coordinator is already closed");
      }
      var registration = new HousekeepingRegistration(this, housekeeper);
      registrations.add(registration);
      return registration;
    }
  }

  /// Runs each registered housekeeper on the calling thread
  public void runHousekeeping() throws IOException {
    List<HousekeepingRegistration> registrationsToRun;
    synchronized (lock) {
      if (closed) {
        throw new IllegalStateException("The housekeeping coordinator is already closed");
      }
      registrationsToRun = List.copyOf(registrations);
    }
    for (var registration : registrationsToRun) {
      var running = registration.reserveRunIfOpen();
      if (running != null) {
        synchronized (executionLock) {
          registration.runHousekeeping(running);
        }
      }
    }
  }

  /// Runs one registered housekeeper on the calling thread
  public @NotNull Duration runHousekeeping(@NotNull HousekeepingRegistration registration) throws IOException {
    if (!registration.isOwnedBy(this)) {
      throw new IllegalArgumentException("The registration belongs to another housekeeping coordinator");
    }
    var running = registration.reserveRun();
    synchronized (executionLock) {
      return registration.runHousekeeping(running);
    }
  }

  @Nullable Duration runHousekeepingIfOpen(@NotNull HousekeepingRegistration registration) throws IOException {
    var running = registration.reserveRunIfOpen();
    if (running == null) {
      return null;
    }
    synchronized (executionLock) {
      return registration.runHousekeeping(running);
    }
  }

  void unregister(@NotNull HousekeepingRegistration registration) {
    synchronized (lock) {
      registrations.remove(registration);
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
}
