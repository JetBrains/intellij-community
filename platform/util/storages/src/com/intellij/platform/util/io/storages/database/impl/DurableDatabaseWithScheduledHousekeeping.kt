// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl

import com.intellij.platform.util.io.storages.database.DurableDatabase
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService

/**
 * Wraps a [DurableDatabaseImpl], adds a periodic housekeeping.
 * Everything else kept as-is
 */
@ApiStatus.Internal
class DurableDatabaseWithScheduledHousekeeping(
  private val database: DurableDatabaseImpl,
  scheduler: ScheduledExecutorService,
  housekeepingExecutor: Executor = scheduler,
) : DurableDatabase by database {
  private val housekeepingScheduler = database.startHousekeeping(scheduler, housekeepingExecutor)

  override fun close() {
    housekeepingScheduler.close()
    database.close()
  }
}
