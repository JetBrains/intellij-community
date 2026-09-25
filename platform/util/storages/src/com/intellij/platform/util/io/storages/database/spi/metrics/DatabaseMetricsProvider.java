// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.spi.metrics;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface DatabaseMetricsProvider {
  /// Returns the current database metrics;
  /// If the database is closed, returned metrics are unspecified.
  ///
  /// @param snapshotMetrics true requires a consistent snapshot; false permits weakly consistent values, i.e., different metrics
  ///                        may be taken in slightly different moments.
  @NotNull DatabaseMetrics metrics(boolean snapshotMetrics);
}
