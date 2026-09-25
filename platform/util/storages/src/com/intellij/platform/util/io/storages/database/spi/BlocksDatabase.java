// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.spi;

import com.intellij.platform.util.io.storages.database.spi.metrics.DatabaseMetricsProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.List;

/// SPI that provides named block stores to application storages.
/// A particular application storage should obtain its [BlocksStore] through [findStore] or [openStore] methods.
@ApiStatus.Internal
public interface BlocksDatabase extends Flushable, Closeable, DatabaseMetricsProvider {
  /// Opens the named store or creates it with the specified data version
  @NotNull BlocksStore openStore(@NotNull String name, int dataVersion) throws IOException;

  /// @return the current named store, or `null` when the store does not exist
  @Nullable BlocksStore findStore(@NotNull String name);

  /// @return a snapshot of the current store names
  @NotNull List<String> storeNames();

  /// @return true when completed changes can require a flush
  boolean isDirty();

  /// Flushes buffered changes on disk: the database configuration controls fsync during this operation.
  @Override
  void flush() throws IOException;

  /// @return true after the database stops accepting operations
  boolean isClosed();

  /// Releases the database storage. The database configuration controls fsync during this operation.
  @Override
  void close() throws IOException;
}
