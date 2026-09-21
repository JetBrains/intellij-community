// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.spi.housekeeping;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/// Performs regular housekeeping:
/// - the database invokes each housekeeper periodically, according to intervals requested by [[#runHousekeeping(BooleanSupplier)];
/// - Housekeeper runs in parallel with DB operations, and shares database access with clients;
@ApiStatus.Internal
@FunctionalInterface
public interface Housekeeper {
  /// Performs one round of housekeeping;
  /// Rounds should be short, and regularly check for cancellation.
  ///
  /// @return a positive delay from the end of this run until the next run;
  ///         this delay is a hint for housekeeper scheduler, not a guarantee.
  @NotNull Duration runHousekeeping(@NotNull BooleanSupplier cancellationRequested) throws IOException;
}
