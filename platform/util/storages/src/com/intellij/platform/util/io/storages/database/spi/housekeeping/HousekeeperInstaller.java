// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.spi.housekeeping;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/// Optional capability that installs one housekeeper and controls its registration lifecycle
@ApiStatus.Internal
public interface HousekeeperInstaller {
  /// @return an idempotent handle that cancels the active run and removes the registration
  @NotNull AutoCloseable installHousekeeper(@NotNull Housekeeper housekeeper);
}
