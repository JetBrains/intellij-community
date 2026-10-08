// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/// A capability of a store: retire blocks that are not by the store anymore;
///  the definition of 'not used' is up to the store implementation.
@ApiStatus.Internal
public interface UnusedBlocksRetirement {
  /// Should check `cancellationRequested` regularly.
  void retireUnusedBlocks(@NotNull BooleanSupplier cancellationRequested) throws IOException;
}
