// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.spi.BlocksDatabase;
import com.intellij.platform.util.io.storages.database.spi.housekeeping.OnStartupHousekeeper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/// Deletes retired chunk files.
/// (Already opened chunks -- are closed before deletion)
/// Runs before the database becomes available to clients.
@ApiStatus.Internal
public final class DropRetiredChunksHousekeeper implements OnStartupHousekeeper {
  @Override
  public void runHousekeeping(@NotNull BlocksDatabase database) throws IOException {
    BlocksDatabaseImpl dbImpl = (BlocksDatabaseImpl)database;
    dbImpl.dropRetiredChunks();
  }
}
