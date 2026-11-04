// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.housekeeping;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Tracks housekeeping registrations and decides when to run them.
///
/// The coordinator calls [#register] under its lifecycle lock and [#unregister] after releasing the lock.
@ApiStatus.Internal
public interface DatabaseHousekeepingScheduler {
  /// Registers a housekeeper for running.
  /// From this point on scheduler takes the responsibility to run the housekeeping as needed
  void register(@NotNull HousekeepingRegistration registration);

  /// Removes a housekeeping from scheduling: no new housekeeping rounds after this method
  void unregister(@NotNull HousekeepingRegistration registration);
}
