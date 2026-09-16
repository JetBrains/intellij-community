// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.platform.util.io.storages.KeyValueStoreTestContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/// Provides the fixture operations that the durable map test scenarios need
public interface DurableMapTestContext<K, V, M extends DurableMap<K, V>> extends KeyValueStoreTestContext<K, V, M> {

  ///should return true if the map is mostly append-only
  boolean isAppendOnlyStorage();

  /// @return _new_ storage, without replacing [storageUnderTest];
  ///         Caller is responsible for closing the returned storage with [closeStorage]
  @NotNull M openStorage(@NotNull Path path) throws IOException;

  /// Closes a map that [openStorage] returned
  default void closeStorage(@NotNull M storage) throws Exception {
    storage.close();
  }

  /// Close [storageUnderTest], drop underlying data for lookup part of it, and reopen the storage back
  void reopenDroppingDurableLookupPart() throws IOException;

  /// Close [storageUnderTest] so that it doesn't detect 'proper close', and reopen it back
  void reopenAfterImproperClose() throws Exception;
}
