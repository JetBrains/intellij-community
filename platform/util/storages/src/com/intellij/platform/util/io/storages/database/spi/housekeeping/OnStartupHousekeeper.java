// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.spi.housekeeping;

import com.intellij.platform.util.io.storages.database.spi.BlocksDatabase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/// Runs once on database startup: after recovery, before application storages get access to the database;
/// thus it has exclusive access to database -- a low-level tool for database-internal matters.
@ApiStatus.Internal
@FunctionalInterface
public interface OnStartupHousekeeper {
  /// Runs synchronously on the opening thread, outside database locks.
  /// Implementation should NOT retain database in its fields, or publish reference to it anywhere.
  /// (In general: prefer stateless implementation)
  /// Keep runs short: startup housekeeping duration adds up to database startup time.
  void runHousekeeping(@NotNull BlocksDatabase database) throws IOException;
}
