// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.List;

/// A database of named storages. The current API provides durable maps
@ApiStatus.Internal
public interface DurableDatabase extends Flushable, Closeable {
  /** Opens the named map or creates it if it does not exist yet */
  <K, V> @NotNull DurableMap<K, V> openMap(@NotNull String name,
                                           int dataVersion,
                                           @NotNull KeyDescriptorEx<K> keyDescriptor,
                                           @NotNull DataExternalizerEx<V> valueExternalizer) throws IOException;

  /// Opens a named map that accepts patches encoded by the supplied codec
  <K, V, P> @NotNull PatchableDurableMap<K, V, P> openMap(@NotNull String name,
                                                          int dataVersion,
                                                          @NotNull KeyDescriptorEx<K> keyDescriptor,
                                                          @NotNull PatchableDurableMap.PatchableValueExternalizer<V, P> valueExternalizer)
    throws IOException;

  /// @return a snapshot of the current map names
  @NotNull List<String> mapNames();

  /// Removes the named map. The map must not be open
  void dropMap(@NotNull String name) throws IOException;

  /** @return true when completed changes still require a flush */
  boolean isDirty();

  /** Flushes all completed changes. The factory configuration controls fsync during this operation. */
  @Override
  void flush() throws IOException;

  /** @return true after the database stops accepting operations */
  boolean isClosed();

  /** Releases the database storage. The factory configuration controls fsync during this operation. */
  @Override
  void close() throws IOException;
}
