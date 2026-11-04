// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.spi.housekeeping;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/// Performs regular housekeeping in parallel with database operations.
/// The scheduler decides whether and when to invoke each housekeeper.
@ApiStatus.Internal
@FunctionalInterface
public interface Housekeeper {
  /// Performs one round of housekeeping;
  /// Rounds should be short, and regularly check for cancellation.
  ///
  /// @return a positive _logical_ delay hint for a later run;
  ///         the scheduler decides whether and when to run again, and how to map the delay to _physical_ time.
  @NotNull Duration runHousekeeping(@NotNull BooleanSupplier cancellationRequested) throws IOException;
}
